package com.ruinap.infra.framework.core.event;

/**
 * 【自定义事件】调度链接配置文件刷新事件
 *
 * @author qianye
 * @create 2025-12-22 13:52
 */
public class RcsLinkConfigRefreshEvent extends ApplicationEvent {
    public RcsLinkConfigRefreshEvent(Object source) {
        super(source);
    }
}
