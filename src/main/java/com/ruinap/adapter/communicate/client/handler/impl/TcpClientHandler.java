package com.ruinap.adapter.communicate.client.handler.impl;

import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.event.AbstractClientEvent;
import com.ruinap.adapter.communicate.client.handler.ClientHandler;
import com.ruinap.adapter.communicate.client.registry.EventRegistry;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.equipment.pojo.RcsChargePile;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * TCP客户端处理器
 *
 * @author qianye
 * @create 2025-05-12 09:45
 */
public class TcpClientHandler implements ClientHandler {

    /**
     * 用于处理接收到的特定类型消息。在此处理TCP帧数据
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
                rcsAgv.setAgvState(-1);
                rcsAgv.setLight(1);
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(equipmentType.getEquipmentType() + "_" + clientId, "AGV连接失败次数过多，设置为离线状态"));
                break;
            case CHARGE_PILE:
                RcsChargePile rcsChargePile = DbCache.RCS_CHARGE_MAP.get(clientId);
                rcsChargePile.setState(0);
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(equipmentType.getEquipmentType() + "_" + clientId, "充电桩连接失败次数过多，设置为离线状态"));
                break;
            case TRANSFER:
                RcsLog.consoleLog.warn("中转系统连接失败，正在继续尝试连接");
                break;
            default:
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
        return new JSONObject();
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
     *
     * @param ctx       上下文
     * @param attribute 客户端属性
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx, ClientAttribute attribute) {
        // 从 Channel 的 Attribute 中获取客户端 ID
        Channel channel = ctx.channel();
        String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        //如果客户端ID不为空，则记录客户端上下文
        if (clientId != null && !clientId.isEmpty()) {
            // 连接建立时触发
            RcsLog.consoleLog.info(RcsLog.formatTemplateRandom(clientId, "已连接到 " + attribute.getProtocol().getProtocol() + " 服务器: " + ctx.channel().remoteAddress()));
            RcsLog.communicateLog.info(RcsLog.formatTemplateRandom(clientId, "已连接到 " + attribute.getProtocol().getProtocol() + " 服务器: " + ctx.channel().remoteAddress()));
        }
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
        //无业务，暂不处理
    }
}
