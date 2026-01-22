package com.ruinap.adapter.communicate.server.handler.impl;

import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.ConfigDB;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;

import java.sql.SQLException;

/**
 * 业务服务器类，用于处理 WebSocket 业务相关的消息
 *
 * @author qianye
 * @create 2025-02-07 21:40
 */
@ChannelHandler.Sharable
@Component
public class BusinessWebSocketHandler implements IServerHandler {
    @Autowired
    private ConfigDB configDB;

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
//                    BusinessWebSocketEvent.receiveMessage(serverId, jsonObject);
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
        // 监听握手完成事件
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // 从 Channel 的 AttributeKey 中获取服务端ID
            String serverId = ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).get();

            // 获取服务端通道ID
            String id = ctx.channel().id().toString();
            JSONObject entries = new JSONObject();
            if (NettyServer.getCONTEXTS().containsKey(serverId)) {
                if (!NettyServer.getCHANNEL_IDS().containsKey(id)) {
                    entries.set("event", "comm_fail");
                    entries.set("msg", "服务端 [" + serverId + "] 已连接到系统，因此你的连接被拒绝");
                    // 发送提示消息并在发送完成后关闭连接
                    // 握手完成后发送消息
                    ctx.writeAndFlush(new TextWebSocketFrame(entries.toString()))
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    RcsLog.consoleLog.error("{} 服务端已连接到系统，因此你的连接被拒绝", serverId);
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
                entries.set("event", "comm_success");
                entries.set("msg", "Business服务端【" + serverId + "】连接成功，欢迎使用调度系统业务端");
                Integer taskGroupKey = null;
                try {
                    Entity configValue = configDB.getConfigValue("sys.task.key");
                    if (configValue != null && !configValue.isEmpty()) {
                        taskGroupKey = configValue.getInt("config_value");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                JSONObject jsonObject1 = new JSONObject();
                jsonObject1.set("current_task_group", taskGroupKey);
                entries.set("data", jsonObject1);

                ctx.writeAndFlush(new TextWebSocketFrame(entries.toString()));
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
