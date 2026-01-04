package com.ruinap.infra.framework.core.event;

/**
 * 【自定义事件】调度核心配置文件刷新事件
 *
 * @author qianye
 * @create 2025-12-19 17:27
 */
public class RcsCoreConfigRefreshEvent extends ApplicationEvent {
    public RcsCoreConfigRefreshEvent(Object source) {
        super(source);
    }
}
