package com.ruinap.adapter.communicate.base.handler;

import cn.hutool.json.JSONObject;
import com.slamopto.communicate.base.ClientAttribute;
import io.netty.channel.ChannelHandlerContext;

/**
 * 客户端处理器基类
 *
 * @author qianye
 * @create 2025-05-09 15:31
 */
public interface IBaseClientHandler {

    /**
     * 触发时机：当用户自定义事件触发时调用
     * 用途：处理Netty内置事件、用户自定义事件等
     *
     * @param ctx 上下文
     * @param evt 事件对象
     */
    JSONObject userEventTriggered(ChannelHandlerContext ctx, Object evt, ClientAttribute attribute);

    /**
     * 触发时机：当从 Channel 读取到数据时调用（核心方法）
     * 用途：处理特定类型的消息（泛型 I 指定消息类型）
     * 特点：自动释放消息对象（通过 ReferenceCountUtil.release(msg)）
     *
     * @param ctx   上下文
     * @param frame 数据帧
     */
    void channelRead0(ChannelHandlerContext ctx, Object frame, ClientAttribute attribute);

    /**
     * 触发时机：当处理过程中发生异常时调用
     * 用途：处理异常（如 IO 错误、解码失败等），需记录日志或关闭连接
     * 注意：若不重写此方法，默认行为是关闭 Channel
     *
     * @param ctx   上下文
     * @param cause 异常
     */
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause, ClientAttribute attribute);
}
