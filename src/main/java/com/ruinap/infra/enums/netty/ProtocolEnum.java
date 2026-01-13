package com.ruinap.infra.enums.netty;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 协议枚举类
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@Getter
@AllArgsConstructor
public enum ProtocolEnum {
    /**
     * WebSocket服务端协议
     */
    WEBSOCKET_SERVER("ws_server"),
    /**
     * MQTT服务端协议
     */
    MQTT_SERVER("mq_server"),
    /**
     * WebSocket客户端协议
     */
    WEBSOCKET_CLIENT("ws_client"),
    /**
     * TCP客户端协议
     */
    TCP_CLIENT("tcp_client");

    /**
     * 协议对象
     * <p>
     * -- GETTER --
     * 获取协议对象
     */
    private final String protocol;

    /**
     * 根据协议字符串获取对应的协议对象
     *
     * @param protocol 协议字符串
     * @return 协议对象
     */
    public static ProtocolEnum fromProtocol(String protocol) {
        for (ProtocolEnum thisProtocol : values()) {
            if (thisProtocol.protocol.equalsIgnoreCase(protocol)) {
                return thisProtocol;
            }
        }
        return null;
    }
}