package com.ruinap.adapter.communicate.client.handler.impl;

import cn.hutool.json.JSONObject;
import com.slamopto.algorithm.domain.PlanStateEnum;
import com.slamopto.common.enums.LinkEquipmentTypeEnum;
import com.slamopto.communicate.base.ClientAttribute;
import com.slamopto.communicate.base.enums.AttributeKeyEnum;
import com.slamopto.communicate.base.event.AbstractClientEvent;
import com.slamopto.communicate.client.handler.ClientHandler;
import com.slamopto.communicate.client.registry.EventRegistry;
import com.slamopto.db.DbCache;
import com.slamopto.db.business.AgvSuggestionManage;
import com.slamopto.equipment.domain.RcsAgv;
import com.slamopto.equipment.domain.RcsChargePile;
import com.slamopto.log.RcsLog;
import com.slamopto.task.TaskPathCache;
import com.slamopto.task.domain.TaskPath;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;

/**
 * WebSocket客户端处理器
 *
 * @author qianye
 * @create 2025-05-12 09:45
 */
public class WebSocketClientHandler implements ClientHandler {

    /**
     * 用于处理接收到的特定类型消息（如TextWebSocketFrame）。在此处理WebSocket帧数据
     *
     * @param ctx   上下文
     * @param frame 数据帧
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object frame, ClientAttribute attribute) {
        //调用事件处理
        AbstractClientEvent event = EventRegistry.getEvent(attribute.getBrand().getBrand(), attribute.getEquipmentType().getEquipmentType());
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
                RcsAgv rcsAgv = DbCache.RCS_AGV_MAP.get(clientId);

                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(equipmentType.getEquipmentType() + "_" + rcsAgv.getAgvId(), "AGV连接失败次数过多，将进入取消任务流程"));
                //将数据存到AGV错误字段中
                AgvSuggestionManage.addSuggestion(equipmentType.getEquipmentType() + "_" + rcsAgv.getAgvId(), "AGV连接失败次数过多，将进入取消任务流程");
                //获取任务路径
                TaskPath taskPath = TaskPathCache.getFirst(rcsAgv.getAgvId());
                if (taskPath != null) {
                    //设置任务状态为取消
                    taskPath.setState(PlanStateEnum.CANCEL.code);
                }
                break;
            case CHARGE_PILE:
                RcsChargePile rcsChargePile = DbCache.RCS_CHARGE_MAP.get(clientId);
                rcsChargePile.setState(0);
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(equipmentType.getEquipmentType() + "_" + clientId, "充电桩连接失败次数过多，设置为离线状态"));
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
                RcsLog.consoleLog.info(RcsLog.formatTemplateRandom(clientId, "userEventTriggered 已连接到 " + attribute.getProtocol().getProtocol() + " 服务器: " + ctx.channel().remoteAddress()));
                RcsLog.communicateLog.info(RcsLog.formatTemplateRandom(clientId, "userEventTriggered 已连接到 " + attribute.getProtocol().getProtocol() + " 服务器: " + ctx.channel().remoteAddress()));
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
        RcsAgv rcsAgv = DbCache.RCS_AGV_MAP.get(clientId);
        if (rcsAgv != null) {
            rcsAgv.setAgvState(-1);
            rcsAgv.setLight(1);
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(clientId, "AGV与调度断开连接，设置为离线状态"));
        }
    }
}
