package com.ruinap.adapter.communicate.base.protocol;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.ChannelPipeline;

/**
 * 通信协议参数接口
 * 模块通过此接口支持不同的底层协议
 *
 * @author qianye
 * @create 2025-04-24 15:30
 */
public interface IProtocolOption<B extends AbstractBootstrap<B, ?>, C> {

    /**
     * 设置 Channel 的配置选项
     *
     * @param bootstrap 具体的 Bootstrap 对象（例如 Bootstrap 或 ServerBootstrap 实例）
     */
    void setOption(B bootstrap);

    /**
     * 设置 Channel 的子配置选项
     *
     * @param bootstrap 具体的 Bootstrap 对象（例如 Bootstrap 或 ServerBootstrap 实例）
     */
    void setChildOption(B bootstrap);

    /**
     * 创建 Channel 所需的 ChannelHandler 列表
     * 此方法现在接受一个泛型上下文对象 C，可以是 NettyServer 或 NettyClient。
     *
     * @param pipeline Channel 的 Pipeline
     * @param context  NettyServer 或 NettyClient 对象
     */
    void createHandlers(ChannelPipeline pipeline, C context);

    /**
     * 包装消息, 用于在发送消息时进行包装
     * 使消息支持不同协议
     *
     * @param rawMessage 原始消息
     * @return 包装后的消息
     */
    Object wrapMessage(Object rawMessage);

}
