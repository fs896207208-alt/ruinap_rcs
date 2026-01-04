package com.ruinap.infra.framework.core.event;

import java.util.EventListener;

/**
 * 【核心接口】应用事件监听器
 * <p>
 * 泛型 E: 监听的事件类型
 *
 * @author qianye
 * @create 2025-12-12 09:51
 */
@FunctionalInterface
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {
    /**
     * 处理应用事件
     *
     * @param event 事件
     */
    void onApplicationEvent(E event);
}
