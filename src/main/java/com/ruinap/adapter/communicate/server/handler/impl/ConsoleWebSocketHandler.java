package com.ruinap.adapter.communicate.server.handler.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.infra.command.system.ConsoleCommand;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;

/**
 * ConsoleWebSocketHandler 类，用于处理 WebSocket 控制台相关的消息
 *
 * @author qianye
 * @create 2025-01-28 21:40
 */
@Component
public class ConsoleWebSocketHandler implements IServerHandler {
    @Autowired
    private ConsoleCommand consoleCommand;

    /**
     * 协议枚举
     */
    @Getter
    private static ProtocolEnum protocol;

    @Override
    public ProtocolEnum getProtocol() {
        return ProtocolEnum.WEBSOCKET_SERVER;
    }

    @Override
    public String getPath() {
        return "/ws/console";
    }

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
            // 处理文本帧，打印收到的消息并发送回客户端
            String request = ((TextWebSocketFrame) frame).text();
            // 处理事件
//            ConsoleWebSocketServerEvent.receiveMessage(serverId, request);

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
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {

            // 1. 获取 ID (由 WebSocketOption 解析并设置)
            String serverId = ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).get();

            if (StrUtil.isBlank(serverId)) {
                RcsLog.consoleLog.error("握手成功但无法获取身份ID, 拒绝连接");
                ctx.writeAndFlush(new TextWebSocketFrame("Error: Identity lost"));
                ctx.close();
                return null;
            }

            // 2. 获取 NettyServer 实例
            NettyServer server = ctx.channel().attr(NettyServer.SERVER_REF_KEY).get();
            if (server == null) {
                RcsLog.consoleLog.error("严重错误: 无法获取 NettyServer 实例");
                ctx.close();
                return null;
            }

            // 3. 重复登录检查
            if (server.getContexts().containsKey(serverId)) {
                RcsLog.consoleLog.warn("{} 尝试重复登录，连接被拒绝", serverId);
                ctx.writeAndFlush(new TextWebSocketFrame("连接拒绝：ID [" + serverId + "] 已在线"));
                ctx.close();
                return null;
            }

            // 4. 登录成功反馈
            if (protocol == null) {
                protocol = attribute.getProtocol();
            }

            ctx.writeAndFlush(new TextWebSocketFrame("Console服务端【" + serverId + "】连接成功，欢迎使用调度系统控制台"));
            ctx.writeAndFlush(new TextWebSocketFrame(consoleCommand.getMenu()));

            jsonObject.set("handshake_complete", true);
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
