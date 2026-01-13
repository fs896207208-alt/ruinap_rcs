package com.ruinap.infra.command.agv.basis.strategy;

import com.slamopto.task.domain.TaskPath;

/**
 * 开始查询策略
 *
 * @author qianye
 * @create 2025-02-24 18:35
 */
public interface StartStrategy {

    /**
     * 获取命令
     *
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return 命令
     */
    String getCommand(TaskPath taskPath, Long mark);
}
