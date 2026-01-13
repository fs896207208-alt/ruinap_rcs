package com.ruinap.adapter.communicate.base.enums;

import io.netty.util.AttributeKey;
import lombok.AllArgsConstructor;

/**
 * Netty的AttributeKey枚举类
 *
 * @author qianye
 * @create 2025-02-20 16:20
 */
@AllArgsConstructor
public enum AttributeKeyEnum {
    /**
     * 服务端ID
     */
    SERVER_ID(AttributeKey.valueOf("serverId")),

    /**
     * 客户端ID
     */
    CLIENT_ID(AttributeKey.valueOf("clientId")),

    /**
     * 通信协议
     */
    PROTOCOL(AttributeKey.valueOf("protocol")),

    /**
     * 路径
     */
    PATH(AttributeKey.valueOf("path"));

    /**
     * 存储 Netty 的 AttributeKey 对象
     */
    private final AttributeKey<String> key;

    /**
     * 获取 Netty 的 AttributeKey 对象
     *
     * @return AttributeKey 对象
     */
    public AttributeKey<String> key() {
        return this.key;
    }
}