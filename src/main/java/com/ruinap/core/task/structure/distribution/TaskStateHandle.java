package com.ruinap.core.task.structure.distribution;


import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;

/**
 * 任务状态处理接口
 *
 * @author qianye
 * @create 2025-03-11 10:14
 */
public interface TaskStateHandle {
    /**
     * 处理任务状态
     *
     * @param task 任务
     * @return 返回AGV
     */
    RcsAgv handle(RcsTask task);
}
