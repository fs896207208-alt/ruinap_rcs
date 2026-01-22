package com.ruinap.core.task.design.filter;

import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.config.pojo.task.StandbyCommonEntity;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.util.*;

/**
 * 待机点过滤器
 *
 * @author qianye
 * @create 2025-04-18 16:57
 */
@Component
public class StandbyFilter {

    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AlarmManager alarmManager;

    /**
     * 判断当前点位是否在待机屏蔽点
     *
     * @param currentPoint 点位
     * @param agvId        AGV编号
     * @return true:在屏蔽点 false:不在屏蔽点
     */
    public boolean isStandbyPointShield(RcsPoint currentPoint, String agvId) {
        // 获取待机配置
        StandbyCommonEntity standbyCommon = taskYaml.getStandbyCommon();
        // 获取待机屏蔽点映射（地图编号-点位 → AGV列表）
        Map<String, String> standbyPointShield = standbyCommon.getStandbyPointShield();
        if (standbyPointShield != null) {
            for (Map.Entry<String, String> entry : standbyPointShield.entrySet()) {
                //{地图编号-点位}
                String standbyPoint = entry.getKey();
                //多个AGV_ID
                String shieldedAgvs = entry.getValue();

                // 解析待机屏蔽点
                RcsPoint shieldPoint = mapManager.getPointByAlias(standbyPoint);
                if (shieldPoint == null) {
                    RcsLog.consoleLog.error("待机屏蔽点位[{}]不存在", standbyPoint);
                    RcsLog.algorithmLog.error("待机屏蔽点位[{}]不存在", standbyPoint);
                    alarmManager.triggerAlarm(standbyPoint, AlarmCodeEnum.E12003, "rcs");
                    continue;
                }

                // 获取同楼层的待机屏蔽点对应的点位集合
                List<RcsPoint> validShieldPoints = mapManager.getSnapshot().standbyShieldPoints().get(shieldPoint.getMapId());
                //判断是否地图配置的待机屏蔽点
                if (!validShieldPoints.contains(shieldPoint)) {
                    RcsLog.consoleLog.info("{} 点位[{}]不是地图设置的待机屏蔽点", agvId, shieldPoint);
                    RcsLog.algorithmLog.info("{} 点位[{}]不是地图设置的待机屏蔽点", agvId, shieldPoint);
                    continue;
                }

                // 检查点位是否匹配
                if (!shieldPoint.equals(currentPoint)) {
                    continue;
                }

                // 分割并处理 AGV 列表（兼容空格）
                Set<String> shieldedAgvSet = new HashSet<>(Arrays.asList(shieldedAgvs.split(",")));
                if (shieldedAgvSet.contains(agvId)) {
                    // 记录日志并立即返回
                    RcsLog.algorithmLog.error("{} AGV在待机屏蔽点[{}]，不进行待机操作", agvId, standbyPoint);
                    RcsLog.consoleLog.error("{} AGV在待机屏蔽点[{}]，不进行待机操作", agvId, standbyPoint);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断当前点位是否在待机列表中
     *
     * @param currentPoint 点位
     * @return true:在待机点 false:不在待机点
     */
    public boolean inStandbyList(RcsPoint currentPoint) {
        //获取指定地图编号待机点集合
        List<RcsPoint> standbys = mapManager.getSnapshot().standbyPoints().get(currentPoint.getMapId());
        return standbys.contains(currentPoint);
    }

    /**
     * 根据距离过滤待机点
     *
     * @param rcsAgv AGV
     * @return 距离最近的待机点
     */
    public RcsPoint filterStandbyByDistance(RcsAgv rcsAgv) {
        List<RcsPoint> standbys = new ArrayList<>();
        Map<Integer, List<RcsPoint>> standbyMap = mapManager.getSnapshot().standbyPoints();
        standbyMap.forEach((mapId, points) -> standbys.addAll(points));
        //调用距离优先匹配方法
        return distanceFirst(rcsAgv, standbys);
    }

    /**
     * 距离优先匹配
     *
     * @param rcsAgv AGV
     * @return 最近的待机点
     */
    private RcsPoint distanceFirst(RcsAgv rcsAgv, List<RcsPoint> standbys) {
        RcsPoint returnPoint = null;
        Integer tempCost = Integer.MAX_VALUE;
        //获取AGV的点位
        RcsPoint rcsPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
        if (rcsPoint == null) {
            RcsLog.algorithmLog.error("{} AGV坐标不存在任何点位上", rcsAgv.getAgvId());
            return returnPoint;
        }
        for (RcsPoint standbyPoint : standbys) {
            //获取点位占用
            RcsPointOccupy rcsOccupy = mapManager.getRcsOccupy(standbyPoint);
            if (rcsOccupy == null) {
                continue;
            }

            // 检查点位是否被占用
            if (rcsOccupy.isPhysicalBlocked()) {
                //检查点位是否被当前AGV占用
                if (!rcsOccupy.getDeviceOccupyState(rcsAgv.getAgvId())) {
                    // 如果被占用且不是当前 AGV，则跳过
                    RcsLog.algorithmLog.info("{} 待机点[{}]已经被占用，跳过该待机点", rcsAgv.getAgvId(), standbyPoint);
                    continue;
                }
            }

            // 计算距离
            int distance = GeometryUtils.calculateDistance(rcsPoint, standbyPoint);
            if (tempCost.compareTo(distance) > 0) {
                tempCost = distance;
                returnPoint = standbyPoint;
            }
        }

        return returnPoint;
    }

    /**
     * 获取绑定的待机点
     *
     * @param rcsAgv AGV
     * @return 最近距离待机点
     */
    public RcsPoint filterStandbyByBinding(RcsAgv rcsAgv) {
        List<RcsPoint> pointList = new ArrayList<>();
        //获取待机点绑定集合
        Map<String, String> standbyPointBinding = taskYaml.getStandbyCommon().getStandbyPointBinding();
        for (Map.Entry<String, String> entry : standbyPointBinding.entrySet()) {
            //{地图编号-点位}
            String key = entry.getKey();
            //多个AGV_ID（String）
            String value = entry.getValue();

            // 获取绑定的AGV字符串
            List<String> agvs = Arrays.asList(value.split(","));
            // 判断AGV是否存在
            if (agvs.contains(rcsAgv.getAgvId())) {
                //获取待机点
                RcsPoint rcsPoint = mapManager.getPointByAlias(key);
                if (rcsPoint != null) {
                    pointList.add(rcsPoint);
                }
            }
        }

        if (pointList.isEmpty()) {
            return null;
        } else {
            //调用距离优先匹配方法
            return distanceFirst(rcsAgv, pointList);
        }
    }
}
