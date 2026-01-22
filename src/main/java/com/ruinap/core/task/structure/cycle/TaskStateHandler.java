package com.ruinap.core.task.structure.cycle;


import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.task.TaskStateEnum;

/**
 * 任务状态处理接口
 *
 * @author qianye
 * @create 2025-03-10 14:45
 */
public interface TaskStateHandler {
    /**
     * 抽象处理方法
     *
     * @param task     任务
     * @param oldState 旧状态
     * @param newState 新状态
     */
    void handle(RcsTask task, TaskStateEnum oldState, TaskStateEnum newState);
}
