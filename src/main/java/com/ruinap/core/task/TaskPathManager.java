package com.ruinap.core.task;

import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.algorithm.TrafficManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.event.RcsPointOccupyChangeEvent;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.framework.annotation.Async;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.EventListener;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务路径缓存
 *
 * @author qianye
 * @create 2025-03-11 20:30
 */
@Service
public class TaskPathManager {

    @Autowired
    private TrafficManager trafficManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private SlideTimeWindow slideTimeWindow;

    /**
     * 全局任务路径
     * <p>
     * key 为 AGV ID，
     * value 为任务路径列表
     */
    private static final Map<String, List<TaskPath>> TASK_PATH_MAP = new ConcurrentHashMap<>();

    /**
     * RcsLock 实例，用于控制并发访问
     */
    private final RcsLock RCS_LOCK = RcsLock.ofReadWrite();

    /**
     * 添加任务路径在集合末尾
     *
     * @param key      键
     * @param taskPath 任务路径
     */
    public void put(String key, TaskPath taskPath) {
        if (key == null || taskPath == null) {
            RcsLog.algorithmLog.error("数据校验不通过，存在空值字段：key = {}, taskPath = {}", key, taskPath);
            return;
        }
        RCS_LOCK.runInWrite(() -> {
            // 获取当前的任务路径列表，若不存在则创建一个新的LinkedList
            List<TaskPath> taskPaths = TASK_PATH_MAP.getOrDefault(key, new ArrayList<>());
            // 创建一个副本以避免直接修改原始列表
            taskPaths = new ArrayList<>(taskPaths);

            // 追加到末尾
            taskPaths.addLast(taskPath);

            // 更新 TASK_PATH_MAP
            TASK_PATH_MAP.put(key, taskPaths);
        });
    }

    /**
     * 添加任务路径
     *
     * @param map 集合
     */
    public void putAll(Map<String, List<TaskPath>> map) {
        RCS_LOCK.runInWrite(() -> {
            // 更新 TASK_PATH_MAP
            TASK_PATH_MAP.putAll(map);
        });
    }

    /**
     * 原子操作：仅当指定 AGV 的任务路径列表为空时，才添加新路径。
     *
     * @param agvCode  AGV编号
     * @param taskPath 任务路径
     * @return true 表示添加成功（之前为空）；false 表示添加失败（之前已有任务）
     */
    public boolean putIfEmpty(String agvCode, TaskPath taskPath) {
        if (agvCode == null || taskPath == null) {
            return false;
        }

        // 使用写锁（Write Lock），保证检查和写入期间不会被其他线程打断
        return RCS_LOCK.supplyInWrite(() -> {
            List<TaskPath> currentPaths = TASK_PATH_MAP.get(agvCode);

            // 1. 检查条件：如果列表存在且不为空，则说明已经有任务了，返回失败
            if (currentPaths != null && !currentPaths.isEmpty()) {
                return false;
            }

            // 2. 执行写入：创建新列表并放入 Map
            List<TaskPath> newPaths = new ArrayList<>();
            newPaths.add(taskPath);
            TASK_PATH_MAP.put(agvCode, newPaths);

            return true; // 返回成功
        });
    }

    /**
     * 移除任务路径
     *
     * @param key 键
     */
    public List<TaskPath> remove(String key) {
        return RCS_LOCK.supplyInWrite(() -> TASK_PATH_MAP.remove(key));
    }

    /**
     * 移除指定首条任务路径
     *
     * @param key 键
     */
    public TaskPath removeFirst(String key) {
        return RCS_LOCK.supplyInWrite(() -> {
            // 直接访问 Map，不调用 get(key) 避免嵌套锁
            List<TaskPath> currentPaths = TASK_PATH_MAP.get(key);

            if (currentPaths == null || currentPaths.isEmpty()) {
                return null;
            }

            // Copy-On-Write: 创建副本进行修改
            List<TaskPath> newPaths = new ArrayList<>(currentPaths);
            TaskPath removedTask = newPaths.removeFirst();

            // 如果移除后为空，可以选择从 Map 中删除 key，或者存入空 List
            if (newPaths.isEmpty()) {
                TASK_PATH_MAP.remove(key);
            } else {
                TASK_PATH_MAP.put(key, newPaths);
            }

            return removedTask;
        });
    }

    /**
     * 获取任务路径（使用乐观锁）
     *
     * @param key 键
     * @return 任务路径列表，如果不存在则返回空
     */
    public TaskPath getFirst(String key) {
        return RCS_LOCK.supplyInRead(() -> {
            List<TaskPath> taskPaths = TASK_PATH_MAP.get(key);
            // JDK 21 新语法
            return (taskPaths == null || taskPaths.isEmpty()) ? null : taskPaths.getFirst();
        });
    }

    /**
     * 获取任务路径（使用乐观锁）
     *
     * @param key 键
     * @return 任务路径列表，如果不存在则返回空集合
     */
    public List<TaskPath> get(String key) {
        return RCS_LOCK.supplyInRead(() -> {
            List<TaskPath> list = TASK_PATH_MAP.getOrDefault(key, new ArrayList<>());
            // 重要：返回不可变列表，禁止外部 add/remove
            return Collections.unmodifiableList(list);
        });
    }

    /**
     * 获取全部任务路径（使用悲观锁）
     *
     * @return 任务路径列表
     */
    public Map<String, List<TaskPath>> getAll() {
        return RCS_LOCK.supplyInRead(() -> {
            // 返回一个不可变的快照，防止外部绕过锁直接修改 Map
            if (TASK_PATH_MAP.isEmpty()) {
                return Collections.emptyMap();
            }
            return Map.copyOf(TASK_PATH_MAP);
        });
    }

    /**
     * 检查是否存在任务路径
     *
     * @param key 键
     * @return 如果存在任务路径返回 true，否则返回 false
     */
    public boolean hasTask(String key) {
        return RCS_LOCK.supplyInRead(() -> TASK_PATH_MAP.containsKey(key) && !TASK_PATH_MAP.get(key).isEmpty());
    }

    /**
     * 【新增】监听点位状态变更事件
     * * @param event 点位状态变更事件
     */
    @Async
    @EventListener
    public void onPointOccupyChangeEvent(RcsPointOccupyChangeEvent event) {
        //获取设备编号
        String deviceCode = event.getDeviceCode();
        RcsPointOccupyChangeEvent.ChangeType changeType = event.getChangeType();
        if (changeType != RcsPointOccupyChangeEvent.ChangeType.RELEASED) {
            return;
        }

        PointOccupyTypeEnum occupyType = event.getOccupyType();
        if (PointOccupyTypeEnum.PARK.equals(occupyType)) {
            RcsAgv rcsAgv = agvManager.getRcsAgvByCode(deviceCode);
            RcsPoint currentPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
            List<TaskPath> taskPaths = get(rcsAgv.getAgvId());
            if (!taskPaths.isEmpty()) {
                TaskPath taskPath = taskPaths.getFirst();
                List<RcsPoint> runningRoutes = taskPath.getRunningRoutes();
                List<RcsPoint> traveledRoutes = taskPath.getTraveledRoutes();
                List<RcsPoint> effectiveRunningPoints = taskPath.getEffectiveRunningPoints();

                // 查找currentPoint的所有出现索引，处理多次回退
                int currentIndex = -1;
                // 从后向前找最后出现
                for (int i = runningRoutes.size() - 1; i >= 0; i--) {
                    if (runningRoutes.get(i).equals(currentPoint)) {
                        currentIndex = i;
                        break;
                    }
                }

                if (currentIndex != -1) {
                    // expectNextPoint逻辑
                    if (!currentPoint.equals(taskPath.getTaskOrigin()) && effectiveRunningPoints.size() > 1 && !currentPoint.equals(effectiveRunningPoints.get(1)) && taskPath.getExpectNextPoint() == null) {
                        taskPath.setExpectNextPoint(effectiveRunningPoints.get(1));
                        RcsLog.consoleLog.warn("{} 检测到AGV可能跳跃或回退点位，设置expectNextPoint: {}", rcsAgv.getAgvId(), effectiveRunningPoints.get(1));
                        RcsLog.algorithmLog.warn("{} 检测到AGV可能跳跃或回退点位，设置expectNextPoint: {}", rcsAgv.getAgvId(), effectiveRunningPoints.get(1));
                    } else if (!currentPoint.equals(taskPath.getTaskOrigin()) && effectiveRunningPoints.size() > 1 && currentPoint.equals(effectiveRunningPoints.get(1)) && taskPath.getExpectNextPoint() != null) {
                        RcsLog.consoleLog.warn("{} 清空expectNextPoint: {}", rcsAgv.getAgvId(), taskPath.getExpectNextPoint());
                        RcsLog.algorithmLog.warn("{} 清空expectNextPoint: {}", rcsAgv.getAgvId(), taskPath.getExpectNextPoint());
                        taskPath.setExpectNextPoint(null);
                    }

                    // pointsToAdds: 从0到currentIndex-1 (排除当前点，除非是任务终点)
                    List<RcsPoint> pointsToAdds = new ArrayList<>(runningRoutes.subList(0, currentIndex));

                    // 如果当前点是任务终点，包含它
                    boolean isTaskDestin = taskPath.getCurrentPlanDestin().equals(taskPath.getTaskDestin()) && currentPoint.equals(taskPath.getTaskDestin());
                    if (isTaskDestin) {
                        pointsToAdds.add(runningRoutes.get(currentIndex));
                        RcsLog.algorithmLog.info("{} 当前点位是任务终点，包含在traveledRoutes中: {}", rcsAgv.getAgvId(), currentPoint);
                    }

                    // 计算增量距离（包含到currentPoint的最后段，即使traveled不加当前点）
                    int distance = 0;
                    if (!traveledRoutes.isEmpty()) {
                        RcsPoint lastTraveled = traveledRoutes.get(traveledRoutes.size() - 1);
                        int lastTraveledIndex = -1;
                        for (int i = 0; i < runningRoutes.size(); i++) {
                            // 找第一个匹配，避免重复混淆
                            if (runningRoutes.get(i).equals(lastTraveled)) {
                                lastTraveledIndex = i;
                                break;
                            }
                        }
                        if (lastTraveledIndex != -1 && lastTraveledIndex < currentIndex) {
                            for (int i = lastTraveledIndex; i < currentIndex; i++) {
                                RcsPoint from = runningRoutes.get(i);
                                RcsPoint to = runningRoutes.get(i + 1);
                                distance += GeometryUtils.calculateDistance(from, to);
                            }
                        } else {
                            distance = GeometryUtils.calculateDistance(lastTraveled, currentPoint);
                        }
                    } else {
                        for (int i = 0; i < currentIndex; i++) {
                            RcsPoint from = runningRoutes.get(i);
                            RcsPoint to = runningRoutes.get(i + 1);
                            distance += GeometryUtils.calculateDistance(from, to);
                        }
                    }

                    // 更新traveledRoutes（处理前进/回退）
                    if (pointsToAdds.size() > traveledRoutes.size()) {
                        List<RcsPoint> newPoints = new ArrayList<>(pointsToAdds.subList(traveledRoutes.size(), pointsToAdds.size()));
                        RcsLog.algorithmLog.info("{} 添加新的traveledRoutes点（包含回退路径）: {}", rcsAgv.getAgvId(), newPoints);
                        slideTimeWindow.subWeight(newPoints);
                        taskPath.addTraveledRoutes(newPoints);
                    } else if (pointsToAdds.size() < traveledRoutes.size()) {
                        RcsLog.algorithmLog.info("{} 重构traveledRoutes（处理回退）: {}", rcsAgv.getAgvId(), pointsToAdds);
                        List<RcsPoint> newTraveledRoutes = new ArrayList<>(pointsToAdds);
                        slideTimeWindow.subWeight(newTraveledRoutes);
                        // 直接覆盖引用
                        taskPath.setTraveledRoutes(newTraveledRoutes);
                    }

                    // 更新realizedCost
                    taskPath.setRealizedCost(taskPath.getRealizedCost() + distance);

                    // 缓存AGV路径
                    trafficManager.updateAgvBuffer(rcsAgv.getAgvId(), taskPath.getEffectiveRunningPoints(), rcsAgv.getCarRange());
                } else {
                    RcsLog.consoleLog.warn("{} AGV当前点位: {}，未在运行路径中找到", rcsAgv.getAgvId(), currentPoint);
                    RcsLog.algorithmLog.warn("{} AGV当前点位: {}，未在运行路径中找到", rcsAgv.getAgvId(), currentPoint);
                }
            } else {
                RcsLog.algorithmLog.info("{} 未找到任务路径，跳过处理，点位占用类型: {}", rcsAgv.getAgvId(), occupyType);
            }
        } else {
            RcsLog.algorithmLog.info("{} 跳过处理，点位占用类型: {}", deviceCode, occupyType);
        }
    }
}
