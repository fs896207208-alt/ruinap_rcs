package com.ruinap.core.task.event;

import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.task.TaskStateEnum;
import com.ruinap.infra.framework.core.event.ApplicationEvent;
import lombok.Getter;

/**
 * 任务状态变更事件
 *
 * @author qianye
 * @create 2026-02-05 10:04
 */
@Getter
public class TaskStateChangeEvent extends ApplicationEvent {

    /**
     * 任务
     */
    private final RcsTask task;
    /**
     * 旧状态
     */
    private final TaskStateEnum oldState;
    /**
     * 新状态
     */
    private final TaskStateEnum newState;

    /**
     * 构造方法
     *
     * @param source   事件源
     * @param task     任务
     * @param oldState 旧状态
     * @param newState 新状态
     */
    public TaskStateChangeEvent(Object source, RcsTask task, TaskStateEnum oldState, TaskStateEnum newState) {
        super(source);
        this.task = task;
        this.oldState = oldState;
        this.newState = newState;
    }
}
