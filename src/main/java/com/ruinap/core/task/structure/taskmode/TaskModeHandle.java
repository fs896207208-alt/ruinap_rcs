package com.ruinap.core.task.structure.taskmode;


import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.auction.BidResult;

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
    BidResult handle(RcsTask task);
}
