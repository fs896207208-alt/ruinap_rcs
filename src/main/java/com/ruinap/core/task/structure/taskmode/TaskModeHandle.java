package com.ruinap.core.task.structure.taskmode;


import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;

/**
 * 任务模式处理接口
 *
 * @author qianye
 * @create 2025-03-11 10:25
 */
public interface TaskModeHandle {
    /**
     * 抽象处理方法
     *
     * @param task 任务
     * @return RcsAgv
     */
    RcsAgv handle(RcsTask task);
}
