package com.ruinap.adapter.communicate.server.handler.impl;

import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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

    // 客户端ID与通道ID的映射关系（用于检测重复连接）
    private static final ConcurrentMap<String, String> CLIENT_ID_TO_CHANNEL_ID = new ConcurrentHashMap<>();
    // 主题订阅关系存储结构：<主题, <订阅者通道ID, QoS等级>>
    private static final ConcurrentMap<String, ConcurrentMap<String, MqttQoS>> TOPIC_SUBSCRIPTIONS = new ConcurrentHashMap<>();

    @Override
    public JSONObject userEventTriggered(ChannelHandlerContext ctx, Object evt, ServerAttribute attribute) {
        JSONObject jsonObject = new JSONObject();
        // 处理空闲状态事件（心跳检测）
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                String clientIdentifier = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();
                RcsLog.consoleLog.warn("MQTT 客户端 [" + clientIdentifier + "] 读空闲超时，关闭连接。");
                ctx.close();
                jsonObject.set("idle_timeout", true); // 标记空闲超时
            }
        }
        return jsonObject;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg, ServerAttribute attribute) {
        // 类型检查：仅处理MQTT消息
        if (!(msg instanceof MqttMessage)) {
            RcsLog.consoleLog.error("接收到非 MQTT 消息类型: " + msg.getClass().getName());
            ReferenceCountUtil.release(msg);
            ctx.close();
            return;
        }

        MqttMessage mqttMessage = (MqttMessage) msg;
        RcsLog.consoleLog.info("接收到 MQTT 消息: " + mqttMessage.fixedHeader().messageType());

        // 根据MQTT消息类型路由到不同处理方法
        switch (mqttMessage.fixedHeader().messageType()) {
            case CONNECT:    // 连接请求
                handleConnect(ctx, (MqttConnectMessage) mqttMessage);
                break;
            case PUBLISH:    // 发布消息
                handlePublish(ctx, (MqttPublishMessage) mqttMessage);
                break;
            case SUBSCRIBE:  // 订阅请求
                handleSubscribe(ctx, (MqttSubscribeMessage) mqttMessage);
                break;
            case UNSUBSCRIBE: // 取消订阅
                handleUnsubscribe(ctx, (MqttUnsubscribeMessage) mqttMessage);
                break;
            case PINGREQ:    // 心跳请求
                handlePingReq(ctx);
                break;
            case DISCONNECT: // 断开连接
                handleDisconnect(ctx);
                break;
            case PUBACK:     // QoS1确认
                handlePubAck(ctx, (MqttPubAckMessage) mqttMessage);
                break;
            case PUBREC:     // QoS2第一步确认
                handlePubRec(ctx, mqttMessage);
                break;
            case PUBREL:     // QoS2第二步确认
                handlePubRel(ctx, mqttMessage);
                break;
            case PUBCOMP:    // QoS2完成确认
                handlePubComp(ctx, mqttMessage);
                break;
            default:
                RcsLog.consoleLog.error("不支持的 MQTT 消息类型: " + mqttMessage.fixedHeader().messageType());
                ctx.close();
                break;
        }
    }

    /**
     * 处理客户端连接请求
     * 核心逻辑：
     * 1. 检查客户端ID是否已存在（拒绝重复连接）
     * 2. 存储客户端连接信息
     * 3. 返回连接响应（CONNACK）
     */
    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        MqttConnectReturnCode returnCode = MqttConnectReturnCode.CONNECTION_ACCEPTED;

        // 检查客户端ID冲突（已连接且不是当前通道）
        if (CLIENT_ID_TO_CHANNEL_ID.containsKey(clientId) && !CLIENT_ID_TO_CHANNEL_ID.get(clientId).equals(ctx.channel().id().asShortText())) {
            returnCode = MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
            RcsLog.consoleLog.warn("客户端 ID [" + clientId + "] 已从另一个通道连接。拒绝新连接");
        } else {
            // 存储客户端信息
            ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).set(clientId);
            CLIENT_ID_TO_CHANNEL_ID.put(clientId, ctx.channel().id().asShortText());
            RcsLog.consoleLog.info("MQTT 客户端 [" + clientId + "] 连接成功");
        }

        // 构建连接响应
        MqttConnAckMessage connAckMessage = MqttMessageBuilders.connAck()
                .sessionPresent(false)
                .returnCode(returnCode)
                .build();
        ctx.writeAndFlush(connAckMessage);

        // 连接失败时关闭通道
        if (returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            ctx.close();
        }
    }

    /**
     * 处理消息发布（PUBLISH）
     * 核心流程：
     * 1. 解析消息内容（主题/QoS/负载）
     * 2. 根据QoS级别发送确认（QoS1: PUBACK, QoS2: PUBREC）
     * 3. 将消息转发给所有匹配的订阅者
     */
    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        String topicName = msg.variableHeader().topicName();
        MqttQoS qos = msg.fixedHeader().qosLevel();
        int packetId = ((MqttPublishVariableHeader) msg.variableHeader()).packetId(); // 消息ID（QoS>0时存在）

        // 读取消息内容
        byte[] payload = new byte[msg.payload().readableBytes()];
        msg.payload().readBytes(payload);
        String messageContent = new String(payload, CharsetUtil.UTF_8);

        RcsLog.consoleLog.info("接收 PUBLISH 来自 [" + ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get() +
                "] 关于主题: '" + topicName + "' (QoS: " + qos + ") 带 payload: '" + messageContent + "'");

        // QoS级别处理
        switch (qos) {
            case AT_LEAST_ONCE: // QoS 1
                MqttPubAckMessage pubAckMessage = (MqttPubAckMessage) MqttMessageBuilders.pubAck()
                        .packetId(packetId)
                        .build();
                ctx.writeAndFlush(pubAckMessage);
                break;
            case EXACTLY_ONCE: // QoS 2 (第一步)
                MqttMessage pubRecMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 2),
                        MqttMessageIdVariableHeader.from(packetId), null);
                ctx.writeAndFlush(pubRecMessage);
                break;
            default: // QoS 0 不需要确认
                break;
        }

        // 消息转发：遍历所有订阅主题，匹配并转发
        TOPIC_SUBSCRIPTIONS.forEach((subTopic, subscribers) -> {
            if (matchesTopic(topicName, subTopic)) { // 主题匹配检查
                subscribers.forEach((subscriberChannelId, subQoS) -> {
                    // 获取订阅者通道
                    ChannelHandlerContext subscriberCtx = NettyServer.getCONTEXTS().get(subscriberChannelId);
                    if (subscriberCtx != null && subscriberCtx.channel().isActive()) {
                        // 构建转发消息（使用订阅者指定的QoS）
                        MqttPublishMessage forwardPublish = MqttMessageBuilders.publish()
                                .topicName(topicName)
                                .qos(subQoS)
                                .retained(msg.fixedHeader().isRetain())
                                .payload(Unpooled.copiedBuffer(payload))
                                .build();
                        subscriberCtx.writeAndFlush(forwardPublish);
                        RcsLog.consoleLog.info("将 PUBLISH 转发到客户端 [" +
                                subscriberCtx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get() +
                                "] 关于主题: '" + topicName + "'");
                    }
                });
            }
        });
    }

    /**
     * 处理订阅请求（SUBSCRIBE）
     * 流程：
     * 1. 记录订阅关系（主题 + QoS）
     * 2. 返回订阅确认（SUBACK）包含授予的QoS列表
     */
    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        int packetId = msg.variableHeader().messageId(); // 消息ID
        String clientId = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        String channelId = ctx.channel().id().asShortText();

        // 处理每个订阅请求
        List<Integer> grantedQoS = msg.payload().topicSubscriptions().stream()
                .map(subscription -> {
                    String topic = subscription.topicName();
                    MqttQoS qos = subscription.qualityOfService();
                    // 存储订阅关系：主题 -> (通道ID -> QoS)
                    TOPIC_SUBSCRIPTIONS.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                            .put(channelId, qos);
                    RcsLog.consoleLog.info("Client [" + clientId + "] 订阅主题: '" + topic + "' with QoS: " + qos);
                    return qos.value(); // 返回授予的QoS值
                })
                .collect(Collectors.toList());

        // 构建订阅确认响应
        MqttSubAckPayload subAckPayload = new MqttSubAckPayload(grantedQoS);
        MqttSubAckMessage subAckMessage = new MqttSubAckMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 2),
                MqttMessageIdVariableHeader.from(packetId),
                subAckPayload
        );
        ctx.writeAndFlush(subAckMessage);
    }

    /**
     * 处理取消订阅（UNSUBSCRIBE）
     * 流程：
     * 1. 移除指定主题的订阅关系
     * 2. 返回取消订阅确认（UNSUBACK）
     */
    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        int packetId = msg.variableHeader().messageId(); // 消息ID
        String channelId = ctx.channel().id().asShortText();
        String clientId = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();

        // 遍历所有要取消的主题
        for (String topic : msg.payload().topics()) {
            TOPIC_SUBSCRIPTIONS.computeIfPresent(topic, (k, v) -> {
                v.remove(channelId); // 移除当前通道的订阅
                RcsLog.consoleLog.info("Client [" + clientId + "] 已取消订阅主题: '" + topic + "'");
                return v.isEmpty() ? null : v; // 若主题无订阅者则移除主题
            });
        }

        // 发送取消订阅确认
        MqttUnsubAckMessage unsubAckMessage = MqttMessageBuilders.unsubAck()
                .packetId(packetId)
                .build();
        ctx.writeAndFlush(unsubAckMessage);
    }

    /**
     * 处理心跳请求：返回PINGRESP响应
     */
    private void handlePingReq(ChannelHandlerContext ctx) {
        MqttMessage pingResp = MqttMessage.PINGRESP;
        ctx.writeAndFlush(pingResp);
        RcsLog.consoleLog.debug("从 接收 PINGREQ [" + ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get() + "], 发送了 PINGRESP.");
    }

    /**
     * 处理断开连接：关闭通道并清理资源（handlerRemoved中执行清理）
     */
    private void handleDisconnect(ChannelHandlerContext ctx) {
        RcsLog.consoleLog.info("MQTT Client [" + ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get() + "] 正常断开连接.");
        ctx.close();
    }

    /**
     * 处理QoS1发布确认（PUBACK）
     */
    private void handlePubAck(ChannelHandlerContext ctx, MqttPubAckMessage msg) {
        RcsLog.consoleLog.info("收到 packetId 的 PUBACK: " + msg.variableHeader().messageId());
        // TODO: 可在此实现消息重发队列的清理
    }

    /**
     * 处理QoS2第一阶段确认（PUBREC）：返回PUBREL响应
     */
    private void handlePubRec(ChannelHandlerContext ctx, MqttMessage msg) {
        int packetId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        RcsLog.consoleLog.info("收到 packetId 的 PUBREC: " + packetId);

        // 发送PUBREL（QoS2第二阶段）
        MqttMessage pubRel = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 2),
                MqttMessageIdVariableHeader.from(packetId), null);
        ctx.writeAndFlush(pubRel);
    }

    /**
     * 处理QoS2第二阶段确认（PUBREL）：返回PUBCOMP响应
     */
    private void handlePubRel(ChannelHandlerContext ctx, MqttMessage msg) {
        int packetId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        RcsLog.consoleLog.info("收到 packetId 的 PUBREL: " + packetId);

        // 发送PUBCOMP（QoS2完成）
        MqttMessage pubComp = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 2),
                MqttMessageIdVariableHeader.from(packetId), null);
        ctx.writeAndFlush(pubComp);
    }

    /**
     * 处理QoS2完成确认（PUBCOMP）
     */
    private void handlePubComp(ChannelHandlerContext ctx, MqttMessage msg) {
        int packetId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        RcsLog.consoleLog.info("收到 packetId 的 PUBCOMP: " + packetId);
        // TODO: 可在此清理QoS2消息状态
    }

    /**
     * 主题匹配算法（支持+和#通配符）
     * 规则：
     * 1. '+' 匹配单级主题
     * 2. '#' 匹配多级主题（必须出现在末尾）
     * 示例：
     * - 发布主题: a/b/c
     * - 匹配订阅: a/+/c, a/#, a/b/#
     * - 不匹配: a/d, a/b/c/d
     */
    private boolean matchesTopic(String publishedTopic, String subscribedTopic) {
        String[] publishedParts = publishedTopic.split("/");
        String[] subscribedParts = subscribedTopic.split("/");

        int pIdx = 0;
        int sIdx = 0;

        while (pIdx < publishedParts.length && sIdx < subscribedParts.length) {
            String subscribedPart = subscribedParts[sIdx];
            if (subscribedPart.equals("#")) {
                return true; // 多级通配符立即匹配
            } else if (subscribedPart.equals("+")) {
                // 单级通配符，继续检查下一级
            } else if (!publishedParts[pIdx].equals(subscribedPart)) {
                return false; // 精确匹配失败
            }
            pIdx++;
            sIdx++;
        }

        // 处理边界情况（订阅主题比发布主题短）
        return (pIdx == publishedParts.length && sIdx == subscribedParts.length) ||
                (sIdx < subscribedParts.length && subscribedParts[sIdx].equals("#")); // 末尾多级通配符
    }

    /**
     * 通道移除时的清理工作
     * 1. 移除客户端ID映射
     * 2. 清理该通道的所有订阅关系
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx, ServerAttribute attribute) {
        String clientIdentifier = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        if (clientIdentifier != null) {
            // 清理客户端ID映射
            CLIENT_ID_TO_CHANNEL_ID.remove(clientIdentifier);

            // 遍历所有主题，移除该通道的订阅
            TOPIC_SUBSCRIPTIONS.forEach((topic, subscribers) -> {
                subscribers.remove(ctx.channel().id().asShortText());
                if (subscribers.isEmpty()) {
                    TOPIC_SUBSCRIPTIONS.remove(topic); // 主题无订阅者时移除
                }
            });
            RcsLog.consoleLog.info("MQTT Client [" + clientIdentifier + "] 已删除频道， 清理订阅.");
        }
    }

    /**
     * 异常处理：记录日志并关闭连接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause, ServerAttribute attribute) {
        String clientIdentifier = ctx.channel().attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        RcsLog.consoleLog.error("MQTT Client [" + clientIdentifier + "] 处理器异常: " + cause.getMessage(), cause);
        ctx.close();
    }
}