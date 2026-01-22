package com.ruinap.infra.framework.core.event.config;

import com.ruinap.infra.framework.core.event.ApplicationEvent;

/**
 * 【自定义事件】任务配置文件刷新事件
 *
 * @author qianye
 * @create 2025-12-22 11:16
 */
public class RcsTaskConfigRefreshEvent extends ApplicationEvent {
    public RcsTaskConfigRefreshEvent(Object source) {
        super(source);
    }
}
