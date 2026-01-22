package com.ruinap.core.task.structure.distribution;


import com.ruinap.infra.enums.task.TaskStateEnum;

/**
 * 任务状态分发工厂
 *
 * @author qianye
 * @create 2025-03-11 09:59
 */
public class TaskDistributionFactory {

    /**
     * 获取任务状态分发工厂
     *
     * @param stateEnum 任务状态
     * @return 处理器
     */
    public static TaskStateHandle getFactory(TaskStateEnum stateEnum) {
        if (stateEnum == null) {
            return null;
        }
        switch (stateEnum) {
            case NEW:
                return new NewTaskStateHandle();
            default:
                return new NotNewTaskStateHandle();
        }
    }
}
