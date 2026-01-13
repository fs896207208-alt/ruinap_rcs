package com.ruinap.infra.command.agv.basis.strategy;

/**
 * 地图检查策略
 *
 * @author qianye
 * @create 2025-02-24 18:35
 */
public interface MapCheckStrategy {

    /**
     * 获取命令
     *
     * @param agvId agv编号
     * @param mark  数据戳
     * @return 命令
     */
    String getCommand(String agvId, Long mark);
}
