package com.ruinap.core.task.structure.taskmode;


import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.util.Map;

/**
 * 电量优先模式
 *
 * @author qianye
 * @create 2025-03-11 11:03
 */
@Component
public class PowerPriorityMode implements TaskModeHandle {
    @Autowired
    private AgvManager agvManager;

    /**
     * 处理RcsTask任务，选择最适合的RcsAgv
     * 该方法首先尝试从空闲的AGV中选择电池电量最大的AGV，如果空闲AGV中没有满足条件的，则从充电的AGV中选择
     *
     * @param task RcsTask任务，用于选择合适的RcsAgv
     * @return 返回选择的RcsAgv，如果没有合适的则返回null
     */
    @Override
    public RcsAgv handle(RcsTask task) {
        // 初始化最大电量为最小整数值，用于比较
        Integer maxPower = Integer.MIN_VALUE;
        // 初始化最近的AGV为null，用于记录选定的AGV
        RcsAgv nearestAgv = null;

        // 获取空闲的AGV集合
        Map<String, RcsAgv> idleRcsAgvMap = agvManager.getIdleRcsAgvMap();
        // 遍历空闲的AGV集合，寻找电池电量最大的在线AGV
        fa:
        for (Map.Entry<String, RcsAgv> entry : idleRcsAgvMap.entrySet()) {
            RcsAgv rcsAgv = entry.getValue();

            //获取设备标签
            String equipmentLabel = task.getEquipmentLabel();
            if (equipmentLabel != null && !"".equalsIgnoreCase(equipmentLabel)) {
                //如果设备标签不为空
                String[] labels = equipmentLabel.split(",");
                for (String label : labels) {
                    if (rcsAgv.getAgvLabel() == null || !rcsAgv.getAgvLabel().contains(label)) {
                        RcsLog.algorithmLog.warn("{} AGV[{}]标签不匹配", task.getTaskCode(), rcsAgv.getAgvId());
                        continue fa;
                    }
                }
            }

            // 比较并更新最大电量和对应的AGV
            if (maxPower.compareTo(rcsAgv.getBattery()) < 0) {
                //判断当前AGV类型是否可接取任务
                if (task.getEquipmentType().equals(0) || rcsAgv.getAgvType().equals(task.getEquipmentType())) {
                    maxPower = rcsAgv.getBattery();
                    nearestAgv = rcsAgv;
                } else {
                    RcsLog.algorithmLog.error("{} 任务分配失败，AGV[{}]类型不匹配", task.getTaskCode(), task.getEquipmentCode());
                }
            }
        }

        // 如果没有找到合适的空闲AGV，尝试从充电的AGV中寻找
        if (nearestAgv == null) {
            // 获取充电的AGV集合
            Map<String, RcsAgv> chargeRcsAgvMap = agvManager.getAllowCancelChargeRcsAgvMap();
            // 遍历充电的AGV集合，寻找电池电量最大的在线AGV
            fa:
            for (Map.Entry<String, RcsAgv> entry : chargeRcsAgvMap.entrySet()) {
                RcsAgv rcsAgv = entry.getValue();

                //获取设备标签
                String equipmentLabel = task.getEquipmentLabel();
                if (equipmentLabel != null && !"".equalsIgnoreCase(equipmentLabel)) {
                    //如果设备标签不为空
                    String[] labels = equipmentLabel.split(",");
                    for (String label : labels) {
                        if (rcsAgv.getAgvLabel() == null || !rcsAgv.getAgvLabel().contains(label)) {
                            RcsLog.algorithmLog.warn("{} AGV[{}]标签不匹配", task.getTaskCode(), rcsAgv.getAgvId());
                            continue fa;
                        }
                    }
                }

                // 比较并更新最大电量和对应的AGV
                if (maxPower.compareTo(rcsAgv.getBattery()) < 0) {
                    //判断当前AGV类型是否可接取任务
                    if (task.getEquipmentType().equals(0) || rcsAgv.getAgvType().equals(task.getEquipmentType())) {
                        maxPower = rcsAgv.getBattery();
                        nearestAgv = rcsAgv;
                    } else {
                        RcsLog.algorithmLog.error("{} 任务分配失败，AGV[{}]类型不匹配", task.getTaskCode(), task.getEquipmentCode());
                    }
                }
            }
        }
        // 返回选定的AGV
        return nearestAgv;
    }
}
