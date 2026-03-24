package com.ruinap.core.task.structure.auction.impl;

import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.auction.CostCalculator;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;

/**
 * 距离代价计算器
 *
 * @author qianye
 * @create 2026-02-26 17:41
 */
@Component
public class DistanceCostCalculator implements CostCalculator {
    @Autowired
    private CoreYaml coreYaml;

    @Override
    public double calculate(RcsAgv agv, RcsTask task, RouteResult route) {
        // 纯物理距离作为基础代价
        return route != null ? route.getPathCost() : Double.MAX_VALUE;
    }

    @Override
    public double getDynamicWeight() {
        // 从配置热更新读取距离权重，默认为 1.0
        return coreYaml.getAlgorithmCommon().getOrDefault("auction_weight_distance", 1);
    }
}
