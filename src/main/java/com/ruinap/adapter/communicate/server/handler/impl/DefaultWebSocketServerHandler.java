package com.ruinap.adapter.communicate.server.handler.impl;

import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;

/**
 * WebSocket处理器
 *
 * @author qianye
 * @create 2025-04-29 15:55
 */
public class DefaultWebSocketServerHandler implements IServerHandler {
    @Override
    public JSONObject userEventTriggered(ChannelHandlerContext ctx, Object evt, ServerAttribute attribute) {
        JSONObject jsonObject = new JSONObject();
        // 监听握手完成事件
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            String serverId = ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).get();
            String id = ctx.channel().id().toString();

            // 1. 直接从 Channel 属性获取当前所属的 NettyServer 实例
            NettyServer server = ctx.channel().attr(NettyServer.SERVER_REF_KEY).get();
            if (server == null) {
                RcsLog.consoleLog.error("无法获取 NettyServer 实例: {}", attribute.getProtocol());
                ctx.close();
                return null;
            }

            // 2. 使用实例方法
            if (server.getContexts().containsKey(serverId)) {
                if (!server.getChannelIds().containsKey(id)) {
                    ctx.writeAndFlush(new TextWebSocketFrame("服务端 [" + serverId + "] 已连接到系统，因此你的连接被拒绝"))
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    RcsLog.consoleLog.error("{} 服务端已连接到系统，因此你的连接被拒绝", serverId);
                                    ctx.close();
                                } else {
                                    ctx.fireExceptionCaught(future.cause());
                                }
                            });
                }
            } else {
                jsonObject.set("handshake_complete", true);
            }
        }

        return jsonObject;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object frame, ServerAttribute attribute) {
        // 从 Channel 的 AttributeKey 中获取服务端ID
        String serverId = ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).get();
        // 检查是否是文本帧
        if (frame instanceof TextWebSocketFrame) {
            // 处理文本帧，打印收到的消息并发送回客户端
            String message = ((TextWebSocketFrame) frame).text();
            RcsLog.consoleLog.error("接收到WebSocket 服务端 [" + serverId + "]消息 " + message);

            // 选择实际的业务事件 进行处理
//            ConsoleWebSocketEvent.handleEvent(serverId, request);

        } else {
            // 如果不是文本帧，抛出异常或不处理
            String message = "不支持的消息类型: " + frame.getClass().getName();
            // 手动释放消息对象
            ReferenceCountUtil.release(frame);

            RcsLog.consoleLog.error("接收到WebSocket 服务端 [" + serverId + "] " + message);
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx, ServerAttribute attribute) {
        //无业务，暂不处理
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause, ServerAttribute attribute) {
        //无业务，暂不处理
    }
}
