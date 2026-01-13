package com.ruinap.adapter.communicate.server.handler.impl;

import cn.hutool.json.JSONObject;
import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.ServerAttribute;
import com.slamopto.communicate.base.enums.AttributeKeyEnum;
import com.slamopto.communicate.event.VisualWebSocketServerEvent;
import com.slamopto.communicate.server.NettyServer;
import com.slamopto.communicate.server.handler.IServerHandler;
import com.slamopto.log.RcsLog;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;

/**
 * 可视化服务器类，用于处理 WebSocket 可视化相关的消息
 *
 * @author qianye
 * @create 2025-02-07 21:40
 */
public class VisualWebSocketHandler implements IServerHandler {

    /**
     * 协议枚举
     */
    @Getter
    private static ProtocolEnum protocol;

    /**
     * 触发时机：当从 Channel 读取到数据时调用（核心方法）
     * 用途：处理特定类型的消息（泛型 I 指定消息类型）
     * 特点：自动释放消息对象（通过 ReferenceCountUtil.release(msg)）
     *
     * @param ctx   上下文
     * @param frame 数据帧
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object frame, ServerAttribute attribute) {
        // 从 Channel 的 AttributeKey 中获取服务端ID
        String serverId = ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).get();
        // 检查是否是文本帧
        if (frame instanceof TextWebSocketFrame) {
            // 获取服务端发送的文本消息
            String request = ((TextWebSocketFrame) frame).text();
            try {
                JSONObject jsonObject = new JSONObject(request);
                if (!jsonObject.isEmpty()) {
                    // 处理事件
                    VisualWebSocketServerEvent.receiveMessage(serverId, jsonObject);
                } else {
                    RcsLog.consoleLog.error("服务端 [" + serverId + "] 接收到空的参数");
                }
            } catch (Exception e) {
                e.printStackTrace();
                RcsLog.consoleLog.error("出现异常，服务端 [" + serverId + "] 传入的参数不是json格式");
            }

        } else {
            // 如果不是文本帧，抛出异常或不处理
            String message = "不支持的消息类型: " + frame.getClass().getName();
            // 手动释放消息对象
            ReferenceCountUtil.release(frame);

            RcsLog.consoleLog.error("接收到WebSocket 服务端 [" + serverId + "] " + message);
            throw new UnsupportedOperationException(message);
        }
    }

    /**
     * 触发时机：当用户自定义事件触发时调用。
     * 用途：处理Netty内置事件、用户自定义事件等。
     *
     * @param ctx 上下文
     * @param evt 事件对象
     */
    @Override
    public JSONObject userEventTriggered(ChannelHandlerContext ctx, Object evt, ServerAttribute attribute) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", evt);
        // 监听握手完成事件
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // 从 Channel 的 AttributeKey 中获取服务端ID
            String serverId = ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).get();

            // 获取服务端通道ID
            String id = ctx.channel().id().toString();
            if (NettyServer.getCONTEXTS().containsKey(serverId)) {
                if (!NettyServer.getCHANNEL_IDS().containsKey(id)) {
                    // 发送提示消息并在发送完成后关闭连接
                    // 握手完成后发送消息
                    ctx.writeAndFlush(new TextWebSocketFrame("服务端 [" + serverId + "] 已连接到系统，因此你的连接被拒绝"))
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(serverId, "服务端已连接到系统，因此你的连接被拒绝"));
                                    ctx.close();
                                } else {
                                    // 将异常传递给 exceptionCaught
                                    ctx.fireExceptionCaught(future.cause());
                                }
                            });
                }
            } else {
                if (protocol == null) {
                    //设置协议
                    protocol = attribute.getProtocol();
                }
                jsonObject.set("handshake_complete", true);
            }
        }

        return jsonObject;
    }

    /**
     * 触发时机：当 Handler 从 ChannelPipeline 中移除时调用。
     * 用途：清理 Handler 级别的资源。
     * <p>
     * 当关闭连接时，第三个调用handlerRemoved
     *
     * @param ctx 上下文
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx, ServerAttribute attribute) {
        //无业务，暂不处理
    }

    /**
     * 触发时机：当处理过程中发生异常时调用
     * 用途：处理异常（如 IO 错误、解码失败等），需记录日志或关闭连接
     * 注意：若不重写此方法，默认行为是关闭 Channel
     *
     * @param ctx   上下文
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause, ServerAttribute attribute) {
        //无业务，暂不处理
    }
}
