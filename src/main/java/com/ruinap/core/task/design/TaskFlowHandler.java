package com.ruinap.core.task.design;

/**
 * 任务流程处理
 *
 * @author qianye
 * @create 2025-04-17 09:58
 */
public interface TaskFlowHandler {
    /**
     * 设置下一个处理者
     *
     * @param nextHandler 下一个处理者
     */
    void setNextHandler(TaskFlowHandler nextHandler);

    /**
     * 处理请求
     */
    void handleRequest();
}
