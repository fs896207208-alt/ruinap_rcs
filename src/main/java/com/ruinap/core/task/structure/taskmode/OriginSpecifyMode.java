package com.ruinap.core.task.structure.taskmode;


import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.config.pojo.task.TaskCommonEntity;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.util.HashMap;
import java.util.Map;

/**
 * 起点指定模式
 *
 * @author qianye
 * @create 2025-03-11 11:09
 */
@Component
public class OriginSpecifyMode implements TaskModeHandle {
    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private AlarmManager alarmManage;
    @Autowired
    private DistancePriorityMode distancePriorityMode;

    /**
     * 处理AGV任务分配
     * 根据任务信息和AGV状态，分配最合适的AGV执行任务
     *
     * @param rcsTask 任务对象，包含任务详细信息
     * @return 返回分配的AGV对象，如果没有合适的AGV则返回null
     */
    @Override
    public RcsAgv handle(RcsTask rcsTask) {
        // 初始化用于存储可分配的AGV和需要充电的AGV的Map
        Map<String, RcsAgv> returnAgvMap = new HashMap<>();
        Map<String, RcsAgv> chargeAgvMap = new HashMap<>();

        // 获取任务起点
        RcsPoint originPoint = mapManager.getPointByAlias(rcsTask.getOrigin());
        if (originPoint == null) {
            // 如果获取起点失败，记录错误日志并触发告警
            RcsLog.algorithmLog.error("{} 任务起点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getOrigin());
            // 添加告警
            alarmManage.triggerAlarm("0", rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getOrigin(), "rcs");
            return null;
        }

        //获取任务配置
        TaskCommonEntity taskCommon = taskYaml.getTaskCommon();
        if (taskCommon == null) {
            // 如果获取任务配置失败，记录错误日志并返回null
            RcsLog.algorithmLog.error("{} 配置文件获取不到任务配置", rcsTask.getTaskCode());
            return null;
        }
        //获取任务起点指定AGV
        Map<String, String> taskOriginSpecify = taskCommon.getTaskOriginSpecify();
        if (taskOriginSpecify == null) {
            // 如果获取任务起点指定的AGV配置失败，记录错误日志并返回null
            RcsLog.algorithmLog.error("{} 配置文件获取不到任务起点指定AGV配置", rcsTask.getTaskCode());
            return null;
        }

        //根据任务起点获取相关AGV的字符串表示
        String agvStr = taskOriginSpecify.get(originPoint.getMapId() + "-" + originPoint.getId());
        String[] agvs = agvStr.split(",");
        fa:
        for (String agv : agvs) {
            // 根据AGV代码获取AGV对象
            RcsAgv rcsAgv = agvManager.getRcsAgvByCode(agv);
            if (rcsAgv == null) {
                // 如果AGV对象获取失败，记录错误日志并继续处理下一个AGV
                RcsLog.algorithmLog.error("{} 配置的AGV[{}]获取不到AGV对象", agv, agv);
                continue;
            }

            //获取设备标签
            String equipmentLabel = rcsTask.getEquipmentLabel();
            if (equipmentLabel != null && !"".equalsIgnoreCase(equipmentLabel)) {
                //如果设备标签不为空
                String[] labels = equipmentLabel.split(",");
                for (String label : labels) {
                    if (rcsAgv.getAgvLabel() == null || !rcsAgv.getAgvLabel().contains(label)) {
                        RcsLog.algorithmLog.warn("{} AGV[{}]标签不匹配", rcsTask.getTaskCode(), rcsAgv.getAgvId());
                        continue fa;
                    }
                }
            }

            // 检查AGV是否空闲
            if (agvManager.getRcsAgvIdle(rcsAgv.getAgvId()) != null) {
                // 如果AGV空闲，将其添加到返回列表中
                returnAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
            } else if (agvManager.getRcsAgvIsCharge(rcsAgv)) {
                if (agvManager.getAllowCancelChargeRcsAgv(rcsAgv) == null) {
                    continue;
                }
                // 如果AGV正在充电，将其添加到充电列表中
                chargeAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
                RcsLog.algorithmLog.error("{} AGV[{}]被添加到充电集合", rcsAgv.getAgvId(), rcsAgv.getAgvId());
            }
        }

        // 初始化最近AGV对象
        RcsAgv nearestAgv = null;
        // 根据空闲AGV和充电AGV的情况，选择最近的AGV
        if (returnAgvMap.isEmpty()) {
            if (!chargeAgvMap.isEmpty()) {
                // 如果没有空闲AGV，从充电AGV中选择最近的AGV
                nearestAgv = distancePriorityMode.findNearestAgv(rcsTask, chargeAgvMap);
            }
        } else {
            // 如果有空闲AGV，从空闲AGV中选择最近的AGV
            nearestAgv = distancePriorityMode.findNearestAgv(rcsTask, returnAgvMap);
        }

        // 返回最近的AGV对象
        return nearestAgv;
    }
}
