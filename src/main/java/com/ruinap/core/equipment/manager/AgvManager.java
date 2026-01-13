package com.ruinap.core.equipment.manager;

import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.config.pojo.task.ChargeCommonEntity;
import com.ruinap.infra.enums.agv.*;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AGV 设备管理
 *
 * @author qianye
 * @create 2026-01-12 13:54
 */
@Service
public class AgvManager {

    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private MapManager mapManager;

    /**
     * 全局AGV缓存
     */
    private final Map<String, RcsAgv> agvCache = new ConcurrentHashMap<>();


    // ********************** 基础方法 ***********************

    /**
     * 获取全部AGV集合
     *
     * @return 全部AGV集合
     */
    public Map<String, RcsAgv> getRcsAgvMap() {
        return agvCache;
    }

    /**
     * 根据AGV编号获取对应的RcsAgv对象
     *
     * @param agvCode AGV编号
     * @return Agv对象，如果找不到则返回null
     */
    public RcsAgv getRcsAgvByCode(String agvCode) {
        if (agvCode == null) {
            return null;
        }
        return getRcsAgvMap().get(agvCode);
    }

    /**
     * 获取指定AGV是否在线
     *
     * @param rcsAgv AGV对象
     * @return 在线状态，true：在线，false：离线
     */
    public static boolean getRcsAgvOnline(RcsAgv rcsAgv) {
        return !AgvStateEnum.isEnumByCode(AgvStateEnum.OFFLINE, rcsAgv.getAgvState());
    }

    /**
     * 获取指定AGV的隔离状态
     *
     * @param rcsAgv AGV对象
     * @return 隔离状态 0未隔离 1在线隔离 2离线隔离
     */
    public static Integer getRcsAgvIsIsolation(RcsAgv rcsAgv) {
        return rcsAgv.getIsolationState();
    }

    /**
     * 获取指定AGV是否已校验地图
     *
     * @param rcsAgv AGV对象
     * @return 校验状态，true：已校验，false：未校验
     */
    public static boolean getRcsAgvMapCheck(RcsAgv rcsAgv) {
        return rcsAgv.isMapChecked();
    }

    /**
     * 获取指定AGV的控制权
     *
     * @param rcsAgv AGV对象
     * @return 控制权 0调度 1其他
     */
    public static Integer getAgvControl(RcsAgv rcsAgv) {
        return rcsAgv.getAgvControl();
    }

    /**
     * 获取指定空闲AGV
     *
     * @param agvCode AGV编号
     * @return 空闲AGV，如果找不到则返回null
     */
    public RcsAgv getRcsAgvIdle(String agvCode) {
        RcsAgv rcsAgv = getRcsAgvByCode(agvCode);
        if (rcsAgv == null) {
            return null;
        }
        return getRcsAgvIdle(rcsAgv);
    }

    /**
     * 获取指定空闲AGV
     *
     * @param rcsAgv AGV对象
     * @return 空闲AGV，如果找不到则返回null
     */
    public RcsAgv getRcsAgvIdle(RcsAgv rcsAgv) {
        String agvId = rcsAgv.getAgvId();
        //判断AGV是否在线
        boolean rcsAgvOnline = getRcsAgvOnline(rcsAgv);
        if (!rcsAgvOnline) {
            RcsLog.algorithmLog.warn("{} AGV状态[{}]，当前无法获取空闲AGV信息", agvId, rcsAgv.getAgvState());
            return null;
        }

        //判断AGV是否已校验地图
        boolean mapCheck = getRcsAgvMapCheck(rcsAgv);
        if (!mapCheck) {
            RcsLog.algorithmLog.warn("{} AGV地图校验状态[{}]，当前无法获取空闲AGV信息", agvId, mapCheck);
            return null;
        }

        //判断AGV是否是隔离
        Integer isolation = getRcsAgvIsIsolation(rcsAgv);
        if (isolation == null || !AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, isolation)) {
            RcsLog.algorithmLog.warn("{} AGV隔离状态[{}]，当前无法获取空闲AGV信息", agvId, rcsAgv.getIsolationState());
            return null;
        }

        //判断AGV是否调度模式
        if (!AgvControlModeEnum.isEnumByCode(AgvControlModeEnum.RCS, getAgvControl(rcsAgv))) {
            RcsLog.algorithmLog.warn("{} AGV的控制模式[{}]不属于调度，当前无法获取空闲AGV信息", agvId, rcsAgv.getAgvControlMode());
            return null;
        }

        //判断AGV是否空闲
        if (AgvModeEnum.isAuto(rcsAgv.getAgvMode()) &&
                AgvEstopEnum.isNormal(rcsAgv.getEstopState()) &&
                AgvStateEnum.isEnumByCode(AgvStateEnum.IDLE, rcsAgv.getAgvState()) &&
                !AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState()) &&
                AgvGoodsEnum.isEnumByCode(AgvGoodsEnum.NONE, rcsAgv.getGoodsState())) {
            return rcsAgv;
        } else {
            RcsLog.algorithmLog.warn("{} 请检查AGV的模式、急停、状态、任务、载货的数据，当前无法获取空闲AGV信息", agvId);
            return null;
        }
    }

    /**
     * 获取空闲AGV集合
     *
     * @return 空闲AGV集合
     */
    public Map<String, RcsAgv> getIdleRcsAgvMap() {
        Map<String, RcsAgv> tempAgvMap = new ConcurrentHashMap<>(getRcsAgvMap().size());
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();
            //判断AGV是否是空闲
            if (getRcsAgvIdle(rcsAgv) != null) {
                tempAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
            }
        }
        return tempAgvMap;
    }

    /**
     * 获取AGV是否充电
     *
     * @param rcsAgv AGV对象
     * @return 充电状态，true：充电中 false：未充电
     */
    public boolean getRcsAgvIsCharge(RcsAgv rcsAgv) {
        //判断AGV是否充电
        return AgvStateEnum.isEnumByCode(AgvStateEnum.CHARGE, rcsAgv.getAgvState());
    }

    /**
     * 获取充电AGV集合
     *
     * @return 充电AGV集合
     */
    public Map<String, RcsAgv> getChargeRcsAgvMap() {
        Map<String, RcsAgv> tempAgvMap = new ConcurrentHashMap<>(getRcsAgvMap().size());
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();

            //判断AGV是否已校验地图
            boolean mapCheck = getRcsAgvMapCheck(rcsAgv);
            if (!mapCheck) {
                continue;
            }

            //判断AGV是否是隔离
            //充电状态不管是否隔离
            Integer isolation = getRcsAgvIsIsolation(rcsAgv);
            if (isolation == null || !AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, isolation)) {
                continue;
            }
            //判断AGV是否是充电中
            if (getRcsAgvIsCharge(rcsAgv)) {
                tempAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
            }
        }
        return tempAgvMap;
    }

    /**
     * 获取允许取消充电的AGV
     *
     * @return 充电AGV，如果找不到则返回null
     */
    public RcsAgv getAllowCancelChargeRcsAgv(String agvCode) {
        RcsAgv rcsAgv = getRcsAgvByCode(agvCode);

        return getAllowCancelChargeRcsAgv(rcsAgv);
    }

    /**
     * 获取允许取消充电的AGV
     *
     * @return 充电AGV，如果找不到则返回null
     */
    public RcsAgv getAllowCancelChargeRcsAgv(RcsAgv rcsAgv) {
        //判断AGV是否已校验地图
        boolean mapCheck = getRcsAgvMapCheck(rcsAgv);
        if (!mapCheck) {
            return null;
        }

        //判断AGV是否是隔离
        Integer isolation = getRcsAgvIsIsolation(rcsAgv);
        if (isolation == null || !AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, isolation)) {
            return null;
        }
        //判断AGV是否是充电中
        if (!getRcsAgvIsCharge(rcsAgv)) {
            return null;
        }

        //获取AGV充电配置
        ChargeCommonEntity chargeCommon = taskYaml.getChargeCommon();
        //AGV最低工作电量
        Integer lowestWorkPower = chargeCommon.getLowestWorkPower();
        //获取AGV当前电量
        Integer battery = rcsAgv.getBattery();
        //判断AGV是否达到允许工作电量
        if (lowestWorkPower.compareTo(battery) > 0) {
            RcsLog.consoleLog.warn("{} 当前AGV不满足最低工作电量{}，请等待AGV充电", rcsAgv.getAgvId(), lowestWorkPower);
            return null;
        }

        return rcsAgv;
    }

    /**
     * 获取允许取消充电的AGV集合
     *
     * @return 充电AGV集合，如果找不到则返回空集合
     */
    public Map<String, RcsAgv> getAllowCancelChargeRcsAgvMap() {
        Map<String, RcsAgv> tempAgvMap = new ConcurrentHashMap<>(getRcsAgvMap().size());
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();
            //获取允许解除充电的AGV
            RcsAgv allowReleaseChargeRcsAgvMap = getAllowCancelChargeRcsAgv(rcsAgv);
            if (allowReleaseChargeRcsAgvMap != null) {
                tempAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
            }
        }
        return tempAgvMap;
    }

    /**
     * 获取有任务的AGV集合
     *
     * @return 有任务的AGV集合
     */
    public Map<String, RcsAgv> getHasTaskRcsAgvMap() {
        Map<String, RcsAgv> tempAgvMap = new ConcurrentHashMap<>(getRcsAgvMap().size());
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();

            //判断AGV是否已校验地图
            boolean mapCheck = getRcsAgvMapCheck(rcsAgv);
            if (!mapCheck) {
                continue;
            }

            //判断AGV是否是隔离
            Integer isolation = getRcsAgvIsIsolation(rcsAgv);
            if (isolation == null || !AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, isolation)) {
                continue;
            }
            //判断AGV是否有任务
            if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState())) {
                tempAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
            }
        }
        return tempAgvMap;
    }

    /**
     * 获取指定地图号的所有AGV集合
     * <p>
     * 请注意：该方法未过滤任何数据
     *
     * @param mapId 地图编号
     * @return AGV集合
     */
    public Map<String, RcsAgv> getRcsAgvMap(Integer mapId) {
        Map<String, RcsAgv> tempAgvMap = new ConcurrentHashMap<>(getRcsAgvMap().size());
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();
            //判断AGV是否在同一个楼层
            if (mapId.equals(rcsAgv.getMapId())) {
                tempAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
            }
        }
        return tempAgvMap;
    }

    /**
     * 获取指定地图号的有任务的AGV集合
     *
     * @param mapId 地图编号
     * @return 有任务的AGV集合
     */
    public Map<String, RcsAgv> getHasTaskRcsAgvMap(Integer mapId) {
        Map<String, RcsAgv> tempAgvMap = new ConcurrentHashMap<>(getRcsAgvMap().size());
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();
            //判断AGV是否在同一个楼层
            if (rcsAgv.getMapId().equals(mapId)) {
                //判断AGV是否已校验地图
                boolean mapCheck = getRcsAgvMapCheck(rcsAgv);
                if (!mapCheck) {
                    continue;
                }
                //判断AGV是否是隔离
                Integer isolation = getRcsAgvIsIsolation(rcsAgv);
                if (isolation == null || !AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, isolation)) {
                    continue;
                }
                //判断AGV是否有任务
                if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState())) {
                    tempAgvMap.put(rcsAgv.getAgvId(), rcsAgv);
                }
            }
        }
        return tempAgvMap;
    }


    // ********************** 点位操作 ***********************

    /**
     * AGV停车占用管理，负责单车的点位占用管理，如停车占用和离线占用
     * <p>
     * 当隔离状态为0时，则判断AGV是否在线，在线则设置停车占用否则设置离线占用，隔离状态为2则删除停车/离线占用
     */
    public void agvParkOccupyManage() {
        for (Map.Entry<String, RcsAgv> entry : getRcsAgvMap().entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();
            if (rcsAgv == null || rcsAgv.getPointId() == null || rcsAgv.getPointId() < 0) {
                continue;
            }

            String agvId = rcsAgv.getAgvId();
            Integer mapId = rcsAgv.getMapId();
            Integer pointId = rcsAgv.getPointId();

            //判断AGV是否是隔离
            Integer agvIsIsolation = getRcsAgvIsIsolation(rcsAgv);
            //判断AGV是否在线
            boolean agvOnline = getRcsAgvOnline(rcsAgv);
            RcsPoint rcsPoint = mapManager.getRcsPoint(mapId, pointId);
            if (rcsPoint == null) {
                clearAgvAllLocks(agvId);
                continue;
            }

            // 安全获取 Enum，防止 agvIsIsolation 为非法值导致 switch 抛出 NPE
            AgvIsolationStateEnum isolationEnum = AgvIsolationStateEnum.fromEnum(agvIsIsolation);
            if (isolationEnum == null) {
                // 如果状态未知，按"未隔离"处理
                isolationEnum = AgvIsolationStateEnum.NORMAL;
            }
            switch (isolationEnum) {
                //0未隔离
                //1在线隔离
                case AgvIsolationStateEnum.NORMAL:
                case AgvIsolationStateEnum.ONLINE_ISOLATION:
                    if (agvOnline) {
                        //设置停车占用
                        mapManager.updateParkOccupy(agvId, rcsPoint, PointOccupyTypeEnum.PARK);
                    } else {
                        //设置离线占用
                        mapManager.updateParkOccupy(agvId, rcsPoint, PointOccupyTypeEnum.OFFLINE);
                    }
                    break;
                //2离线隔离
                case AgvIsolationStateEnum.OFFLINE_ISOLATION:
                default:
                    //删除停车/离线占用
                    clearAgvAllLocks(agvId);
                    break;
            }
        }
    }

    /**
     * 提取公共清理逻辑，避免重复代码
     */
    private void clearAgvAllLocks(String agvId) {
        Set<RcsPointOccupy> deviceOccupiedPoints = mapManager.getDeviceOccupiedPoints(agvId);
        for (RcsPointOccupy rcsPointOccupy : deviceOccupiedPoints) {
            //移除两种可能的驻留锁
            mapManager.removeOccupyType(agvId, rcsPointOccupy, PointOccupyTypeEnum.PARK);
            mapManager.removeOccupyType(agvId, rcsPointOccupy, PointOccupyTypeEnum.OFFLINE);
        }
    }

    /**
     * 内部逻辑：处理单车的管制占用
     */
    private void handleControlOccupy(RcsAgv agv, List<RcsPoint> pathPoints) {
        String agvId = agv.getAgvId();
        Integer mapId = agv.getMapId();

        // 状态校验
        boolean mapCheck = getRcsAgvMapCheck(agv);
        Integer isolation = getRcsAgvIsIsolation(agv);
        boolean agvOnline = getRcsAgvOnline(agv);
        boolean isNormal = mapCheck && AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, isolation) && agvOnline;

        // --- 1. 管制区 (Control Area) ---
        Map<String, List<RcsPoint>> controlAreaMap = mapManager.getSnapshot().controlAreas().get(mapId);

        for (Map.Entry<String, List<RcsPoint>> entry : controlAreaMap.entrySet()) {
            List<RcsPoint> areaPoints = entry.getValue();

            // 判断路径是否涉及该管制区
            // Collections.disjoint 返回 true 表示没有交集，取反表示有交集
            boolean isIntersect = !Collections.disjoint(pathPoints, areaPoints);

            if (isIntersect && isNormal) {
                // 进入管制区：尝试锁定该区域所有点
                for (RcsPoint point : areaPoints) {
                    mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLAREA);
                }
            } else {
                // 离开管制区或状态异常：释放该区域占用
                // 优化：只释放本车持有的 CONTROLAREA 锁
                for (RcsPoint point : areaPoints) {
                    // 利用 MapManager 的 removeOccupyType 内部校验，不需要先查 getOccupyType
                    // 如果本车没占，remove 会直接返回 false，无副作用
                    mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLAREA);
                }
            }
        }

        // --- 2. 管制点 (Control Point) ---
        Map<RcsPoint, List<RcsPoint>> controlPointMap = mapManager.getSnapshot().controlPoints().get(mapId);

        for (Map.Entry<RcsPoint, List<RcsPoint>> entry : controlPointMap.entrySet()) {
            RcsPoint triggerPoint = entry.getKey();
            List<RcsPoint> mutexPoints = entry.getValue();

            // 如果路径包含触发点
            if (pathPoints.contains(triggerPoint) && isNormal) {
                // 锁定互斥点
                for (RcsPoint point : mutexPoints) {
                    mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLPOINT);
                }
            } else {
                // 释放互斥点
                for (RcsPoint point : mutexPoints) {
                    mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLPOINT);
                }
            }
        }
    }
}
