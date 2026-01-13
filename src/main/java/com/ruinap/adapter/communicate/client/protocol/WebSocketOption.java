package com.ruinap.adapter.communicate.client.protocol;

import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.protocol.IProtocolOption;
import com.slamopto.communicate.client.NettyClient;
import com.slamopto.log.RcsLog;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;

/**
 * WebSocket 客户端协议参数实现
 * 支持标准化 WebSocket 连接 URI
 * 消息内容假设为 WebSocket 文本帧
 *
 * @author qianye
 * @create 2025-04-24 16:43
 */
public class WebSocketOption implements IProtocolOption<Bootstrap, NettyClient> {

    /**
     * 设置客户端 Bootstrap 的参数
     *
     * @param bootstrap 客户端 Bootstrap 对象
     */
    @Override
    public void setOption(Bootstrap bootstrap) {
        // 连接超时时间（毫秒）
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        RcsLog.consoleLog.info("客户端使用 [" + ProtocolEnum.WEBSOCKET_SERVER + "] 协议设置参数");
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
        // HTTP 编解码器
        pipeline.addLast(new HttpClientCodec());
        // 将多个 HTTP 消息聚合为一个完整的消息
        pipeline.addLast(new HttpObjectAggregator(65536));

        // 创建 WebSocket 握手器设置
        WebSocketClientProtocolConfig config = WebSocketClientProtocolConfig.newBuilder()
                // WebSocket 服务器地址
                .webSocketUri(client.getAttribute().getUri())
                // WebSocket 协议版本
                .version(WebSocketVersion.V13)
                // 子协议
                .subprotocol(null)
                // 是否扩展
                .allowExtensions(false)
                // 自定义 HTTP 头
                .customHeaders(new DefaultHttpHeaders())
                // 最大帧大小
                .maxFramePayloadLength(65536)
                .build();
        // WebSocket 客户端协议处理器
        pipeline.addLast(new WebSocketClientProtocolHandler(config));
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
        // 根据WebSocket协议要求，将原始消息包装为WebSocketFrame
        if (rawMessage instanceof String) {
            // 文本消息使用TextWebSocketFrame包装
            return new TextWebSocketFrame((String) rawMessage);
        } else if (rawMessage instanceof byte[]) {
            // 二进制消息使用BinaryWebSocketFrame包装
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer((byte[]) rawMessage));
        }

        // 处理其他类型或抛出异常
        throw new IllegalArgumentException("WebSocket协议不支持的消息类型: " +
                (rawMessage != null ? rawMessage.getClass().getSimpleName() : "null"));
    }
}
