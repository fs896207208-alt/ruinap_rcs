package com.ruinap.core.map;

import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.enums.agv.AgvIsolationStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.*;

/**
 * 点位占用管理
 *
 * @author qianye
 * @create 2026-03-13 15:46
 */
@Component
public class PointOccupyManager {

    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private TaskPathManager taskPathManager;

    // =========================================================================
    // 【模块一：调度引擎内部动态锁刷新逻辑】 (随定时器或业务事件高频触发)
    // =========================================================================

    /**
     * 1. AGV 停车占用管理：负责单车的停车(PARK)和离线(OFFLINE)占用
     */
    public void agvParkOccupyManage() {
        agvManager.getRcsAgvMap().values().forEach(agv -> {
            if (agv.getPointId() == null || agv.getPointId() < 0) {
                return;
            }

            String agvId = agv.getAgvId();
            //判断AGV是否是隔离
            Integer isolation = agvManager.getRcsAgvByIsolation(agv);
            boolean online = agvManager.getRcsAgvByOnline(agv);
            RcsPoint currentPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());
            if (currentPoint == null) {
                return;
            }

            // 基于 JDK 21 的增强 Switch 模式匹配思想
            switch (AgvIsolationStateEnum.fromEnum(isolation)) {
                case NORMAL, ONLINE_ISOLATION -> {
                    if (online) {
                        addParkOccupy(agvId, currentPoint);
                    } else {
                        addOfflineOccupy(agvId, currentPoint);
                    }
                }
                case OFFLINE_ISOLATION -> {
                    // 离线且被人工隔离时，安全撤销其物理防撞保护
                    removeParkOccupy(agvId, currentPoint);
                    removeOfflineOccupy(agvId, currentPoint);
                }
            }
        });
    }

    /**
     * 2. 全局扫描并更新所有 AGV 的管制区/管制点占用
     */
    public void agvControlOccupy() {
        agvManager.getRcsAgvMap().values().forEach(agv -> {
            if (agv == null || agv.getPointId() == null || agv.getPointId() < 0) {
                return;
            }

            RcsPoint currentPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());
            if (currentPoint == null) {
                return;
            }

            List<RcsPoint> pathPoints = buildEffectivePathPoints(agv);
            handleControlOccupy(agv, currentPoint, pathPoints);
        });
    }

    /**
     * 设置管制区和管制点占用
     *
     * @param agv          AGV
     * @param currentPoint 当前点位
     * @param pathPoints   路径集合
     */
    private void handleControlOccupy(RcsAgv agv, RcsPoint currentPoint, List<RcsPoint> pathPoints) {
        String agvId = agv.getAgvId();
        var snapshot = mapManager.getSnapshot();

        // 防御性获取管制配置
        Map<String, List<RcsPoint>> controlAreaMap = snapshot.controlAreas().getOrDefault(currentPoint.getMapId(), Map.of());
        Map<RcsPoint, List<RcsPoint>> controlPointMap = snapshot.controlPoints().getOrDefault(currentPoint.getMapId(), Map.of());

        // 路径转 Set 提升匹配性能至 O(1)
        Set<RcsPoint> pathPointSet = new HashSet<>(pathPoints);

        // 核心合法状态：已校验地图 且 未被人工隔离
        boolean isAgvNormal = agvManager.getRcsAgvMapCheck(agv) &&
                AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, agvManager.getRcsAgvByIsolation(agv));

        // 处理管制区 (Control Area)
        controlAreaMap.forEach((areaId, points) -> {
            boolean isIntersect = points.stream().anyMatch(pathPointSet::contains);
            if (isIntersect && isAgvNormal) {
                points.forEach(p -> addControlAreaOccupy(agvId, p));
            } else {
                points.forEach(p -> removeControlAreaOccupy(agvId, p));
            }
        });

        // 处理管制点 (Control Point)
        controlPointMap.forEach((keyPoint, points) -> {
            if (pathPointSet.contains(keyPoint) && isAgvNormal) {
                points.forEach(p -> addControlPointOccupy(agvId, p));
            } else {
                points.forEach(p -> removeControlPointOccupy(agvId, p));
            }
        });
    }

    /**
     * 3. 释放任务(TASK)点位占用：当 AGV 驶过点位后，清理走过的路线占用
     */
    public void releaseTaskPointOccupy() {
        agvManager.getRcsAgvMap().values().forEach(agv -> {
            if (!agvManager.getRcsAgvMapCheck(agv)) {
                return;
            }

            String agvId = agv.getAgvId();
            boolean isNormal = AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, agvManager.getRcsAgvByIsolation(agv));
            boolean isOnline = agvManager.getRcsAgvByOnline(agv);

            if (isNormal && isOnline) {
                List<TaskPath> taskPaths = taskPathManager.get(agvId);
                if (taskPaths != null && !taskPaths.isEmpty()) {
                    TaskPath taskPath = taskPaths.getFirst();
                    Set<RcsPoint> effectivePoints = new HashSet<>(taskPath.getEffectiveRunningPoints());

                    // 只释放已经走过，且未来不再经过的点
                    taskPath.getTraveledRoutes().forEach(p -> {
                        if (!effectivePoints.contains(p)) {
                            removeTaskOccupy(agvId, p);
                        }
                    });
                } else {
                    // 没有任务时，直接向 MapManager 查询该车所有的 TASK 锁并清空 (杜绝幽灵锁)
                    mapManager.getDeviceOccupiedPoints(agvId, PointOccupyTypeEnum.TASK)
                            .forEach(occupy -> mapManager.removeOccupyType(agvId, occupy, PointOccupyTypeEnum.TASK));
                }
            }
        });
    }

    /**
     * 4. 设置及释放车距(DISTANCE)占用 (基于多边形与空间树的增量更新)
     */
    public void handleVehicleDistanceOccupy() {
        agvManager.getRcsAgvMap().values().forEach(agv -> {
            if (agv == null || agv.getPointId() == null || agv.getPointId() < 0) {
                return;
            }

            String agvId = agv.getAgvId();
            Integer mapId = agv.getMapId();
            boolean isNormal = agvManager.getRcsAgvMapCheck(agv) &&
                    AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, agvManager.getRcsAgvByIsolation(agv));

            if (!isNormal) {
                // 车辆隔离或未校验，一键释放所有车距幽灵锁
                mapManager.getDeviceOccupiedPoints(agvId, PointOccupyTypeEnum.DISTANCE)
                        .forEach(occupy -> mapManager.removeOccupyType(agvId, occupy, PointOccupyTypeEnum.DISTANCE));
                return;
            }

            List<RcsPoint> pathPoints = buildEffectivePathPoints(agv);
            if (pathPoints.isEmpty()) {
                return;
            }

            int carRange = agv.getCarRange() == null ? 0 : agv.getCarRange();
            GeometryUtils.EdgeProvider edgeProvider = (u, v) -> mapManager.getRcsPointTarget(mapId, u, v);

            // 1. 计算外接矩形
            int[] bbox = GeometryUtils.calculatePathBoundingBox(pathPoints, carRange, edgeProvider);
            if (bbox == null) {
                return;
            }

            // 2. STRtree 空间索引初筛
            STRtree spatialIndex = mapManager.getSnapshot().spatialIndexes().get(mapId);
            List<RcsPoint> candidatePoints = GeometryUtils.querySpatialIndex(spatialIndex, bbox[0], bbox[1], bbox[2], bbox[3]);

            Set<RcsPoint> pathPointSet = new HashSet<>(pathPoints);
            Set<Integer> expectedPointIds = new HashSet<>();

            // 3. 几何精算与加锁
            candidatePoints.forEach(p -> {
                if (!pathPointSet.contains(p) && GeometryUtils.isPointWithinBoundingBox(bbox, p.getX(), p.getY())) {
                    if (GeometryUtils.isPointWithinPathTolerance(pathPoints, carRange, p.getX(), p.getY(), edgeProvider)) {
                        expectedPointIds.add(p.getId());
                        addDistanceOccupy(agvId, p);
                    }
                }
            });

            // 4. 同步清理老旧幽灵锁
            mapManager.getDeviceOccupiedPoints(agvId, PointOccupyTypeEnum.DISTANCE).forEach(occupy -> {
                if (!expectedPointIds.contains(occupy.getPointId())) {
                    mapManager.removeOccupyType(agvId, occupy, PointOccupyTypeEnum.DISTANCE);
                }
            });
        });
    }

    /**
     * 5. 释放选择点位占用 (CHOOSE - 算法寻路临时锁)
     */
    public void releaseChooseOccupy() {
        // 利用底层倒排索引快速定位
        mapManager.getDeviceOccupyIndex().forEach((targetId, occupies) -> {
            boolean taskStillValid = taskPathManager.get(targetId) != null && !taskPathManager.get(targetId).isEmpty();
            // 任务结束，精准释放 CHOOSE 锁
            if (!taskStillValid) {
                occupies.stream()
                        .filter(o -> o.containsType(PointOccupyTypeEnum.CHOOSE))
                        .toList() // 创建副本防止并发修改异常
                        .forEach(o -> mapManager.removeOccupyType(targetId, o, PointOccupyTypeEnum.CHOOSE));
            }
        });
    }

    /**
     * 辅助方法：构建物理安全防撞的有效路径
     */
    private List<RcsPoint> buildEffectivePathPoints(RcsAgv agv) {
        List<RcsPoint> pathPoints = new ArrayList<>();
        RcsPoint currentPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());

        List<TaskPath> taskPaths = taskPathManager.get(agv.getAgvId());
        if (taskPaths != null && !taskPaths.isEmpty()) {
            TaskPath tp = taskPaths.getFirst();
            pathPoints.addAll(tp.getEffectiveRunningPoints());
            if (pathPoints.isEmpty()) {
                pathPoints.addAll(tp.getNewPlanRoutes());
            }
        }
        // 绝对防线：保证车体物理所在点绝不丢锁
        if (currentPoint != null && !pathPoints.contains(currentPoint)) {
            pathPoints.add(currentPoint);
        }
        return pathPoints;
    }

    // =========================================================================
    // 【模块二：全景锁务 API 门面】 (共计 11 种合法枚举锁的显式映射)
    // =========================================================================

    // --- 第一阵营：调度引擎内部动态锁 ---

    /**
     * 1. 添加任务点位占用 (TASK)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean addTaskOccupy(String agvId, RcsPoint point) {
        return mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.TASK);
    }

    /**
     * 2. 释放任务点位占用 (TASK)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean removeTaskOccupy(String agvId, RcsPoint point) {
        return mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.TASK);
    }

    /**
     * 3. 添加车距点位占用 (DISTANCE)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean addDistanceOccupy(String agvId, RcsPoint point) {
        return mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.DISTANCE);
    }

    /**
     * 4. 释放车距点位占用 (DISTANCE)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean removeDistanceOccupy(String agvId, RcsPoint point) {
        return mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.DISTANCE);
    }

    /**
     * 5. 添加控制区域点位占用 (CONTROLAREA)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean addControlAreaOccupy(String agvId, RcsPoint point) {
        return mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLAREA);
    }

    /**
     * 6. 释放控制区域点位占用 (CONTROLAREA)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean removeControlAreaOccupy(String agvId, RcsPoint point) {
        return mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLAREA);
    }

    /**
     * 7. 添加控制点位占用 (CONTROLPOINT)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean addControlPointOccupy(String agvId, RcsPoint point) {
        return mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLPOINT);
    }

    /**
     * 8. 释放控制点位占用 (CONTROLPOINT)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean removeControlPointOccupy(String agvId, RcsPoint point) {
        return mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.CONTROLPOINT);
    }

    /**
     * 9. 添加选择点位占用 (CHOOSE)
     *
     * @param taskCode 任务编号
     * @param point    点位
     * @return 是否成功
     */
    boolean addChooseOccupy(String taskCode, RcsPoint point) {
        return mapManager.addOccupyType(taskCode, point, PointOccupyTypeEnum.CHOOSE);
    }

    /**
     * 10. 释放选择点位占用 (CHOOSE)
     *
     * @param taskCode 任务编号
     * @param point    点位
     * @return 是否成功
     */
    boolean removeChooseOccupy(String taskCode, RcsPoint point) {
        return mapManager.removeOccupyType(taskCode, point, PointOccupyTypeEnum.CHOOSE);
    }

    // --- 第二阵营：AGV 驻留状态锁 ---

    /**
     * 11. 添加AGV驻留点位占用 (PARK)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean addParkOccupy(String agvId, RcsPoint point) {
        return mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.PARK);
    }

    /**
     * 12. 释放AGV驻留点位占用 (PARK)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean removeParkOccupy(String agvId, RcsPoint point) {
        return mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.PARK);
    }

    /**
     * 13. 添加AGV离线点位占用 (OFFLINE)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean addOfflineOccupy(String agvId, RcsPoint point) {
        return mapManager.addOccupyType(agvId, point, PointOccupyTypeEnum.OFFLINE);
    }

    /**
     * 14. 释放AGV离线点位占用 (OFFLINE)
     *
     * @param agvId AGV编号
     * @param point 点位
     * @return 是否成功
     */
    boolean removeOfflineOccupy(String agvId, RcsPoint point) {
        return mapManager.removeOccupyType(agvId, point, PointOccupyTypeEnum.OFFLINE);
    }

    // --- 第三阵营：外部干预与静态锁 (公开给 Http 等外部调用) ---

    /**
     * 15. 添加手动点位占用 (MANUAL)
     *
     * @param operatorId 操作员编号
     * @param point      点位
     * @return 是否成功
     */
    public boolean addManualOccupy(String operatorId, RcsPoint point) {
        return mapManager.addOccupyType(operatorId, point, PointOccupyTypeEnum.MANUAL);
    }

    /**
     * 16. 释放手动点位占用 (MANUAL)
     *
     * @param operatorId 操作员编号
     * @param point      点位
     * @return 是否成功
     */
    public boolean removeManualOccupy(String operatorId, RcsPoint point) {
        return mapManager.removeOccupyType(operatorId, point, PointOccupyTypeEnum.MANUAL);
    }

    /**
     * 17. 添加阻塞点位占用 (BLOCK)
     *
     * @param operatorId 操作员编号
     * @param point      点位
     * @return 是否成功
     */
    public boolean addBlockOccupy(String operatorId, RcsPoint point) {
        return mapManager.addOccupyType(operatorId, point, PointOccupyTypeEnum.BLOCK);
    }

    /**
     * 18. 释放阻塞点位占用 (BLOCK)
     *
     * @param operatorId 操作员编号
     * @param point      点位
     * @return 是否成功
     */
    public boolean removeBlockOccupy(String operatorId, RcsPoint point) {
        return mapManager.removeOccupyType(operatorId, point, PointOccupyTypeEnum.BLOCK);
    }

    /**
     * 19. 添加地图配置点位占用 (CONFIG)
     *
     * @param mapSourceId 地图配置编号
     * @param point       点位
     * @return 是否成功
     */
    public boolean addConfigOccupy(String mapSourceId, RcsPoint point) {
        return mapManager.addOccupyType(mapSourceId, point, PointOccupyTypeEnum.CONFIG);
    }

    /**
     * 20. 释放地图配置点位占用 (CONFIG)
     *
     * @param mapSourceId 地图配置编号
     * @param point       点位
     * @return 是否成功
     */
    public boolean removeConfigOccupy(String mapSourceId, RcsPoint point) {
        return mapManager.removeOccupyType(mapSourceId, point, PointOccupyTypeEnum.CONFIG);
    }

    // --- 第四阵营：外部联动设备锁 ---

    /**
     * 21. 添加外部联动设备点位占用 (EQUIPMENT)
     *
     * @param equipmentCode 设备编号
     * @param point         点位
     * @return 是否成功
     */
    public boolean addEquipmentOccupy(String equipmentCode, RcsPoint point) {
        return mapManager.addOccupyType(equipmentCode, point, PointOccupyTypeEnum.EQUIPMENT);
    }

    /**
     * 22. 释放外部联动设备点位占用 (EQUIPMENT)
     *
     * @param equipmentCode 设备编号
     * @param point         点位
     * @return 是否成功
     */
    public boolean removeEquipmentOccupy(String equipmentCode, RcsPoint point) {
        return mapManager.removeOccupyType(equipmentCode, point, PointOccupyTypeEnum.EQUIPMENT);
    }
}