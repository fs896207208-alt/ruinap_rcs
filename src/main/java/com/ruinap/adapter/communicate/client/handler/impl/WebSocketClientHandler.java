package com.ruinap.adapter.communicate.client.handler.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.event.AbstractClientEvent;
import com.ruinap.adapter.communicate.client.handler.ClientHandler;
import com.ruinap.adapter.communicate.client.registry.EventRegistry;
import com.ruinap.core.business.AgvSuggestionManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.manager.ChargePileManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.equipment.pojo.RcsChargePile;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.task.PlanStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;

/**
 * WebSocket客户端处理器
 *
 * @author qianye
 * @create 2025-05-12 09:45
 */
@Component
public class WebSocketClientHandler implements ClientHandler {
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private ChargePileManager chargePileManager;
    @Autowired
    private AgvSuggestionManager agvSuggestionManager;
    @Autowired
    private TaskPathManager taskPathManager;

    /**
     * 用于处理接收到的特定类型消息（如TextWebSocketFrame）。在此处理WebSocket帧数据
     *
     * @param ctx   上下文
     * @param frame 数据帧
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object frame, ClientAttribute attribute) {
        //调用事件处理
        AbstractClientEvent event = EventRegistry.getEvent(attribute.getEquipmentType().getEquipmentType());
        event.receiveMessage(attribute, frame);
    }

    /**
     * 当客户端连接尝试失败时调用（连接被拒绝，达到最大重试次数）
     *
     * @param cause     失败原因 (可选，如果原因明确)
     * @param attribute 客户端属性
     */
    @Override
    public void connectionFailed(Throwable cause, ClientAttribute attribute) {
        String clientId = attribute.getClientId();
        LinkEquipmentTypeEnum equipmentType = attribute.getEquipmentType();
        switch (equipmentType) {
            case AGV:
                RcsAgv rcsAgv = agvManager.getRcsAgvByCode(clientId);

                RcsLog.consoleLog.error("{} AGV连接失败次数过多，将进入取消任务流程", StrUtil.format("{}_{}", equipmentType.getEquipmentType(), rcsAgv.getAgvId()));
                //将数据存到AGV错误字段中
                agvSuggestionManager.addSuggestion(equipmentType.getEquipmentType() + "_" + rcsAgv.getAgvId(), "AGV连接失败次数过多，将进入取消任务流程");
                //获取任务路径
                TaskPath taskPath = taskPathManager.getFirst(rcsAgv.getAgvId());
                if (taskPath != null) {
                    //设置任务状态为取消
                    taskPath.setState(PlanStateEnum.CANCEL.code);
                }
                break;
            case CHARGE_PILE:
                RcsChargePile rcsChargePile = chargePileManager.getRcsChargePileByCode(clientId);
                rcsChargePile.setState(0);
                RcsLog.consoleLog.error("{} 充电桩连接失败次数过多，设置为离线状态", StrUtil.format("{}_{}", equipmentType.getEquipmentType(), clientId));
                break;
            case TRANSFER:
                RcsLog.consoleLog.warn("中转系统连接失败，正在继续尝试连接");
                break;
            case null:
                RcsLog.consoleLog.error("未知设备种类:" + equipmentType.getEquipmentType().toLowerCase());
        }
    }

    /**
     * 处理用户自定义事件
     * <p>
     * HANDSHAKE_COMPLETE 事件，表示握手已经完成
     *
     * @param ctx 上下文
     * @param evt 事件
     */
    @Override
    public JSONObject userEventTriggered(ChannelHandlerContext ctx, Object evt, ClientAttribute attribute) {
        JSONObject jsonObject = new JSONObject();
        //握手完成事件
        if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            // 从 Channel 的 Attribute 中获取客户端 ID
            Channel channel = ctx.channel();
            String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
            //如果客户端ID不为空，则记录客户端上下文
            if (clientId != null && !clientId.isEmpty()) {
                // 连接建立时触发
                RcsLog.consoleLog.info("{} userEventTriggered 已连接到 {} 服务器: {}", clientId, attribute.getProtocol().getProtocol(), ctx.channel().remoteAddress());
                RcsLog.communicateLog.info("{} userEventTriggered 已连接到 {} 服务器: {}", clientId, attribute.getProtocol().getProtocol(), ctx.channel().remoteAddress());
                jsonObject.set("handshake_complete", true);
            }
        }
        return jsonObject;
    }

    /**
     * 捕获异常时调用，处理错误并决定是否关闭连接
     *
     * @param ctx   上下文
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause, ClientAttribute attribute) {
        //无业务，暂不处理
    }

    /**
     * 当连接成功时调用。
     * 用途：记录连接成功信息，并执行其他初始化操作。
     *
     * @param ctx 上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx, ClientAttribute attribute) {

    }

    /**
     * 当连接关闭或通道失效时触发。
     * 用于资源清理、重连机制或记录连接关闭状态
     *
     * @param ctx 上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        //无业务，暂不处理
    }

    /**
     * 触发时机：当 Channel 从 EventLoop 中注销时调用。
     * 用途：清理与 Channel 相关的资源。
     * 注意：此时 Channel 可能已关闭或未绑定。
     * <p>
     * 当关闭连接时，第二个调用channelUnregistered
     *
     * @param ctx 上下文
     */
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {

        // 从 Channel 的 Attribute 中获取客户端 ID
        Channel channel = ctx.channel();
        String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(clientId);
        if (rcsAgv != null) {
            rcsAgv.setAgvState(-1);
            rcsAgv.setLight(1);
            RcsLog.consoleLog.error("{} AGV与调度断开连接，设置为离线状态", clientId);
        }
    }
}
