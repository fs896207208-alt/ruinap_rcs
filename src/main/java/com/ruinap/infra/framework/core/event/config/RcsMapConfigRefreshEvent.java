package com.ruinap.infra.framework.core.event.config;

import com.ruinap.infra.framework.core.event.ApplicationEvent;

/**
 * 【自定义事件】地图配置文件刷新事件
 *
 * @author qianye
 * @create 2025-12-22 10:31
 */
public class RcsMapConfigRefreshEvent extends ApplicationEvent {
    public RcsMapConfigRefreshEvent(Object source) {
        super(source);
    }
}
