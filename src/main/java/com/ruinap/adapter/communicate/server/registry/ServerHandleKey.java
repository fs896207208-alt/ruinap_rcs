package com.ruinap.adapter.communicate.server.registry;

import com.ruinap.infra.enums.netty.ProtocolEnum;

/**
 * 强类型路由键
 * 使用 Record 自动实现 equals/hashCode
 *
 * @author qianye
 * @create 2026-01-22 11:38
 */
public record ServerHandleKey(ProtocolEnum protocol, String path) {
    public ServerHandleKey {
        // 规范化 path，防止 "/ws" 和 "ws" 匹配不上
        if (path == null) path = "";
        if (!path.startsWith("/") && !path.isEmpty()) {
            path = "/" + path;
        }
    }
}
