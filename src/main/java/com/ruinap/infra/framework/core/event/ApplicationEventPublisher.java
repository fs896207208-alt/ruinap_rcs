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
     * 发布一个事件给所有注册的监听器 (默认异步执行 - 发后即忘)
     *
     * @param event 事件对象
     */
    default void publishEvent(ApplicationEvent event) {
        // 默认 false，走全局异步
        publishEvent(event, false);
    }

    /**
     * 发布一个事件，并允许调用者强行指定是否同步执行
     *
     * @param event  事件对象
     * @param isSync true: 阻塞当前线程直到监听器执行完毕; false: 开启虚拟线程异步执行
     */
    void publishEvent(ApplicationEvent event, boolean isSync);
}
