package com.ruinap.core.task.design;


import com.ruinap.core.task.TaskManager;
import com.ruinap.infra.framework.annotation.Component;

/**
 * 任务分发处理器
 *
 * @author qianye
 * @create 2025-04-17 10:19
 */
@Component
public class TaskDistributionHandler implements TaskFlowHandler {

    private TaskManager taskManager;
    /**
     * 下一个处理者
     */
    private TaskFlowHandler nextHandler;

    /**
     * 设置下一个处理者
     *
     * @param nextHandler 下一个处理者
     */
    @Override
    public void setNextHandler(TaskFlowHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    /**
     * 处理请求
     *
     * @return 处理结果
     */
    @Override
    public void handleRequest() {
        //执行任务分发
        taskManager.taskDistribution();

        // 委托给下一个处理器执行
        if (nextHandler != null) {
            nextHandler.handleRequest();
        }
    }
}
