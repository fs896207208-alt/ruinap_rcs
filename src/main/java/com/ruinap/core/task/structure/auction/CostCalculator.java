package com.ruinap.core.task.structure.auction;

import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;

/**
 * 动态代价计算器接口
 *
 * @author qianye
 * @create 2026-02-26 17:28
 */
public interface CostCalculator {
    /**
     * 计算特定维度的代价
     *
     * @param agv   竞标的 AGV
     * @param task  拍卖的任务
     * @param route A* 算出的物理路线 (供拥堵或距离计算使用)
     * @return 代价值
     */
    double calculate(RcsAgv agv, RcsTask task, RouteResult route);

    /**
     * 获取当前该策略的动态权重 (可从 CoreYaml 或 数据库 热读取)
     */
    double getDynamicWeight();
}