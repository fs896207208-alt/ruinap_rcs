package com.ruinap.core.task.structure.taskmode;


import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.auction.BidResult;
import com.ruinap.infra.log.RcsLog;

/**
 * 自定义任务分配模式
 *
 * @author qianye
 * @create 2025-03-11 11:13
 */
public class CustomizeMode implements TaskModeHandle {
    /**
     * 自定义任务分配模式
     *
     * @param task 任务
     * @return RcsAgv
     */
    @Override
    public BidResult handle(RcsTask task) {
        RcsLog.algorithmLog.error("{} 未实现的自定义任务分配模式", task.getEquipmentCode());
        return null;
    }
}
