package com.ruinap.core.task.design;

import lombok.Getter;

/**
 * 任务协调器
 *
 * @author qianye
 * @create 2025-04-17 10:33
 */
public class TaskOrchestrator {

    /**
     * 任务处理链
     */
    @Getter
    private static final TaskFlowHandler TASK_FLOW_HANDLER_CHAIN;

    /* 初始化任务处理链 */
    static {
        //第一步进行充电检查
        ChargeHandler chargingHandler = new ChargeHandler();
        //第二步进行任务分配
        TaskDistributionHandler taskHandler = new TaskDistributionHandler();
        //第三步进行待命检查
        StandbyHandler standbyHandler = new StandbyHandler();

        //分别设置下一个处理链
        chargingHandler.setNextHandler(taskHandler);
        taskHandler.setNextHandler(standbyHandler);

        //开始调用
        TASK_FLOW_HANDLER_CHAIN = chargingHandler;
    }
}
