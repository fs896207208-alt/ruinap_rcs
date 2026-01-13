package com.ruinap.adapter.communicate.client.handler;

import com.slamopto.communicate.base.ClientAttribute;
import com.slamopto.communicate.base.handler.IBaseClientHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * 客户端处理器接口
 *
 * @author qianye
 * @create 2025-05-09 15:34
 */
public interface ClientHandler extends IBaseClientHandler {

    /**
     * 当通道激活时触发。
     * 用于记录连接关闭状态
     *
     * @param ctx 上下文
     */
    void channelActive(ChannelHandlerContext ctx, ClientAttribute attribute);

    /**
     * 当连接关闭或通道失效时触发。
     * 用于资源清理、重连机制或记录连接关闭状态
     *
     * @param ctx 上下文
     */
    void channelInactive(ChannelHandlerContext ctx);

    /**
     * 触发时机：当 Channel 从 EventLoop 中注销时调用。
     * 用途：清理与 Channel 相关的资源。
     * 注意：此时 Channel 可能已关闭或未绑定。
     * <p>
     * 当关闭连接时，第二个调用channelUnregistered
     *
     * @param ctx 上下文
     */
    void channelUnregistered(ChannelHandlerContext ctx);

    /**
     * 当客户端连接尝试失败时调用（连接被拒绝，达到最大重试次数）
     *
     * @param cause     失败原因 (可选，如果原因明确)
     * @param attribute 客户端属性
     */
    void connectionFailed(Throwable cause, ClientAttribute attribute);
}
