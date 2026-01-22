package com.ruinap.core.task.structure.taskmode;


import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 距离优先模式
 *
 * @author qianye
 * @create 2025-03-11 10:27
 */
@Component
public class DistancePriorityMode implements TaskModeHandle {

    @Autowired
    private AgvManager agvManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private AlarmManager alarmManage;
    @Autowired
    private TaskPathManager taskPathManager;

    /**
     * 处理任务
     *
     * @param task 任务
     * @return agv
     */
    @Override
    public RcsAgv handle(RcsTask task) {
        //获取距离优先的AGV
        return distanceFirst(task);
    }

    /**
     * 距离优先
     *
     * @param rcsTask 任务
     * @return 最近的AGV
     */
    private RcsAgv distanceFirst(RcsTask rcsTask) {
        //先获取空闲AGV集合
        ConcurrentHashMap<String, RcsAgv> rcsAgvMap = new ConcurrentHashMap<>(agvManager.getIdleRcsAgvMap());

        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, rcsTask.getTaskType())) {
            Map<String, RcsAgv> taskRcsAgvMap = agvManager.getHasTaskRcsAgvMap();
            if (!taskRcsAgvMap.isEmpty()) {
                for (Map.Entry<String, RcsAgv> entry : taskRcsAgvMap.entrySet()) {
                    RcsAgv value = entry.getValue();
                    TaskPath firstTaskPath = taskPathManager.getFirst(entry.getKey());
                    if (firstTaskPath != null) {
                        RcsTask rcsTask1 = taskManager.taskCache.get(firstTaskPath.getTaskCode());
                        if (rcsTask1 != null && TaskTypeEnum.isEnumByCode(TaskTypeEnum.DOCK, rcsTask1.getTaskType())) {
                            rcsAgvMap.putIfAbsent(entry.getKey(), value);
                        }
                    }
                }
            }
        }

        // 首先尝试从空闲AGV中选择
        RcsAgv selectedAgv = findNearestAgv(rcsTask, rcsAgvMap);
        if (selectedAgv == null) {
            //如果没有再从充电中的AGV中选择
            selectedAgv = findNearestAgv(rcsTask, agvManager.getAllowCancelChargeRcsAgvMap());
        }
        return selectedAgv;
    }

    /**
     * 从指定的AGV集合中找到距离任务起点最近的AGV，如果没有找到，则返回null
     */
    public RcsAgv findNearestAgv(RcsTask rcsTask, Map<String, RcsAgv> agvMap) {
        Integer minDistance = Integer.MAX_VALUE;
        RcsAgv nearestAgv = null;

        if (agvMap.isEmpty()) {
            return null;
        }

        // 获取任务起点
        RcsPoint taskPoint = mapManager.getPointByAlias(rcsTask.getOrigin());
        if (taskPoint == null) {
            RcsLog.consoleLog.error("{} 任务起点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getOrigin());
            RcsLog.algorithmLog.error("{} 任务起点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getOrigin());
            // 添加告警
            alarmManage.triggerAlarm("0", rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getOrigin(), "rcs");
            return null;
        }

        // 遍历AGV集合
        fa:
        for (Map.Entry<String, RcsAgv> entry : agvMap.entrySet()) {
            RcsAgv agv = entry.getValue();

            //判断AGV是否空闲
            RcsAgv rcsAgvIdle = agvManager.getRcsAgvIdle(agv);
            if (rcsAgvIdle == null) {
                RcsLog.algorithmLog.warn("{} AGV[{}]非空闲", agv.getAgvId(), agv.getAgvId());
                continue;
            }

            //获取设备标签
            String equipmentLabel = rcsTask.getEquipmentLabel();
            if (equipmentLabel != null && !"".equalsIgnoreCase(equipmentLabel)) {
                //如果设备标签不为空
                String[] labels = equipmentLabel.split(",");
                for (String label : labels) {
                    if (agv.getAgvLabel() == null || !agv.getAgvLabel().contains(label)) {
                        RcsLog.algorithmLog.warn("{} AGV[{}]标签不匹配", rcsTask.getTaskCode(), agv.getAgvId());
                        continue fa;
                    }
                }
            }

            Integer pointId = agv.getPointId();
            if (pointId == null) {
                RcsLog.algorithmLog.error("{} 任务分配失败，AGV[{}]不在点位上", rcsTask.getTaskCode(), agv.getAgvId());
                continue;
            }

            //判断当前AGV类型是否可接取任务
            if (rcsTask.getEquipmentType().equals(0) || agv.getAgvType().equals(rcsTask.getEquipmentType())) {
                // AGV所在点位
                RcsPoint agvPoint = mapManager.getRcsPoint(agv.getMapId(), pointId);
                if (agvPoint == null) {
                    continue;
                }

                // 计算距离
                int distance = GeometryUtils.calculateDistance(agvPoint, taskPoint);
                if (minDistance.compareTo(distance) > 0) {
                    minDistance = distance;
                    nearestAgv = agv;
                }
            } else {
                RcsLog.algorithmLog.error("{} 任务分配失败，AGV[{}]类型不匹配", rcsTask.getTaskCode(), rcsTask.getEquipmentCode());
            }
        }

        return nearestAgv;
    }
}
