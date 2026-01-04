package com.ruinap.infra.framework.event;

import com.ruinap.infra.framework.core.event.ApplicationEvent;

/**
 * @author qianye
 * @create 2025-12-12 10:29
 */
public class AgvTaskEvent extends ApplicationEvent {
    private final String taskCode;
    private final String agvCode;
    private final TaskStatus status;
    private final String message;

    public AgvTaskEvent(Object source, String taskCode, String agvCode, TaskStatus status, String message) {
        super(source);
        this.taskCode = taskCode;
        this.agvCode = agvCode;
        this.status = status;
        this.message = message;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public String getAgvCode() {
        return agvCode;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public enum TaskStatus {
        CREATED, EXECUTING, BLOCKED, COMPLETED
    }
}
