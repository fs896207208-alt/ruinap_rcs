package com.ruinap.adapter.communicate.server.handler.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.MqttTopicTrie;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VDA5050 MQTT协议处理类
 * 核心功能：
 * 1. 处理MQTT客户端连接、订阅、发布等协议流程
 * 2. 实现主题通配符匹配的消息转发
 * 3. 管理客户端连接状态和订阅关系
 *
 * @author qianye
 * @create 2025-06-10 17:47
 */
public class Vda5050MqttServerHandler implements IServerHandler {

    /**
     * MQTT 主题前缀树：用于消息分发匹配
     */
    private static final MqttTopicTrie TOPIC_TRIE = new MqttTopicTrie();

    /**
     * 客户端订阅记录：用于断线时的资源清理
     */
    private static final ConcurrentMap<String, Set<String>> CLIENT_SUBSCRIPTIONS = new ConcurrentHashMap<>();

    /**
     * 客户端ID映射表：用于管理连接唯一性
     */
    private static final ConcurrentMap<String, String> CLIENT_ID_TO_CHANNEL_ID = new ConcurrentHashMap<>();

    /**
     * 消息ID生成器，保证ID唯一性
     */
    private static final AtomicInteger PACKET_ID_GENERATOR = new AtomicInteger(1);

    @Override
    public JSONObject userEventTriggered(ChannelHandlerContext ctx, Object evt, ServerAttribute attribute) {
        return null;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object frame, ServerAttribute attribute) {
        if (frame instanceof MqttMessage mqttMessage) {
            MqttMessageType messageType = mqttMessage.fixedHeader().messageType();
            try {
                switch (messageType) {
                    case CONNECT:
                        handleConnect(ctx, (MqttConnectMessage) mqttMessage, attribute);
                        break;
                    case PUBLISH:
                        handlePublish(ctx, (MqttPublishMessage) mqttMessage, attribute);
                        break;
                    case SUBSCRIBE:
                        handleSubscribe(ctx, (MqttSubscribeMessage) mqttMessage);
                        break;
                    case UNSUBSCRIBE:
                        handleUnsubscribe(ctx, (MqttUnsubscribeMessage) mqttMessage);
                        break;
                    case PINGREQ:
                        handlePingReq(ctx);
                        break;
                    case DISCONNECT:
                        handleDisconnect(ctx);
                        break;
                    case PUBACK:
                        RcsLog.consoleLog.debug("收到PUBACK确认包");
                        break;
                    case PUBREC:
                    case PUBREL:
                    case PUBCOMP:
                        // QoS 2 流程保留扩展点
                        break;
                    default:
                        RcsLog.consoleLog.warn("收到未处理的MQTT消息类型: {}", messageType);
                        break;
                }
            } catch (Exception e) {
                RcsLog.consoleLog.error("处理MQTT消息时发生异常: {}", e.getMessage());
                ctx.close();
            }
        }
    }

    /**
     * 处理客户端连接请求
     * 包含互斥登录逻辑：如果相同ID已存在，则踢掉旧连接
     */
    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg, ServerAttribute attribute) {
        String clientId = msg.payload().clientIdentifier();

        // 1. 校验 ClientID
        if (StrUtil.isBlank(clientId)) {
            sendConnAck(ctx, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            ctx.close();
            return;
        }

        // 2. 直接从 Channel 属性获取当前所属的 NettyServer 实例
        NettyServer server = ctx.channel().attr(NettyServer.SERVER_REF_KEY).get();
        if (server == null) {
            RcsLog.consoleLog.error("严重错误: 无法找到协议 [{}] 对应的 NettyServer 实例", attribute.getProtocol());
            ctx.close();
            return;
        }

        // 3. 处理旧连接 (剔除重复登录)
        // 使用 server.getContexts() 替代 NettyServer.getCONTEXTS()
        String oldChannelId = CLIENT_ID_TO_CHANNEL_ID.get(clientId);
        if (oldChannelId != null) {
            ChannelHandlerContext oldCtx = server.getContexts().get(oldChannelId);
            if (oldCtx != null) {
                RcsLog.consoleLog.warn("Client ID 冲突，踢出旧连接: {}", clientId);
                oldCtx.close();
            }
        }

        // 4. 注册新连接
        String newChannelId = ctx.channel().id().asShortText();
        String fullChannelId = ctx.channel().id().toString();

        // 必须同时存入 contexts 和 channelIds
        // 1) 存入连接池 (用于发送消息)
        server.getContexts().put(newChannelId, ctx);
        // 2) 存入 ID 映射 (用于 NettyServer.handlerRemoved 清理和监控)
        server.getChannelIds().put(fullChannelId, newChannelId);

        // 维护 Handler 内部映射
        CLIENT_ID_TO_CHANNEL_ID.put(clientId, newChannelId);
        // 绑定 Channel 属性
        ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).set(clientId);
        RcsLog.consoleLog.info("MQTT Client 连接成功: {}", clientId);

        // 5. 发送连接确认 (CONNACK)
        sendConnAck(ctx, MqttConnectReturnCode.CONNECTION_ACCEPTED);
    }

    /**
     * 处理消息发布请求
     * 包含零拷贝转发逻辑
     */
    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg, ServerAttribute attribute) {
        String topicName = msg.variableHeader().topicName();
        ByteBuf payload = msg.payload();
        MqttQoS publishQoS = msg.fixedHeader().qosLevel();

        // 1. 协议响应 (保持在 IO 线程，快速响应)
        if (publishQoS == MqttQoS.AT_LEAST_ONCE) {
            sendPubAck(ctx, msg.variableHeader().packetId());
        } else if (publishQoS == MqttQoS.EXACTLY_ONCE) {
            sendPubRec(ctx, msg.variableHeader().packetId());
        }

        // 2. 查找订阅者
        Map<String, MqttQoS> matchedSubscribers = TOPIC_TRIE.match(topicName);
        if (matchedSubscribers.isEmpty()) {
            return;
        }

        // 直接从 Channel 属性获取当前所属的 NettyServer 实例
        NettyServer server = ctx.channel().attr(NettyServer.SERVER_REF_KEY).get();
        if (server == null) {
            return;
        }

        matchedSubscribers.forEach((subscriberChannelId, subscribeQoS) -> {
            if (subscriberChannelId.equals(ctx.channel().id().asShortText())) {
                return;
            }

            ChannelHandlerContext subCtx = server.getContexts().get(subscriberChannelId);
            if (subCtx != null && subCtx.channel().isActive()) {
                MqttQoS finalQoS = (publishQoS.value() < subscribeQoS.value()) ? publishQoS : subscribeQoS;

                // 零拷贝转发
                // retainedDuplicate() 创建一个新的 Buffer 对象指向同一块内存，并增加引用计数 +1
                // 必须 retain，因为 writeAndFlush 发送完成后会 release 这个 Buffer
                ByteBuf outgoingPayload = payload.retainedDuplicate();

                MqttFixedHeader fixedHeader = new MqttFixedHeader(
                        MqttMessageType.PUBLISH, false, finalQoS, false, 0
                );
                MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topicName, getNewPacketId());

                MqttPublishMessage publishMessage = new MqttPublishMessage(fixedHeader, variableHeader, outgoingPayload);

                subCtx.writeAndFlush(publishMessage).addListener(future -> {
                    if (!future.isSuccess()) {
                        RcsLog.consoleLog.error("转发失败: {}", future.cause().getMessage());
                    }
                });
            }
        });
    }

    /**
     * 处理订阅请求
     */
    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        String channelId = ctx.channel().id().asShortText();
        List<MqttTopicSubscription> topicSubscriptions = msg.payload().topicSubscriptions();
        List<Integer> grantedQoSLevels = new ArrayList<>(topicSubscriptions.size());
        String clientId = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();

        for (MqttTopicSubscription subscription : topicSubscriptions) {
            String topic = subscription.topicFilter();
            MqttQoS qos = subscription.qualityOfService();

            // 记录订阅关系
            TOPIC_TRIE.subscribe(topic, channelId, qos);
            CLIENT_SUBSCRIPTIONS.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(topic);

            grantedQoSLevels.add(qos.value());
            RcsLog.consoleLog.info("MQTT订阅请求: 客户端=[{}], 主题=[{}], QoS=[{}]", clientId, topic, qos);
        }

        // 回复SUBACK
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(msg.variableHeader().messageId());
        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQoSLevels);
        ctx.writeAndFlush(new MqttSubAckMessage(fixedHeader, variableHeader, payload));
    }

    /**
     * 处理取消订阅请求
     */
    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        String channelId = ctx.channel().id().asShortText();
        List<String> topics = msg.payload().topics();
        String clientId = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();

        for (String topic : topics) {
            // 移除订阅关系
            TOPIC_TRIE.unsubscribe(topic, channelId);
            Set<String> subs = CLIENT_SUBSCRIPTIONS.get(channelId);
            if (subs != null) {
                subs.remove(topic);
            }
            RcsLog.consoleLog.info("MQTT取消订阅: 客户端=[{}], 主题=[{}]", clientId, topic);
        }

        // 回复UNSUBACK
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(msg.variableHeader().messageId());
        ctx.writeAndFlush(new MqttUnsubAckMessage(fixedHeader, variableHeader));
    }

    /**
     * 处理心跳请求
     */
    private void handlePingReq(ChannelHandlerContext ctx) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        ctx.writeAndFlush(new MqttMessage(fixedHeader));
    }

    /**
     * 处理断开连接通知
     */
    private void handleDisconnect(ChannelHandlerContext ctx) {
        String clientId = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        RcsLog.consoleLog.info("收到客户端主动断开连接请求: [{}]", clientId);
        ctx.close();
    }

    /**
     * 发送连接确认包
     */
    private void sendConnAck(ChannelHandlerContext ctx, MqttConnectReturnCode returnCode) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader variableHeader = new MqttConnAckVariableHeader(returnCode, false);
        ctx.writeAndFlush(new MqttConnAckMessage(fixedHeader, variableHeader));
    }

    /**
     * 发送发布确认包 (QoS 1)
     */
    private void sendPubAck(ChannelHandlerContext ctx, int packetId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(packetId);
        ctx.writeAndFlush(new MqttPubAckMessage(fixedHeader, variableHeader));
    }

    /**
     * 发送发布接收包 (QoS 2 第一步)
     */
    private void sendPubRec(ChannelHandlerContext ctx, int packetId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(packetId);
        ctx.writeAndFlush(new MqttMessage(fixedHeader, variableHeader));
    }

    /**
     * 生成下一个PacketID
     * 循环使用 1-65535 范围
     */
    private int getNewPacketId() {
        return PACKET_ID_GENERATOR.getAndUpdate(id -> (id >= 65535) ? 1 : id + 1);
    }

    /**
     * 连接断开时的资源清理
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx, ServerAttribute attribute) {
        String channelId = ctx.channel().id().asShortText();
        String clientId = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();

        // 直接从 Channel 属性获取当前所属的 NettyServer 实例
        NettyServer server = ctx.channel().attr(NettyServer.SERVER_REF_KEY).get();
        if (server != null) {
            server.getContexts().remove(channelId);
        }

        // 仅移除当前连接的映射，防止误删新建立的连接映射
        if (clientId != null) {
            CLIENT_ID_TO_CHANNEL_ID.computeIfPresent(clientId, (k, v) -> v.equals(channelId) ? null : v);
        }

        // 清理订阅树
        Set<String> subscribedTopics = CLIENT_SUBSCRIPTIONS.remove(channelId);
        if (subscribedTopics != null) {
            for (String topic : subscribedTopics) {
                TOPIC_TRIE.unsubscribe(topic, channelId);
            }
            RcsLog.consoleLog.info("客户端资源清理完成: Client=[{}], 清理订阅数=[{}]", clientId, subscribedTopics.size());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause, ServerAttribute attribute) {
        RcsLog.consoleLog.error("MQTT通道异常: {}", cause.getMessage());
        ctx.close();
    }
}