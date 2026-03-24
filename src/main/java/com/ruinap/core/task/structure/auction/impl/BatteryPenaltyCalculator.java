package com.ruinap.core.task.structure.auction.impl;

import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.auction.CostCalculator;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;

/**
 * 电量代价计算器
 *
 * @author qianye
 * @create 2026-02-26 17:42
 */
@Component
public class BatteryPenaltyCalculator implements CostCalculator {
    @Autowired
    private CoreYaml coreYaml;
    @Autowired
    private TaskYaml taskYaml;

    @Override
    public double calculate(RcsAgv agv, RcsTask task, RouteResult route) {
        // AGV允许充电阈值
        Integer allowChargePower = taskYaml.getChargeCommon().getAllowChargePower();
        // AGV最低工作电量
        Integer lowestWorkPower = taskYaml.getChargeCommon().getLowestWorkPower();
        int battery = agv.getBattery();
        // 电量越低，强行增加的惩罚代价越高，逼迫它流标去充电
        if (battery < lowestWorkPower) {
            // 绝对惩罚
            return 50000.0;
        }
        if (battery < allowChargePower) {
            // 软性惩罚
            return 10000.0;
        }
        return 0.0;
    }

    @Override
    public double getDynamicWeight() {
        return coreYaml.getAlgorithmCommon().getOrDefault("auction_weight_battery", 1);
    }
}