package com.ruinap.core.task.domain;

import com.ruinap.core.map.pojo.RcsPoint;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 对接设备类
 * <p>
 * 为什么不使用InteractionsBody类，而是使用 DockDevice 呢，因为可以减少获取 RcsPoint 的运算
 *
 * @author qianye
 * @create 2025-06-03 17:04
 */
@Getter
@Setter
public class DockDevice {
    /**
     * 对接设备编号
     */
    private String equipmentId;
    /**
     * 对接类型 2电梯对接任务 3风淋室对接任务 4输送线对接任务
     */
    private int dockType;
    /**
     * 对接提前点
     */
    private int dockingAdvance;
    /**
     * 对接状态
     */
    private volatile int dockState;
    /**
     * 自动门、电梯、风淋室、输送线开始对接点位
     */
    private RcsPoint startPoint;
    /**
     * 设备点位
     */
    private RcsPoint equipmentPoint;
    /**
     * 电梯、风淋室结束对接点位、输送线关联点位
     */
    private RcsPoint endPoint;
    /**
     * 前门集合
     * <p>
     * 前门一般用于对接自动门、电梯、风淋室
     */
    private Map<String, RcsPoint> frontDoors;
    /**
     * 后门集合
     * <p>
     * 后门一般用于对接自动门、电梯、风淋室
     */
    private Map<String, RcsPoint> backDoors;
}
