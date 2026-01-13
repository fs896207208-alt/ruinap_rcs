package com.ruinap.infra.command.agv.basis.strategy;

/**
 * AGV重定位策略
 *
 * @author qianye
 * @create 2025-04-08 15:17
 */
public interface ReLocationStrategy {

    /**
     * 获取命令
     *
     * @param agvId   AGV编号
     * @param mapId   地图号
     * @param pointId 点位编号
     * @param angle   朝向角度
     * @param mark    数据戳
     * @return 命令
     */
    String getCommand(String agvId, Integer mapId, Integer pointId, Integer angle, Long mark);
}
