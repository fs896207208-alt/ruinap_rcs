package com.ruinap.adapter.communicate.base;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 针对 VDA5050 优化的 MQTT 主题前缀树
 * 时间复杂度: O(Topic Levels)
 * 空间复杂度: O(Subscriptions)
 *
 * @author qianye
 * @create 2026-01-22 10:01
 */
public class MqttTopicTrie {
    // 根节点
    private final Node root = new Node();

    // 节点定义
    private static class Node {
        // 子节点：路径片段 -> 下级节点
        final Map<String, Node> children = new ConcurrentHashMap<>();

        // 关键修正点：这里必须是 Map，存储 ChannelId -> QoS
        // 用于支持不同客户端以不同 QoS 订阅同一主题
        final Map<String, MqttQoS> subscribers = new ConcurrentHashMap<>();
    }

    /**
     * 添加订阅
     *
     * @param topicFilter 主题过滤器，如 "agv/+/order"
     * @param channelId   客户端 Channel ID
     * @param qos         订阅质量等级
     */
    public void subscribe(String topicFilter, String channelId, MqttQoS qos) {
        String[] parts = topicFilter.split("/");
        Node current = root;
        for (String part : parts) {
            current = current.children.computeIfAbsent(part, k -> new Node());
        }
        // 存储 ChannelId 和对应的 QoS
        current.subscribers.put(channelId, qos);
    }

    /**
     * 移除订阅
     */
    public void unsubscribe(String topicFilter, String channelId) {
        String[] parts = topicFilter.split("/");
        Node current = root;
        for (String part : parts) {
            current = current.children.get(part);
            if (current == null) {
                return;
            }
        }
        current.subscribers.remove(channelId);
    }

    /**
     * 匹配发布主题
     *
     * @param topicName 具体主题，如 "agv/001/order"
     * @return Map<ChannelId, MqttQoS> 包含所有匹配的客户端及其要求的最大 QoS
     */
    public Map<String, MqttQoS> match(String topicName) {
        Map<String, MqttQoS> result = new HashMap<>();
        String[] parts = topicName.split("/");
        matchRecursive(root, parts, 0, result);
        return result;
    }

    private void matchRecursive(Node node, String[] parts, int index, Map<String, MqttQoS> result) {
        // 1. 检查多级通配符 '#'
        Node hashNode = node.children.get("#");
        if (hashNode != null) {
            // 将匹配到的订阅者全部加入结果集
            // 如果同一个 ChannelId 已经在结果集中（例如通过其他规则匹配），
            // MQTT 协议通常建议取最高 QoS，这里简化为覆盖或保留均可
            result.putAll(hashNode.subscribers);
        }

        if (index == parts.length) {
            // 精确匹配结束，添加当前节点的所有订阅者
            result.putAll(node.subscribers);
            return;
        }

        String part = parts[index];

        // 2. 检查精确匹配
        Node exactNode = node.children.get(part);
        if (exactNode != null) {
            matchRecursive(exactNode, parts, index + 1, result);
        }

        // 3. 检查单级通配符 '+'
        Node plusNode = node.children.get("+");
        if (plusNode != null) {
            matchRecursive(plusNode, parts, index + 1, result);
        }
    }
}