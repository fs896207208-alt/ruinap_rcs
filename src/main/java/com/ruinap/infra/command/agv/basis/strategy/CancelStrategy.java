package com.ruinap.infra.command.agv.basis.strategy;

import com.slamopto.task.domain.TaskPath;

/**
 * 取消任务策略
 *
 * @author qianye
 * @create 2025-04-08 15:17
 */
public interface CancelStrategy {

    /**
     * 获取命令
     *
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return 命令
     */
    String getCommand(TaskPath taskPath, Long mark);
}
