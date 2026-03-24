package com.ruinap.core.algorithm.domain;

import com.ruinap.core.task.domain.TaskPath;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务上下文
 *
 * @author qianye
 * @create 2025-03-24 11:04
 */
public class PathContext {
    @Setter
    private PathState state;
    @Getter
    private TaskPath taskPath;

    public PathContext(TaskPath taskPath) {
        this.taskPath = taskPath;
        // 初始状态可以根据实际情况设置
        this.state = null;
    }

    /**
     * 请求状态处理
     */
    public void request() {
        state.handle(this);
    }
}
