package com.ruinap.adapter.communicate.server.protocol;

import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IdleEventHandler;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * MQTT 协议参数实现
 *
 * @author qianye
 * @create 2025-06-10 16:57
 */
public class MqttOption implements IProtocolOption<ServerBootstrap, NettyServer> {

    /**
     * 设置服务端 Bootstrap 的参数
     *
     * @param bootstrap 服务端 Bootstrap 对象
     */
    @Override
    public void setOption(ServerBootstrap bootstrap) {
        // 设置TCP连接请求的最大队列长度
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        // 使用池化的ByteBuf分配器
        bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        RcsLog.consoleLog.info("服务端使用 [" + ProtocolEnum.MQTT_SERVER + "] 协议设置参数");
    }

    /**
     * 设置服务端连接所需的 ChannelHandler
     *
     * @param bootstrap 服务端 Bootstrap 对象
     */
    @Override
    public void setChildOption(ServerBootstrap bootstrap) {
        // 启用心跳机制，保持长连接
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        // 禁用 Nagle 算法，减少延迟
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        // 使用池化的ByteBuf分配器
        bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        RcsLog.consoleLog.info("服务端使用[" + ProtocolEnum.MQTT_SERVER + "]协议设置子参数");
    }

    @Override
    public void createHandlers(ChannelPipeline pipeline, NettyServer server) {
        pipeline.addLast("mqtt-decoder", new MqttDecoder());
        pipeline.addLast("mqtt-encoder", MqttEncoder.INSTANCE);
        pipeline.addLast("idle-handler", new IdleStateHandler(1, 1, 1, TimeUnit.HOURS));
        pipeline.addLast(new IdleEventHandler());
        
        try {
            pipeline.addLast(server);
        } catch (Exception e) {
            RcsLog.consoleLog.error("添加 MQTT 业务处理器失败", e);
            throw new RuntimeException("添加 MQTT 业务处理器失败", e);
        }
    }

    /**
     * 包装原始消息
     *
     * @param rawMessage 原始消息
     * @return 包装后的消息
     */
    @Override
    public Object wrapMessage(Object rawMessage) {
        // 对于 MQTT 协议，通常不需要在发送前进行额外包装，因为 MqttEncoder 会处理 MqttMessage 对象。
        // 但是如果你的业务逻辑中，原始消息是 String 或 byte[]，需要手动构建 MqttPublishMessage。
        if (rawMessage instanceof String) {
            // 假设我们希望发送一个 PUBLISH 消息
            return MqttMessageBuilders.publish()
                    .topicName("default/topic")
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .payload(io.netty.buffer.Unpooled.copiedBuffer((String) rawMessage, CharsetUtil.UTF_8))
                    .build();
        } else if (rawMessage instanceof JSONObject) {
            return MqttMessageBuilders.publish()
                    .topicName("default/topic")
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .payload(io.netty.buffer.Unpooled.copiedBuffer(((JSONObject) rawMessage).toString(), CharsetUtil.UTF_8))
                    .build();
        } else if (rawMessage instanceof byte[]) {
            return MqttMessageBuilders.publish()
                    .topicName("default/topic")
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .payload(io.netty.buffer.Unpooled.wrappedBuffer((byte[]) rawMessage))
                    .build();
        } else if (rawMessage instanceof io.netty.handler.codec.mqtt.MqttMessage) {
            // 如果已经是 MqttMessage 类型，直接返回
            return rawMessage;
        }

        throw new IllegalArgumentException("MQTT 协议不支持的消息类型: " +
                (rawMessage != null ? rawMessage.getClass().getSimpleName() : "null"));
    }
}
