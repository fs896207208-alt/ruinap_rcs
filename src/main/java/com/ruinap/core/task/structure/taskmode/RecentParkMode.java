package com.ruinap.core.task.structure.taskmode;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.util.List;
import java.util.Map;

/**
 * 最近停靠优先
 *
 * @author qianye
 * @create 2025-03-11 11:05
 */
@Component
public class RecentParkMode implements TaskModeHandle {
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AlarmManager alarmManage;

    /**
     * 待机点超时缓存，默认120分钟过期
     */
    private static final TimedCache<Integer, RcsPoint> STANDBY_POINT_TIMED_CACHE = CacheUtil.newTimedCache(1000 * 60 * 120);

    @Override
    public RcsAgv handle(RcsTask rcsTask) {
        RcsAgv returnAgv = null;
        //获取空闲AGV列表
        Map<String, RcsAgv> rcsAgvMap = agvManager.getIdleRcsAgvMap();

        // 获取任务起点
        RcsPoint originPoint = mapManager.getPointByAlias(rcsTask.getOrigin());
        if (originPoint == null) {
            RcsLog.algorithmLog.error("{} 任务起点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getOrigin());
            // 添加告警
            alarmManage.triggerAlarm("0", rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getOrigin(), "rcs");
            return null;
        }

        //临时距离
        Integer tempDistance = Integer.MAX_VALUE;
        //临时点位
        RcsPoint tempPoint = null;
        //尝试从缓存获取最近待机点
        RcsPoint tempPointCache = STANDBY_POINT_TIMED_CACHE.get(originPoint.getId());
        if (tempPointCache != null) {
            //如果缓存中有则直接返回
            tempPoint = tempPointCache;
        } else {
            //获取所有待机点
            List<RcsPoint> standbyList = mapManager.getSnapshot().standbyPoints().get(originPoint.getMapId());
            //遍历待机点
            for (RcsPoint rcsPoint : standbyList) {
                //寻找最近待机点

                //获取待机点的距离
                Integer standbyPointDistance = GeometryUtils.calculateDistance(rcsPoint, originPoint);
                //比较距离
                if (standbyPointDistance.compareTo(tempDistance) < 0) {
                    tempDistance = standbyPointDistance;
                    tempPoint = rcsPoint;
                }
            }
            if (tempPoint != null) {
                //将最近待机点缓存
                STANDBY_POINT_TIMED_CACHE.put(originPoint.getId(), tempPoint);
            }
        }

        //遍历AGV列表
        fa:
        for (Map.Entry<String, RcsAgv> entry : rcsAgvMap.entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();

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

            // AGV所在点位
            RcsPoint agvPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
            if (rcsAgv.getPointId() == null || rcsAgv.getPointId() < 0) {
                RcsLog.algorithmLog.error("{} 任务分配失败，AGV[{}]不在点位上", rcsTask.getTaskCode(), rcsTask.getEquipmentCode());
                continue;
            }


            //判断当前AGV类型是否可接取任务
            if (rcsTask.getEquipmentType().equals(0) || rcsAgv.getAgvType().equals(rcsTask.getEquipmentType())) {
                //判断最近待机点id和AGV的当前点id相等
                if (tempPoint != null && tempPoint.equals(agvPoint)) {
                    returnAgv = rcsAgv;
                }
            } else {
                RcsLog.algorithmLog.error("{} 任务分配失败，AGV类型[{}]类型不匹配", rcsTask.getTaskCode(), rcsAgv.getAgvType());
            }
        }
        return returnAgv;
    }
}
