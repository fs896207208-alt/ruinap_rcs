package com.ruinap.infra.framework.core.event;

import java.util.EventObject;

/**
 * 【核心类】应用事件基类
 * <p>
 * 所有自定义事件（如 TaskFinishedEvent）都必须继承此类。
 * source: 事件源（谁触发的事件）
 * timestamp: 事件发生时间
 *
 * @author qianye
 * @create 2025-12-12 09:49
 */
public abstract class ApplicationEvent extends EventObject {
    private final long timestamp;

    public ApplicationEvent(Object source) {
        super(source);
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
