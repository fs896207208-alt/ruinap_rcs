package com.ruinap.core.task.structure;

import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.algorithm.search.RcsAstarSearch;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.domain.DockDevice;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.structure.auction.BidResult;
import com.ruinap.infra.config.InteractionYaml;
import com.ruinap.infra.config.MapYaml;
import com.ruinap.infra.config.pojo.interactions.*;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.task.DockTaskTypeEnum;
import com.ruinap.infra.enums.task.FinallyTaskEnum;
import com.ruinap.infra.enums.task.SubTaskTypeEnum;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import lombok.Getter;

import java.util.*;

/**
 * 任务分段管理类
 * <p>
 * 该类使用 RcsLock 读写锁保证线程安全
 *
 * @author qianye
 * @create 2025-03-11 14:57
 */
@Component
public class TaskSectionManager {

    @Autowired
    private MapManager mapManager;
    @Autowired
    private SlideTimeWindow slideTimeWindow;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private InteractionYaml interactionYaml;
    @Autowired
    private MapYaml mapYaml;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private RcsAstarSearch rcsAstarSearch;

    /**
     * 分段任务缓存, key为AGV编号，value为任务拆分集合
     */
    @Getter
    private final Map<String, List<TaskPath>> taskSectionCache = new HashMap<>();

    /**
     * RcsLock 实例，用于控制并发访问
     */
    private final RcsLock rcsLock = RcsLock.ofReadWrite();

    /**
     * 从缓存中获取指定AGV的分段任务列表
     *
     * @param agvId AGV编号
     * @return 分段任务列表，如果不存在则返回空集合
     */
    public List<TaskPath> getTaskSections(String agvId) {
        // 乐观读：性能极高，无锁读取
        return rcsLock.optimisticRead(
                () -> taskSectionCache.getOrDefault(agvId, new ArrayList<>()),
                () -> taskSectionCache.getOrDefault(agvId, new ArrayList<>())
        );
    }

    /**
     * 将分段任务列表放入缓存
     *
     * @param map 数据集合
     */
    public void putTaskSections(Map<String, List<TaskPath>> map) {
        // 获取写锁
        rcsLock.runInWrite(() -> taskSectionCache.putAll(map));
    }

    /**
     * 将分段任务列表放入缓存
     *
     * @param agvId     AGV编号
     * @param taskPaths 分段任务列表
     */
    public void putTaskSections(String agvId, List<TaskPath> taskPaths) {
        // 获取写锁
        rcsLock.runInWrite(() -> taskSectionCache.put(agvId, taskPaths));
    }

    /**
     * 从缓存中移除指定AGV的所有分段任务
     *
     * @param agvId AGV编号
     * @return 被移除的分段任务列表，如果不存在则返回null
     */
    public List<TaskPath> removeTaskSections(String agvId) {
        // 获取写锁
        return rcsLock.supplyInWrite(() -> taskSectionCache.remove(agvId));
    }

    /**
     * 从缓存中移除首条AGV的分段任务
     *
     * @param agvId AGV编号
     * @return 被移除的首条分段任务，如果不存在则返回null
     */
    public TaskPath removeFirst(String agvId) {
        // 获取写锁
        return rcsLock.supplyInWrite(() -> {
            List<TaskPath> taskPaths = taskSectionCache.get(agvId);
            // 判空保护
            if (taskPaths == null || taskPaths.isEmpty()) {
                return null;
            }
            // JDK 21 List新特性
            TaskPath taskPath = taskPaths.removeFirst();
            // 清理空Entry
            if (taskPaths.isEmpty()) {
                taskSectionCache.remove(agvId);
            }
            return taskPath;
        });
    }

    /**
     * 获取新的分段任务
     * <p>
     * 首次进行任务拆分逻辑的运算先进行电梯任务拆分，再进行风淋室任务拆分，如果拆分失败，则直接使用任务的起终点
     * 第二次再调用则直接返回该AGV的第一条任务分段数据
     *
     * @param bidResult 竞标结果
     * @return 任务拆分结果
     */
    public TaskPath getNewTaskSection(BidResult bidResult) {
        List<TaskPath> taskPaths = new ArrayList<>();

        //获取AGV
        RcsAgv agv = bidResult.getRcsAgv();
        //获取任务
        RcsTask rcsTask = bidResult.getRcsTask();
        //获取AGV编号
        String agvId = agv.getAgvId();

        // 获取AGV编号对应的分段任务
        List<TaskPath> tempPaths = getTaskSections(agvId);
        //如果当前AGV已经存在分段任务，那么直接返回第一条数据
        if (!tempPaths.isEmpty()) {
            RcsLog.algorithmLog.info("AGV编号[{}]已存在分段任务，直接返回第一条数据", agvId);
            return tempPaths.getFirst();
        }

        //任务类型 0搬运任务 1充电任务 2停靠任务 3临时任务 4避让任务
        Integer taskType = rcsTask.getTaskType();
        // 获取任务起点
        RcsPoint taskOrigin = mapManager.getPointByAlias(rcsTask.getOrigin());
        if (taskOrigin == null) {
            RcsLog.consoleLog.error("{} 任务起点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getOrigin());
            RcsLog.algorithmLog.error("{} 任务起点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getOrigin());
            // 添加告警
            alarmManager.triggerAlarm(agvId, rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getOrigin(), "rcs");
            return null;
        }

        // 获取任务终点
        RcsPoint taskDestin = mapManager.getPointByAlias(rcsTask.getDestin());
        if (taskDestin == null) {
            RcsLog.consoleLog.error("{} 任务终点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getDestin());
            RcsLog.algorithmLog.error("{} 任务终点[{}]获取不到点位数据", rcsTask.getTaskCode(), rcsTask.getDestin());
            // 添加告警
            alarmManager.triggerAlarm(agvId, rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getDestin(), "rcs");
            return null;
        }

        // 【性能优化 1】复用竞标阶段已算好的“前往起点”的完整路径 (0次 A* 计算)
        List<RcsPoint> originFullPath = (bidResult.getRoute() != null && bidResult.getRoute().getPaths() != null)
                ? bidResult.getRoute().getPaths() : new ArrayList<>();

        // 【性能优化 2】全局统筹计算“前往终点”的完整路径 (整段任务生命周期仅 1次 A* 计算)
        List<RcsPoint> destinFullPath = new ArrayList<>();
        RouteResult destinRoute = rcsAstarSearch.aStarSearch(agvId, taskOrigin, taskDestin);
        if (destinRoute != null && destinRoute.getPaths() != null) {
            destinFullPath = destinRoute.getPaths();
        }

        //判断任务类型
        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, taskType)) {
            // 获取AGV当前点位
            RcsPoint rcsPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());
            //只有搬运任务才要前往任务起点
            taskPaths.addAll(sectionElevatorTask(agvId, rcsTask, rcsPoint, taskOrigin, SubTaskTypeEnum.ORIGIN, originFullPath));
            //拆分到终点的任务
            taskPaths.addAll(sectionElevatorTask(agvId, rcsTask, taskOrigin, taskDestin, SubTaskTypeEnum.DESTIN, destinFullPath));
        } else {
            //拆分到终点的任务
            taskPaths.addAll(sectionElevatorTask(agvId, rcsTask, taskOrigin, taskDestin, SubTaskTypeEnum.DESTIN, destinFullPath));
        }

        //如果任务进行电梯拆分后集合为空，则使用通用任务拆分
        if (taskPaths.isEmpty()) {
            RcsLog.taskLog.info("{} 如果任务进行电梯拆分后集合为空，则使用通用任务拆分", agvId);
            RcsLog.algorithmLog.info("{} 如果任务进行电梯拆分后集合为空，则使用通用任务拆分", agvId);
            taskPaths.addAll(sectionCommonTask(agv, rcsTask, taskOrigin, taskDestin));
        }

        //风淋室分段任务拆分
        taskPaths = sectionAirShowersTask(rcsTask, taskPaths);

        //如果任务进行风淋室拆分后集合为空，则使用通用任务拆分
        if (taskPaths.isEmpty()) {
            RcsLog.taskLog.info("{} 如果任务进行风淋室拆分后集合为空，则使用通用任务拆分", agvId);
            RcsLog.algorithmLog.info("{} 如果任务进行风淋室拆分后集合为空，则使用通用任务拆分", agvId);
            taskPaths.addAll(sectionCommonTask(agv, rcsTask, taskOrigin, taskDestin));
        }
        //如果任务不为空，并且是搬运任务，则进行输送线分段任务拆分
        if (!taskPaths.isEmpty() && TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, taskType)) {
            taskPaths = sectionConveyorLineTask(rcsTask, taskPaths);
        }

        //处理任务数据
        int count = 1;
        for (TaskPath taskPath : taskPaths) {
            taskPath.setTaskId(rcsTask.getId());
            taskPath.setTaskGroup(rcsTask.getTaskGroup());
            taskPath.setTaskCode(rcsTask.getTaskCode());
            taskPath.setSubTaskNo(count++);
        }

        //将任务分段缓存
        putTaskSections(agvId, taskPaths);
        //返回第一条任务
        return taskPaths.getFirst();
    }

    /**
     * 处理输送线对接逻辑
     *
     * @param taskPaths 任务分段数据
     */
    private List<TaskPath> sectionConveyorLineTask(RcsTask rcsTask, List<TaskPath> taskPaths) {
        List<TaskPath> tempPaths = new ArrayList<>();
        for (TaskPath taskPath : taskPaths) {
            // 获取AGV编号
            String agvId = taskPath.getAgvId();
            //子任务类型 0前往起点任务 1前往终点任务
            int taskType = taskPath.getSubTaskType();
            //获取任务起点
            RcsPoint taskOrigin = taskPath.getTaskOrigin();
            //获取任务终点
            RcsPoint taskDestin = taskPath.getTaskDestin();

            //获取指定点位的输送线配置
            //这里为什么使用任务终点呢？因为任务已经被拆分了，所以这里使用任务终点
            HandoverDeviceEntity conveyorLine = interactionYaml.getHandoverDeviceByRelevancyPoint(taskDestin);
            //只有前往起终点的任务才需要对接输送线
            if (conveyorLine != null && (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType) || SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, taskType))) {
                if (conveyorLine.getDockingPoint() == null) {
                    throw new RuntimeException("输送线【" + conveyorLine.getCode() + "】对接点配置错误");
                }
                //获取输送线对接点点位
                RcsPoint dockingPoint = mapManager.getPointByAlias(conveyorLine.getDockingPoint());

                //取货模式 0任务下发前询问是否可取货 1在对接点询问是否可取货
                int pickupMode = conveyorLine.getPickupMode();
                //卸货模式 0任务下发前询问是否可卸货 1在对接点询问是否可卸货
                int unloadMode = conveyorLine.getUnloadMode();
                // 仅当任务类型和对应的取货/卸货模式匹配时，才进行输送线对接任务拆分
                if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType) && pickupMode == 1 ||
                        SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, taskType) && unloadMode == 1) {
                    //获取AGV信息
                    RcsAgv agv = agvManager.getRcsAgvByCode(agvId);
                    if (agv != null) {
                        //获取AGV当前点位
                        RcsPoint currentPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());
                        //如果AGV当前点位等于输送线对接点位，则进行输送线对接任务拆分
                        if (!dockingPoint.equals(currentPoint)) {
                            //创建从任务起点到输送线对接点的AGV任务
                            TaskPath taskPath1 = new TaskPath();
                            taskPath1.setSubTaskType(taskType);
                            taskPath1.setAgvId(taskPath.getAgvId());
                            taskPath1.setTaskOrigin(taskOrigin);
                            taskPath1.setTaskDestin(dockingPoint);
                            //设置任务动作参数
                            Map<String, Integer> taskPath1Params = mapManager.getDestinActionParameter(agvId, conveyorLine.getDockingPoint(), rcsTask.getTaskType(), rcsTask.getPalletType());
                            taskPath1.setTaskAction(taskPath1Params.get("action"));
                            taskPath1.setTaskParameter(taskPath1Params.get("parameter"));

                            //添加任务到集合
                            tempPaths.add(taskPath1);
                        }
                    } else {
                        throw new RuntimeException("AGV" + agvId + "不存在");
                    }

                    //创建从输送线对接点到任务终点的对接任务
                    TaskPath taskPath2 = new TaskPath();
                    taskPath2.setSubTaskType(taskType);
                    taskPath2.setAgvId(taskPath.getAgvId());
                    taskPath2.setTaskOrigin(dockingPoint);
                    taskPath2.setTaskDestin(taskDestin);

                    DockDevice dockDevice = new DockDevice();
                    dockDevice.setEquipmentId(conveyorLine.getCode());
                    dockDevice.setDockType(DockTaskTypeEnum.CONVEYORLINE.code);
                    dockDevice.setDockingAdvance(conveyorLine.getDockingAdvance());
                    dockDevice.setStartPoint(dockingPoint);
                    dockDevice.setEndPoint(taskDestin);
                    taskPath2.setDockDevice(dockDevice);

                    //判断子任务类型 并 设置任务动作参数
                    if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType)) {
                        //最后任务 0非最终 1起点最终 2终点最终
                        taskPath2.setFinallyTask(FinallyTaskEnum.ORIGIN_FINAL.getCode());
                        Map<String, Integer> originParams = mapManager.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                        taskPath2.setTaskAction(originParams.get("action"));
                        taskPath2.setTaskParameter(originParams.get("parameter"));
                    } else {
                        //最后任务 0非最终 1起点最终 2终点最终
                        taskPath2.setFinallyTask(FinallyTaskEnum.DESTIN_FINAL.getCode());
                        Map<String, Integer> destinParams = mapManager.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                        taskPath2.setTaskAction(destinParams.get("action"));
                        taskPath2.setTaskParameter(destinParams.get("parameter"));
                    }

                    //添加任务到集合
                    tempPaths.add(taskPath2);
                } else {
                    //虽然获取到了输送线配置，但是取货模式和卸货模式都是0，所以不需要对接
                    tempPaths.add(taskPath);
                }
            } else {
                //如果不是搬运任务，则直接添加到任务集合中
                tempPaths.add(taskPath);
            }
        }
        return tempPaths;
    }

    /**
     * 从任务集合中获取一条分段任务
     * <p>
     * 该方法不会进行任务拆分的逻辑，只是从缓存中获取第一条数据，如果集合为空，则返回null
     *
     * @param agvId AGV编号
     * @return 任务分段数据
     */
    public TaskPath getTaskSection(String agvId) {
        List<TaskPath> taskPaths = getTaskSections(agvId);
        if (!taskPaths.isEmpty()) {
            return taskPaths.getFirst();
        }
        return null;
    }

    /**
     * 通用任务拆分
     *
     * @param agv        AGV
     * @param rcsTask    SQL任务
     * @param taskOrigin 任务起点
     * @param taskDestin 任务终点
     * @return 任务拆分集合
     */
    private List<TaskPath> sectionCommonTask(RcsAgv agv, RcsTask rcsTask, RcsPoint taskOrigin, RcsPoint taskDestin) {
        // 初始化任务路径列表
        List<TaskPath> result = new ArrayList<>(2);
        // 获取AGV标识
        String agvId = agv.getAgvId();
        // 从缓存中获取AGV所在位置的RcsPoint对象
        RcsPoint rcsPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());

        // 判断任务类型是否为搬运任务
        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, rcsTask.getTaskType())) {
            // 如果任务起点与AGV当前位置不一致
            if (!taskOrigin.equals(rcsPoint)) {
                // 添加起点任务路径
                Map<String, Integer> originParams = mapManager.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                result.add(createTaskPath(agvId, SubTaskTypeEnum.ORIGIN, rcsPoint, taskOrigin, originParams));

                // 添加终点任务路径
                Map<String, Integer> destinParams = mapManager.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                result.add(createTaskPath(agvId, SubTaskTypeEnum.DESTIN, taskOrigin, taskDestin, destinParams));
            } else {
                // 如果任务起点与AGV当前位置一致，直接添加终点任务路径
                Map<String, Integer> destinParams = mapManager.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                result.add(createTaskPath(agvId, SubTaskTypeEnum.DESTIN, taskOrigin, taskDestin, destinParams));
            }
        } else {
            // 如果任务类型不是搬运任务，添加终点任务路径
            Map<String, Integer> destinParams = mapManager.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
            result.add(createTaskPath(agvId, SubTaskTypeEnum.DESTIN, rcsPoint, taskDestin, destinParams));
        }

        return result;
    }

    /**
     * 创建任务路径的通用方法
     *
     * @param agvId        AGV编号
     * @param subTaskType  子任务类型
     * @param origin       起点
     * @param destin       终点
     * @param actionParams 动作参数
     * @return 任务路径
     */
    private TaskPath createTaskPath(String agvId, SubTaskTypeEnum subTaskType, RcsPoint origin, RcsPoint destin, Map<String, Integer> actionParams) {
        TaskPath taskPath = new TaskPath();
        taskPath.setSubTaskType(subTaskType.code);
        if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, subTaskType.code)) {
            //最后任务 0非最终 1起点最终 2终点最终
            taskPath.setFinallyTask(FinallyTaskEnum.ORIGIN_FINAL.getCode());
        } else if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, subTaskType.code)) {
            //最后任务 0非最终 1起点最终 2终点最终
            taskPath.setFinallyTask(FinallyTaskEnum.DESTIN_FINAL.getCode());
        }
        taskPath.setAgvId(agvId);
        taskPath.setTaskOrigin(origin);
        taskPath.setTaskDestin(destin);
        taskPath.setTaskAction(actionParams.get("action"));
        taskPath.setTaskParameter(actionParams.get("parameter"));
        return taskPath;
    }


    /**
     * 风淋室任务拆分
     *
     * @param taskPaths 任务拆分集合
     * @return 任务拆分集合
     */
    private List<TaskPath> sectionAirShowersTask(RcsTask rcsTask, List<TaskPath> taskPaths) {
        List<TaskPath> finalResult = new ArrayList<>();

        // 1. 遍历任务段
        for (TaskPath taskPath : taskPaths) {
            String agvId = taskPath.getAgvId();
            int taskType = taskPath.getSubTaskType();

            if (!SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType) &&
                    !SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, taskType)) {
                finalResult.add(taskPath);
                continue;
            }

            List<AirShowerEntity> airShowers = interactionYaml.getAirShowers();
            if (airShowers == null || airShowers.isEmpty()) {
                finalResult.add(taskPath);
                continue;
            }

            RcsPoint taskOrigin = taskPath.getTaskOrigin();
            RcsPoint taskDestin = taskPath.getTaskDestin();

            // 2. 获取或规划路径 [修改点: 优先从 expectRoutes 获取，兜底使用 A*]
            List<RcsPoint> paths = taskPath.getExpectRoutes();
            if (paths == null || paths.isEmpty()) {
                RouteResult routeResult = rcsAstarSearch.aStarSearch(agvId, taskOrigin, taskDestin);
                paths = routeResult.getPaths();
                if (paths == null || paths.isEmpty()) {
                    finalResult.add(taskPath);
                    continue;
                }
                // 虽然马上要被废弃，但为了结构统一还是塞回去
                taskPath.setExpectRoutes(new ArrayList<>(paths));
            }

            // 3. 收集并排序
            TreeMap<Integer, Map<String, Object>> sortedMatches = new TreeMap<>();

            for (AirShowerEntity airShower : airShowers) {
                String centerPointStr = airShower.getPoint();
                if (centerPointStr == null) continue;

                RcsPoint centerPoint = mapManager.getPointByAlias(centerPointStr);
                // 核心：路径必须经过风淋室中心
                if (centerPoint == null || !paths.contains(centerPoint)) {
                    continue;
                }

                Map<String, AirShowerInteractionBody> interactions = airShower.getInteractions();
                if (interactions == null) continue;

                for (Map.Entry<String, AirShowerInteractionBody> entry : interactions.entrySet()) {
                    AirShowerInteractionBody body = entry.getValue();
                    RcsPoint startPoint = mapManager.getPointByAlias(body.getStart().getPoint());
                    RcsPoint endPoint = mapManager.getPointByAlias(body.getEnd().getPoint());

                    if (endPoint != null) {
                        int cIdx = paths.indexOf(centerPoint);
                        int eIdx = paths.indexOf(endPoint);

                        // 匹配逻辑:
                        // 1. 必须经过 Center(1-25) -> End(1-23)
                        // 2. 如果 Start(1-43) 也在路径中，必须 Start -> Center
                        // 3. 断点续传场景下，Start 可能不在路径中（已过），允许匹配
                        boolean match = false;
                        if (cIdx != -1 && eIdx != -1 && cIdx < eIdx) {
                            if (startPoint != null) {
                                int sIdx = paths.indexOf(startPoint);
                                if (sIdx != -1) {
                                    if (sIdx < cIdx) match = true;
                                } else {
                                    // 断点续传场景
                                    match = true;
                                }
                            }
                        }

                        if (match) {
                            Map<String, Object> matchData = new HashMap<>();
                            matchData.put("entity", airShower);
                            matchData.put("body", body);
                            matchData.put("start", startPoint);
                            matchData.put("end", endPoint);
                            matchData.put("center", centerPoint);
                            sortedMatches.put(cIdx, matchData);
                            break;
                        }
                    }
                }
            }

            if (sortedMatches.isEmpty()) {
                finalResult.add(taskPath);
                continue;
            }

            // 4. 游标切分任务
            RcsPoint currentCursor = taskOrigin;
            SubTaskTypeEnum tempEnum = SubTaskTypeEnum.fromEnum(taskType);
            // [新增切片逻辑] 定义路径切片游标
            int currentPathIndex = 0;

            for (Map<String, Object> matchData : sortedMatches.values()) {
                AirShowerEntity entity = (AirShowerEntity) matchData.get("entity");
                AirShowerInteractionBody body = (AirShowerInteractionBody) matchData.get("body");
                RcsPoint startPoint = (RcsPoint) matchData.get("start");
                RcsPoint endPoint = (RcsPoint) matchData.get("end");
                RcsPoint centerPoint = (RcsPoint) matchData.get("center");

                // [新增切片逻辑] 查找关键节点索引
                int sIdx = startPoint != null ? paths.indexOf(startPoint) : -1;
                int eIdx = endPoint != null ? paths.indexOf(endPoint) : -1;

                // 1. 判断是否在【门口等待点】(start.point, 如 1-43)
                boolean isAtEntrance = currentCursor.equals(startPoint);

                // 2. 判断是否在【风淋室内部】(entity.point, 如 1-25)
                boolean isInside = currentCursor.equals(centerPoint);

                // 3. 判断是否在【进门门区】(occupancys, 如 1-42)
                boolean isAtDoor = false;
                if (body.getStart().getDoor() != null && entity.getOccupancys() != null) {
                    for (String doorCode : body.getStart().getDoor()) {
                        // 解析 occupancys: "2F_Left_out,1-42"
                        RcsPoint doorPoint = getAirShowerOccupancyPoint(entity.getOccupancys(), doorCode);
                        if (currentCursor.equals(doorPoint)) {
                            isAtDoor = true;
                            break;
                        }
                    }
                }

                // 汇总状态：是否已经在风淋室作业区域
                boolean alreadyInArea = isAtEntrance || isInside || isAtDoor;

                // --- A段: 移动到门口 ---
                if (!alreadyInArea) {
                    TaskPath walkTask = new TaskPath();
                    walkTask.setSubTaskType(tempEnum.code);
                    walkTask.setAgvId(agvId);
                    walkTask.setTaskOrigin(currentCursor);
                    walkTask.setTaskDestin(startPoint);
                    walkTask.setFinallyTask(FinallyTaskEnum.NOT_FINAL.getCode());

                    Map<String, Integer> walkParams = mapManager.getDestinActionParameter(agvId, startPoint.getName(), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
                    if (walkParams != null) {
                        walkTask.setTaskAction(walkParams.getOrDefault("action", 0));
                        walkTask.setTaskParameter(walkParams.getOrDefault("parameter", 0));
                    }

                    // [新增切片逻辑] 截取移动到门口的路段
                    if (sIdx != -1 && currentPathIndex <= sIdx) {
                        walkTask.setExpectRoutes(new ArrayList<>(paths.subList(currentPathIndex, sIdx + 1)));
                    }

                    finalResult.add(walkTask);
                }

                // --- B段: 穿越风淋室 ---
                TaskPath showerTask = new TaskPath();
                showerTask.setSubTaskType(taskType);
                showerTask.setAgvId(agvId);
                showerTask.setTaskOrigin(startPoint);
                showerTask.setTaskDestin(endPoint);
                showerTask.setFinallyTask(FinallyTaskEnum.NOT_FINAL.getCode());

                Map<String, Integer> showerParams = mapManager.getDestinActionParameter(agvId, endPoint.getName(), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
                if (showerParams != null) {
                    showerTask.setTaskAction(showerParams.getOrDefault("action", 0));
                    showerTask.setTaskParameter(showerParams.getOrDefault("parameter", 0));
                }

                // 构建 DockDevice
                DockDevice dockDevice = new DockDevice();
                dockDevice.setEquipmentId(entity.getCode());
                dockDevice.setDockType(DockTaskTypeEnum.AIRSHOWER.code);
                dockDevice.setStartPoint(startPoint);
                dockDevice.setEquipmentPoint(centerPoint);
                dockDevice.setEndPoint(endPoint);

                // 填充前门 (Front Doors)
                // [修改] 直接调用辅助方法，不进行Null判断，因为方法内部会抛异常
                Map<String, RcsPoint> frontDoors = new HashMap<>();
                if (body.getStart().getDoor() != null) {
                    body.getStart().getDoor().forEach(doorCode -> {
                        RcsPoint p = getAirShowerOccupancyPoint(entity.getOccupancys(), doorCode);
                        frontDoors.put(doorCode, p);
                    });
                }
                dockDevice.setFrontDoors(frontDoors);

                // 填充后门 (Back Doors)
                // [修改] 同上
                Map<String, RcsPoint> backDoors = new HashMap<>();
                if (body.getEnd().getDoor() != null) {
                    body.getEnd().getDoor().forEach(doorCode -> {
                        RcsPoint p = getAirShowerOccupancyPoint(entity.getOccupancys(), doorCode);
                        backDoors.put(doorCode, p);
                    });
                }
                dockDevice.setBackDoors(backDoors);

                showerTask.setDockDevice(dockDevice);

                // [新增切片逻辑] 截取风淋室内部路段
                // 强行对齐游标（防止 because of alreadyInArea 导致 A 段没生成，游标落后）
                if (sIdx != -1 && currentPathIndex < sIdx) {
                    currentPathIndex = sIdx;
                }
                if (eIdx != -1 && currentPathIndex <= eIdx) {
                    showerTask.setExpectRoutes(new ArrayList<>(paths.subList(currentPathIndex, eIdx + 1)));
                }

                finalResult.add(showerTask);

                // --- 关键：游标推进 ---
                currentCursor = endPoint;
                if (eIdx != -1) {
                    currentPathIndex = eIdx;
                }
            }

            // --- C段: 收尾 ---
            if (!currentCursor.equals(taskDestin)) {
                TaskPath lastTask = new TaskPath();
                lastTask.setSubTaskType(tempEnum.code);
                lastTask.setAgvId(agvId);
                lastTask.setTaskOrigin(currentCursor);
                lastTask.setTaskDestin(taskDestin);

                if (SubTaskTypeEnum.ORIGIN.equals(tempEnum)) {
                    lastTask.setFinallyTask(FinallyTaskEnum.ORIGIN_FINAL.getCode());
                    Map<String, Integer> originParams = mapManager.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                    if (originParams != null) {
                        lastTask.setTaskAction(originParams.getOrDefault("action", 0));
                        lastTask.setTaskParameter(originParams.getOrDefault("parameter", 0));
                    }
                } else {
                    lastTask.setFinallyTask(FinallyTaskEnum.DESTIN_FINAL.getCode());
                    Map<String, Integer> destinParams = mapManager.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                    if (destinParams != null) {
                        lastTask.setTaskAction(destinParams.getOrDefault("action", 0));
                        lastTask.setTaskParameter(destinParams.getOrDefault("parameter", 0));
                    }
                }

                // [新增切片逻辑] 收尾阶段，将剩余的预期路径全部吃掉
                if (currentPathIndex < paths.size()) {
                    lastTask.setExpectRoutes(new ArrayList<>(paths.subList(currentPathIndex, paths.size())));
                }
                finalResult.add(lastTask);
            }
        }

        return finalResult;
    }

    /**
     * 获取风淋室对接点位
     *
     * @param occupancys 占用点位集合
     * @param doorCode   门编号
     * @return 点位
     */
    private RcsPoint getAirShowerOccupancyPoint(List<String> occupancys, String doorCode) {
        RcsPoint doorPoint = null;
        for (String occupancy : occupancys) {
            String[] split = occupancy.split(",");
            if (doorCode.equalsIgnoreCase(split[0])) {
                doorPoint = mapManager.getPointByAlias(split[1]);
            }
        }
        if (doorPoint == null) {
            throw new RuntimeException("风淋室的occupancys属性里[" + doorCode + "]获取不到点位数据");
        }
        return doorPoint;
    }

    /**
     * 电梯任务拆分，将数据库的任务进行拆分，方便后续调度进行处理
     *
     * @param agvId       AGV编号
     * @param rcsTask     SQL任务
     * @param taskOrigin  任务起点
     * @param taskDestin  任务终点
     * @param subTaskType 子任务类型
     * @param fullPaths   A*规划的全路径
     * @return 任务拆分集合
     */
    private List<TaskPath> sectionElevatorTask(String agvId, RcsTask rcsTask, RcsPoint taskOrigin, RcsPoint taskDestin, SubTaskTypeEnum subTaskType, List<RcsPoint> fullPaths) {
        List<TaskPath> result = new ArrayList<>();

        // 1. 判断楼层是否一样，如果一样直接返回空（或在上层逻辑处理）
        if (Objects.equals(taskOrigin.getMapId(), taskDestin.getMapId())) {
            RcsLog.algorithmLog.info("{} 起终点楼层相同，无需电梯拆分", agvId);
            return result;
        }

        // 2. 获取规划路径
        if (fullPaths == null || fullPaths.isEmpty()) {
            RouteResult routeResult = rcsAstarSearch.aStarSearch(agvId, taskOrigin, taskDestin);
            List<RcsPoint> paths = routeResult.getPaths();
            if (paths == null || paths.isEmpty()) {
                throw new RuntimeException("电梯拆分任务时规划路径为空");
            }
            fullPaths = paths;
        }

        // 3. 获取桥接点位配置数据 (注意：此处必须保证返回的List是按路径先后顺序排序的！)
        List<LinkedHashMap<String, String>> bridgePointList = mapManager.getTargetBridgePoint(fullPaths);
        if (bridgePointList == null || bridgePointList.isEmpty()) {
            RcsLog.algorithmLog.info("{} 获取不到桥接点配置数据，调度认为不需要对接电梯", agvId);
            return result;
        }

        // --- 定义游标：当前分段任务的起点，初始为总任务起点 ---
        RcsPoint currentTaskOrigin = taskOrigin;
        // [新增切片逻辑] 定义路径切片游标：记录当前切片在 fullPaths 中的起始索引
        int currentPathIndex = 0;

        // 4. 循环遍历路径上经过的所有电梯
        for (LinkedHashMap<String, String> bridgePointMap : bridgePointList) {
            // 获取设备编码和桥接ID
            String equipmentCode = bridgePointMap.get("equipment_code");
            String bridgeId = bridgePointMap.get("bridge_id");
            RcsPoint origin = mapManager.getPointByAlias(bridgePointMap.get("origin"));
            RcsPoint destin = mapManager.getPointByAlias(bridgePointMap.get("destin"));

            // [新增切片逻辑] 查找电梯口和出口在总路径中的索引
            int entranceIdx = fullPaths.indexOf(origin);
            int exitIdx = fullPaths.indexOf(destin);

            // 获取指定的电梯实体
            ElevatorEntity elevatorEntity = interactionYaml.getElevatorByCode(equipmentCode);
            if (elevatorEntity == null) {
                RcsLog.algorithmLog.error("{} 未找到电梯配置 code:{}", agvId, equipmentCode);
                continue;
            }

            // 获取交互配置体
            Map<String, InteractionsBody> interactions = elevatorEntity.getInteractions();
            if (interactions == null || interactions.isEmpty()) {
                throw new RuntimeException("电梯[" + equipmentCode + "]的Interactions属性为空");
            }
            InteractionsBody interactionsBody = interactions.get(bridgeId);
            if (interactionsBody == null) {
                RcsLog.algorithmLog.error("{} 传送点：{}，获取不到InteractionsBody的电梯配置", agvId, bridgeId);
                continue;
            }

            // 解析电梯的入口(Start)和出口(End)点位
            String startPointAlias = interactionsBody.getStart().getPoint();
            String endPointAlias = interactionsBody.getEnd().getPoint();
            if (startPointAlias == null || endPointAlias == null) {
                throw new RuntimeException("电梯交互配置缺少start或end点位信息");
            }

            // 轿厢内(起始层)
            RcsPoint innerPointStart = mapManager.getPointByAlias(startPointAlias);
            // 轿厢内(目标层)
            RcsPoint innerPointEnd = mapManager.getPointByAlias(endPointAlias);
            if (innerPointStart == null || innerPointEnd == null) {
                throw new RuntimeException("电梯点位在地图中未定义: " + startPointAlias + "/" + endPointAlias);
            }

            //判断当前AGV是否在电梯口 (Waiting Point)
            // 对应: bridge_point.桥编号.origin (即 origin)
            boolean isAtEntrance = currentTaskOrigin.equals(origin);

            // 判断当前AGV是否在电梯门点位 (Door Point)
            boolean isAtDoor = false;
            List<String> startDoors = interactionsBody.getStart().getDoor();
            if (startDoors != null && elevatorEntity.getOccupancys() != null) {
                for (String doorCode : startDoors) {
                    // 遍历 occupancys 查找 "doorCode,mapId-pointId"
                    for (String occupancy : elevatorEntity.getOccupancys()) {
                        String[] split = occupancy.split(",");
                        if (split.length >= 2 && doorCode.equalsIgnoreCase(split[0])) {
                            RcsPoint doorPoint = mapManager.getPointByAlias(split[1]);
                            if (currentTaskOrigin.equals(doorPoint)) {
                                isAtDoor = true;
                                break;
                            }
                        }
                    }
                    if (isAtDoor) {
                        break;
                    }
                }
            }

            // 判断当前AGV是否在电梯里 (Inside Point)
            boolean isInside = currentTaskOrigin.equals(innerPointStart) || currentTaskOrigin.equals(innerPointEnd);

            // 汇总状态：是否已经在电梯相关区域
            boolean alreadyInElevatorArea = isAtEntrance || isAtDoor || isInside;

            // --- 步骤A: 生成【移动任务】(从 当前位置 -> 走到电梯口) ---
            // 只有当 AGV 不在电梯口、不在门区、也不在轿厢内时，才生成这一段
            if (!alreadyInElevatorArea) {
                TaskPath walkTask = new TaskPath();
                walkTask.setSubTaskType(subTaskType.code);
                walkTask.setAgvId(agvId);
                // 使用游标
                walkTask.setTaskOrigin(currentTaskOrigin);
                walkTask.setTaskDestin(origin);
                // 中间段非最终
                walkTask.setFinallyTask(FinallyTaskEnum.NOT_FINAL.getCode());

                // 获取移动到电梯口的动作参数（通常是普通的移动，或者有特定的入库前动作）
                // 这里逻辑复用 DestinActionParameter，传入 TaskTypeEnum.DOCK 表示对接前置动作
                Map<String, Integer> walkParams = mapManager.getDestinActionParameter(agvId, origin.getName(), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
                if (walkParams != null) {
                    walkTask.setTaskAction(walkParams.getOrDefault("action", 0));
                    walkTask.setTaskParameter(walkParams.getOrDefault("parameter", 0));
                }

                // [新增切片逻辑] 截取“当前点”到“电梯口”的路径
                if (entranceIdx != -1 && currentPathIndex <= entranceIdx) {
                    walkTask.setExpectRoutes(new ArrayList<>(fullPaths.subList(currentPathIndex, entranceIdx + 1)));
                }

                result.add(walkTask);
            }

            // --- 步骤B: 生成【电梯对接任务】(从 电梯入口 -> 电梯出口) ---
            TaskPath elevatorTask = new TaskPath();
            elevatorTask.setSubTaskType(subTaskType.code);
            elevatorTask.setAgvId(agvId);
            // 如果AGV已经在电梯区域(门区或轿厢内)，为了防止坐标跳跃，
            // 必须将任务起点修正为AGV当前的实际物理点位 (currentTaskOrigin)；否则使用等待点(origin)
            elevatorTask.setTaskOrigin(alreadyInElevatorArea ? currentTaskOrigin : origin);
            elevatorTask.setTaskDestin(destin);
            // 电梯段也非最终
            elevatorTask.setFinallyTask(FinallyTaskEnum.NOT_FINAL.getCode());

            // 设置电梯动作参数
            Map<String, Integer> elevatorParams = mapManager.getDestinActionParameter(agvId, destin.getName(), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
            if (elevatorParams != null) {
                elevatorTask.setTaskAction(elevatorParams.getOrDefault("action", 0));
                elevatorTask.setTaskParameter(elevatorParams.getOrDefault("parameter", 0));
            }

            // --- 内联构建 DockDevice 对象 ---
            DockDevice dockDevice = new DockDevice();
            dockDevice.setEquipmentId(elevatorEntity.getCode());
            dockDevice.setDockType(DockTaskTypeEnum.ELEVATOR.code);
            dockDevice.setStartPoint(innerPointStart);
            dockDevice.setEndPoint(innerPointEnd);

            // 构建前门(FrontDoors) Map
            Map<String, RcsPoint> frontDoors = new HashMap<>(2);
            // 所有电梯门配置 "doorCode,mapId-pointId"
            List<String> occupancys = elevatorEntity.getOccupancys();
            if (interactionsBody.getStart().getDoor() != null) {
                interactionsBody.getStart().getDoor().forEach(doorCode -> {
                    if (occupancys != null) {
                        for (String occupancy : occupancys) {
                            String[] split = occupancy.split(",");
                            if (split.length >= 2 && doorCode.equalsIgnoreCase(split[0])) {
                                frontDoors.put(doorCode, mapManager.getPointByAlias(split[1]));
                            }
                        }
                    }
                });
            }
            dockDevice.setFrontDoors(frontDoors);

            // 构建后门(BackDoors) Map
            Map<String, RcsPoint> backDoors = new HashMap<>(2);
            if (interactionsBody.getEnd().getDoor() != null) {
                interactionsBody.getEnd().getDoor().forEach(doorCode -> {
                    if (occupancys != null) {
                        for (String occupancy : occupancys) {
                            String[] split = occupancy.split(",");
                            if (split.length >= 2 && doorCode.equalsIgnoreCase(split[0])) {
                                backDoors.put(doorCode, mapManager.getPointByAlias(split[1]));
                            }
                        }
                    }
                });
            }
            dockDevice.setBackDoors(backDoors);

            elevatorTask.setDockDevice(dockDevice);

            // [新增切片逻辑] 截取“电梯入口”到“电梯出口”的路径
            if (entranceIdx != -1 && exitIdx != -1 && entranceIdx <= exitIdx) {
                // 防止 AGV 已经在电梯口导致 walkTask 没生成，游标没推进的情况
                currentPathIndex = entranceIdx;
                elevatorTask.setExpectRoutes(new ArrayList<>(fullPaths.subList(currentPathIndex, exitIdx + 1)));
            }

            result.add(elevatorTask);

            // --- 关键：游标推进 ---
            // 下一段任务的起点，变成当前电梯的出口点
            currentTaskOrigin = destin;
            // [新增切片逻辑] 更新大路径的索引游标
            if (exitIdx != -1) {
                currentPathIndex = exitIdx;
            }
        }

        // 5. 循环结束后，生成【最后一段移动任务】(从 最后一个电梯出口 -> 最终目的地)
        // 防止出了电梯就是终点
        if (!currentTaskOrigin.equals(taskDestin)) {
            TaskPath finalTask = new TaskPath();
            finalTask.setSubTaskType(subTaskType.code);
            finalTask.setAgvId(agvId);
            finalTask.setTaskOrigin(currentTaskOrigin);
            finalTask.setTaskDestin(taskDestin);

            // 设置最终状态参数 (0非最终 1起点最终 2终点最终)
            if (SubTaskTypeEnum.ORIGIN.equals(subTaskType)) {
                // 起点最终
                finalTask.setFinallyTask(FinallyTaskEnum.ORIGIN_FINAL.getCode());
                Map<String, Integer> originParams = mapManager.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                if (originParams != null) {
                    finalTask.setTaskAction(originParams.getOrDefault("action", 0));
                    finalTask.setTaskParameter(originParams.getOrDefault("parameter", 0));
                }
            } else {
                // 终点最终 (默认)
                finalTask.setFinallyTask(FinallyTaskEnum.DESTIN_FINAL.getCode());
                Map<String, Integer> destinParams = mapManager.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
                if (destinParams != null) {
                    finalTask.setTaskAction(destinParams.getOrDefault("action", 0));
                    finalTask.setTaskParameter(destinParams.getOrDefault("parameter", 0));
                }
            }

            // [新增切片逻辑] 截取“最后电梯出口”到“终点”的路径
            if (currentPathIndex < fullPaths.size()) {
                finalTask.setExpectRoutes(new ArrayList<>(fullPaths.subList(currentPathIndex, fullPaths.size())));
            }

            result.add(finalTask);
        }

        return result;
    }
}
