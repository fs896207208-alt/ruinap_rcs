package com.ruinap.adapter.communicate.base.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 服务端路由枚举类
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@Getter
@AllArgsConstructor
public enum ServerRouteEnum {
    /**
     * WebSocket控制台路由
     */
    CONSOLE("/websocket/console"),
    /**
     * WebSocket可视化路由
     */
    VISUAL("/websocket/visual"),
    /**
     * WebSocket调度业务路由
     */
    BUSINESS("/websocket/business"),
    /**
     * WebSocket调度仿真路由
     */
    SIMULATION("/websocket/simulation"),
    /**
     * MQTT路由
     */
    MQTT("");

    /**
     * 路由
     * <p>
     * -- GETTER --
     * 获取路由
     */
    private final String route;

    /**
     * 根据路由字符串获取对应的路由对象
     *
     * @param route 路由字符串
     * @return 路由对象
     */
    public static ServerRouteEnum fromRoute(String route) {
        for (ServerRouteEnum thisProtocol : values()) {
            if (thisProtocol.route.equalsIgnoreCase(route)) {
                return thisProtocol;
            }
        }
        return null;
    }
}