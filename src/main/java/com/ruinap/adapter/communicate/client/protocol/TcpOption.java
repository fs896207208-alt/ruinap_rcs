package com.ruinap.adapter.communicate.client.protocol;

import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.protocol.IProtocolOption;
import com.slamopto.communicate.client.NettyClient;
import com.slamopto.log.RcsLog;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

/**
 * TCP 客户端协议参数实现
 * 支持标准化 TCP 连接 URI
 * 消息内容为 TCP byte[]帧
 *
 * @author qianye
 * @create 2025-04-24 16:43
 */
public class TcpOption implements IProtocolOption<Bootstrap, NettyClient> {

    /**
     * 设置客户端 Bootstrap 的参数
     *
     * @param bootstrap 客户端 Bootstrap 对象
     */
    @Override
    public void setOption(Bootstrap bootstrap) {
        // 连接超时时间（毫秒）
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        // 禁用Nagle算法
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        RcsLog.consoleLog.info("客户端使用 [" + ProtocolEnum.TCP_CLIENT + "] 协议设置参数");
    }

    /**
     * 设置客户端 Bootstrap 的子参数
     *
     * @param bootstrap 客户端 Bootstrap 对象
     */
    @Override
    public void setChildOption(Bootstrap bootstrap) {
        //客户端无需设置子参数
    }

    /**
     * 创建客户端连接所需的 ChannelHandler 列表
     *
     * @return ChannelHandler集合
     */
    @Override
    public void createHandlers(ChannelPipeline pipeline, NettyClient client) {
        // 使用字节编解码器
        pipeline.addLast(new ByteArrayEncoder());
        pipeline.addLast(new ByteArrayDecoder());

        // 指定Netty客户端为消息处理器
        pipeline.addLast(client);
    }


    /**
     * 包装原始消息
     *
     * @param rawMessage 原始消息
     * @return 包装后的消息
     */
    @Override
    public Object wrapMessage(Object rawMessage) {
        // TCP协议需要将消息包装为byte[]数据
        if (rawMessage instanceof String) {
            // 文本消息转换为byte[]数组
            return ((String) rawMessage).getBytes();
        } else if (rawMessage instanceof byte[]) {
            // 已经是byte[]格式，直接返回
            return rawMessage;
        }

        // 处理其他类型或抛出异常
        throw new IllegalArgumentException("TCP协议不支持的消息类型: " +
                (rawMessage != null ? rawMessage.getClass().getSimpleName() : "null"));
    }

}
