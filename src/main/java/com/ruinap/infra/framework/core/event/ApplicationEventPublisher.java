package com.ruinap.infra.framework.core.event;

/**
 * 【核心接口】事件发布器
 * <p>
 * ApplicationContext 会继承此接口。
 * 用户可以通过 ctx.publishEvent(...) 来发送消息。
 *
 * @author qianye
 * @create 2025-12-12 09:50
 */
public interface ApplicationEventPublisher {
    /**
     * 发布一个事件给所有注册的监听器
     *
     * @param event 事件对象
     */
    void publishEvent(ApplicationEvent event);
}
