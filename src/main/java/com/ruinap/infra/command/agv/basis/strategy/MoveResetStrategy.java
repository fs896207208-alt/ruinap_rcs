package com.ruinap.infra.command.agv.basis.strategy;

/**
 * AGV仿真重置策略
 *
 * @author qianye
 * @create 2025-04-08 15:17
 */
public interface MoveResetStrategy {

    /**
     * 获取命令
     *
     * @param agvId   AGV编号
     * @param pointId 点位编号
     * @param yaw     AGV角度
     * @param battery AGV电量
     * @param load    AGV负载
     * @param mark    数据戳
     * @return 命令
     */
    String getCommand(String agvId, Integer pointId, Integer yaw, Integer battery, Integer load, Long mark);
}
