package com.ruinap.infra.command.agv.basis.strategy;

/**
 * 获取任务策略
 *
 * @author qianye
 * @create 2025-02-24 18:35
 */
public interface TaskStrategy {

    /**
     * 获取命令
     *
     * @param agvId AGV编号
     * @param mark  数据戳
     * @return 命令
     */
    String getCommand(String agvId, Long mark);
}
