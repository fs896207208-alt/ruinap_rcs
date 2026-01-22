package com.ruinap.core.task.structure;

/**
 * 任务分段管理类
 * <p>
 * 该类使用 RcsLock 读写锁保证线程安全
 *
 * @author qianye
 * @create 2025-03-11 14:57
 */
public class TaskSectionManage {

//    /**
//     * 分段任务缓存, key为AGV编号，value为任务拆分集合
//     */
//    @Getter
//    private static final Map<String, List<TaskPath>> TASK_SECTION_CACHE = new HashMap<>();
//
//    /**
//     * RcsLock 实例，用于控制并发访问
//     */
//    private final RcsLock RCS_LOCK = new RcsLock();
//
//    /**
//     * 从缓存中获取指定AGV的分段任务列表
//     *
//     * @param agvId AGV编号
//     * @return 分段任务列表，如果不存在则返回空集合
//     */
//    public static List<TaskPath> getTaskSections(String agvId) {
//        long stamp = RCS_LOCK.tryOptimisticRead();
//        List<TaskPath> taskPaths = TASK_SECTION_CACHE.getOrDefault(agvId, new ArrayList<>());
//        if (!RCS_LOCK.validate(stamp)) {
//            stamp = RCS_LOCK.readLock();
//            try {
//                taskPaths = TASK_SECTION_CACHE.getOrDefault(agvId, new ArrayList<>());
//            } finally {
//                RCS_LOCK.unlockRead(stamp);
//            }
//        }
//        return taskPaths;
//    }
//
//    /**
//     * 将分段任务列表放入缓存
//     *
//     * @param map 数据集合
//     */
//    public static void putTaskSections(Map<String, List<TaskPath>> map) {
//        // 获取写锁
//        long stamp = RCS_LOCK.writeLock();
//        try {
//            TASK_SECTION_CACHE.putAll(map);
//        } finally {
//            // 释放写锁
//            RCS_LOCK.unlockWrite(stamp);
//        }
//    }
//
//    /**
//     * 将分段任务列表放入缓存
//     *
//     * @param agvId     AGV编号
//     * @param taskPaths 分段任务列表
//     */
//    public static void putTaskSections(String agvId, List<TaskPath> taskPaths) {
//        // 获取写锁
//        long stamp = RCS_LOCK.writeLock();
//        try {
//            TASK_SECTION_CACHE.put(agvId, taskPaths);
//        } finally {
//            // 释放写锁
//            RCS_LOCK.unlockWrite(stamp);
//        }
//    }
//
//    /**
//     * 从缓存中移除指定AGV的所有分段任务
//     *
//     * @param agvId AGV编号
//     * @return 被移除的分段任务列表，如果不存在则返回null
//     */
//    public static List<TaskPath> removeTaskSections(String agvId) {
//        // 获取写锁
//        long stamp = RCS_LOCK.writeLock();
//        try {
//            return TASK_SECTION_CACHE.remove(agvId);
//        } finally {
//            // 释放写锁
//            RCS_LOCK.unlockWrite(stamp);
//        }
//    }
//
//    /**
//     * 从缓存中移除首条AGV的分段任务
//     *
//     * @param agvId AGV编号
//     * @return 被移除的首条分段任务，如果不存在则返回null
//     */
//    public static TaskPath removeFirst(String agvId) {
//        // 获取写锁
//        long stamp = RCS_LOCK.writeLock();
//        try {
//            List<TaskPath> taskPaths = TASK_SECTION_CACHE.get(agvId);
//            if (taskPaths.isEmpty()) {
//                return null;
//            }
//            TaskPath taskPath = TASK_SECTION_CACHE.get(agvId).removeFirst();
//            if (TASK_SECTION_CACHE.get(agvId).isEmpty()) {
//                TASK_SECTION_CACHE.remove(agvId);
//            }
//            return taskPath;
//        } finally {
//            // 释放写锁
//            RCS_LOCK.unlockWrite(stamp);
//        }
//    }
//
//    /**
//     * 获取新的分段任务
//     * <p>
//     * 首次进行任务拆分逻辑的运算先进行电梯任务拆分，再进行风淋室任务拆分，如果拆分失败，则直接使用任务的起终点
//     * 第二次再调用则直接返回该AGV的第一条任务分段数据
//     *
//     * @param rcsTask 任务
//     * @param agv     AGV
//     * @return 任务拆分结果
//     */
//    public static TaskPath getNewTaskSection(RcsTask rcsTask, RcsAgv agv) {
//        List<TaskPath> taskPaths = new ArrayList<>();
//        //获取AGV编号
//        String agvId = agv.getAgvId();
//
//        // 获取AGV编号对应的分段任务
//        List<TaskPath> tempPaths = getTaskSections(agvId);
//        //如果当前AGV已经存在分段任务，那么直接返回第一条数据
//        if (!tempPaths.isEmpty()) {
//            RcsLog.algorithmLog.info(RcsLog.formatTemplate("AGV编号[" + agvId + "]已存在分段任务，直接返回第一条数据"));
//            return tempPaths.getFirst();
//        }
//
//        //任务类型 0搬运任务 1充电任务 2停靠任务 3临时任务 4避让任务
//        Integer taskType = rcsTask.getTaskType();
//        // 获取任务起点
//        RcsPoint taskOrigin = RcsUtils.commonPointParse(rcsTask.getOrigin());
//        if (taskOrigin == null) {
//            RcsLog.consoleLog.error(RcsLog.formatTemplate(rcsTask.getTaskCode() + "任务起点[" + rcsTask.getOrigin() + "]获取不到点位数据"));
//            RcsLog.algorithmLog.error(RcsLog.formatTemplate(rcsTask.getTaskCode() + "任务起点[" + rcsTask.getOrigin() + "]获取不到点位数据"));
//            // 添加告警
//            AlarmManage.triggerAlarm(agvId, rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getOrigin(), "rcs");
//            return null;
//        }
//
//        // 获取任务终点
//        RcsPoint taskDestin = RcsUtils.commonPointParse(rcsTask.getDestin());
//        if (taskDestin == null) {
//            RcsLog.consoleLog.error(RcsLog.formatTemplate(rcsTask.getTaskCode() + "任务终点[" + rcsTask.getDestin() + "]获取不到点位数据"));
//            RcsLog.algorithmLog.error(RcsLog.formatTemplate(rcsTask.getTaskCode() + "任务终点[" + rcsTask.getDestin() + "]获取不到点位数据"));
//            // 添加告警
//            AlarmManage.triggerAlarm(agvId, rcsTask.getTaskGroup(), rcsTask.getTaskCode(), AlarmCodeEnum.E11001, rcsTask.getDestin(), "rcs");
//            return null;
//        }
//
//        //判断任务类型
//        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, taskType)) {
//            // 获取AGV当前点位
//            RcsPoint rcsPoint = RcsPointCache.getRcsPoint(agv.getMapId(), agv.getPointId());
//            //只有搬运任务才要前往任务起点
//            taskPaths.addAll(sectionElevatorTask(agvId, rcsTask, rcsPoint, taskOrigin, SubTaskTypeEnum.ORIGIN));
//            //拆分到终点的任务
//            taskPaths.addAll(sectionElevatorTask(agvId, rcsTask, taskOrigin, taskDestin, SubTaskTypeEnum.DESTIN));
//        } else {
//            //拆分到终点的任务
//            taskPaths.addAll(sectionElevatorTask(agvId, rcsTask, taskOrigin, taskDestin, SubTaskTypeEnum.DESTIN));
//        }
//
//        //如果任务进行电梯拆分后集合为空，则使用通用任务拆分
//        if (taskPaths.isEmpty()) {
//            RcsLog.taskLog.info(RcsLog.formatTemplate(agvId, "如果任务进行电梯拆分后集合为空，则使用通用任务拆分"));
//            RcsLog.algorithmLog.info(RcsLog.formatTemplate(agvId, "如果任务进行电梯拆分后集合为空，则使用通用任务拆分"));
//            taskPaths.addAll(sectionCommonTask(agv, rcsTask, taskOrigin, taskDestin));
//        }
//
//        //风淋室分段任务拆分
//        taskPaths = sectionAirShowersTask(rcsTask, taskPaths);
//
//        //如果任务进行风淋室拆分后集合为空，则使用通用任务拆分
//        if (taskPaths.isEmpty()) {
//            RcsLog.taskLog.info(RcsLog.formatTemplate(agvId, "如果任务进行风淋室拆分后集合为空，则使用通用任务拆分"));
//            RcsLog.algorithmLog.info(RcsLog.formatTemplate(agvId, "如果任务进行电梯拆分后集合为空，则使用通用任务拆分"));
//            taskPaths.addAll(sectionCommonTask(agv, rcsTask, taskOrigin, taskDestin));
//        }
//        //如果任务不为空，并且是搬运任务，则进行输送线分段任务拆分
//        if (!taskPaths.isEmpty() && TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, taskType)) {
//            taskPaths = sectionConveyorLineTask(rcsTask, taskPaths);
//        }
//
//        //处理任务数据
//        int count = 1;
//        for (TaskPath taskPath : taskPaths) {
//            taskPath.setTaskId(rcsTask.getId());
//            taskPath.setTaskGroup(rcsTask.getTaskGroup());
//            taskPath.setTaskCode(rcsTask.getTaskCode());
//            taskPath.setSubTaskNo(count++);
//        }
//
//        //将任务分段缓存
//        putTaskSections(agvId, taskPaths);
//        //返回第一条任务
//        return taskPaths.getFirst();
//    }
//
//    /**
//     * 处理输送线对接逻辑
//     *
//     * @param taskPaths 任务分段数据
//     */
//    private static List<TaskPath> sectionConveyorLineTask(RcsTask rcsTask, List<TaskPath> taskPaths) {
//        List<TaskPath> tempPaths = new ArrayList<>();
//        for (TaskPath taskPath : taskPaths) {
//            // 获取AGV编号
//            String agvId = taskPath.getAgvId();
//            //子任务类型 0前往起点任务 1前往终点任务
//            int taskType = taskPath.getSubTaskType();
//            //获取任务起点
//            RcsPoint taskOrigin = taskPath.getTaskOrigin();
//            //获取任务终点
//            RcsPoint taskDestin = taskPath.getTaskDestin();
//
//            //获取指定点位的输送线配置
//            //这里为什么使用任务终点呢？因为任务已经被拆分了，所以这里使用任务终点
//            ConveyorLineEntity conveyorLine = InteractionYaml.getConveyorLineByRelevancyPoint(taskDestin);
//            //只有前往起终点的任务才可能需要对接输送线
//            if (conveyorLine != null && (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType) || SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, taskType))) {
//                if (conveyorLine.getDockingPoint() == null) {
//                    throw new RuntimeException("输送线【" + conveyorLine.getCode() + "】对接点配置错误");
//                }
//                //获取输送线对接点点位
//                RcsPoint dockingPoint = RcsUtils.commonPointParse(conveyorLine.getDockingPoint());
//
//                //取货模式 0任务下发前询问是否可取货 1在对接点询问是否可取货
//                int pickupMode = conveyorLine.getPickupMode();
//                //卸货模式 0任务下发前询问是否可卸货 1在对接点询问是否可卸货
//                int unloadMode = conveyorLine.getUnloadMode();
//                // 仅当任务类型和对应的取货/卸货模式匹配时，才进行输送线对接任务拆分
////                if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType) && pickupMode == 1 ||
////                        SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, taskType) && unloadMode == 1) {
//
//                //获取AGV信息
//                RcsAgv agv = RcsAgvCache.getRcsAgvByCode(agvId);
//                if (agv != null) {
//                    //获取AGV当前点位
//                    RcsPoint currentPoint = RcsPointCache.getRcsPoint(agv.getMapId(), agv.getPointId());
//                    //如果AGV当前点位等于输送线对接点位，则进行输送线对接任务拆分
//                    if (!dockingPoint.equals(currentPoint)) {
//                        //创建从任务起点到输送线对接点的AGV任务
//                        TaskPath taskPath1 = new TaskPath();
//                        taskPath1.setSubTaskType(taskType);
//                        taskPath1.setAgvId(taskPath.getAgvId());
//                        taskPath1.setTaskOrigin(taskOrigin);
//                        taskPath1.setTaskDestin(dockingPoint);
//                        //设置任务动作参数
//                        Map<String, Integer> taskPath1Params = TaskRegexUtils.getDestinActionParameter(agvId, conveyorLine.getDockingPoint(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                        taskPath1.setTaskAction(taskPath1Params.get("action"));
//                        taskPath1.setTaskParameter(taskPath1Params.get("parameter"));
//
//                        //添加任务到集合
//                        tempPaths.add(taskPath1);
//                    }
//                } else {
//                    throw new RcsException("AGV" + agvId + "不存在");
//                }
//
//                //创建从输送线对接点到任务终点的对接任务
//                TaskPath taskPath2 = new TaskPath();
//                taskPath2.setSubTaskType(taskType);
//                taskPath2.setAgvId(taskPath.getAgvId());
//                taskPath2.setTaskOrigin(dockingPoint);
//                taskPath2.setTaskDestin(taskDestin);
//
//                DockDevice dockDevice = new DockDevice();
//                dockDevice.setEquipmentId(conveyorLine.getCode());
//                dockDevice.setDockType(DockTaskTypeEnum.CONVEYORLINE.code);
//                dockDevice.setDockingAdvance(conveyorLine.getDockingAdvance());
//                dockDevice.setStartPoint(dockingPoint);
//                dockDevice.setEndPoint(taskDestin);
//                taskPath2.setDockDevice(dockDevice);
//
//                //判断子任务类型 并 设置任务动作参数
//                if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType)) {
//                    //最后任务 0非最终 1起点最终 2终点最终
//                    taskPath2.setFinallyTask(1);
//                    Map<String, Integer> originParams = TaskRegexUtils.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                    taskPath2.setTaskAction(originParams.get("action"));
//                    taskPath2.setTaskParameter(originParams.get("parameter"));
//                } else {
//                    //最后任务 0非最终 1起点最终 2终点最终
//                    taskPath2.setFinallyTask(2);
//                    Map<String, Integer> destinParams = TaskRegexUtils.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                    taskPath2.setTaskAction(destinParams.get("action"));
//                    taskPath2.setTaskParameter(destinParams.get("parameter"));
//                }
//
//                //添加任务到集合
//                tempPaths.add(taskPath2);
////                } else {
////                    //虽然获取到了输送线配置，但是取货模式和卸货模式都是0，所以不需要对接
////                    tempPaths.add(taskPath);
////                }
//            } else {
//                //如果不是搬运任务，则直接添加到任务集合中
//                tempPaths.add(taskPath);
//            }
//        }
//        return tempPaths;
//    }
//
//    /**
//     * 从任务集合中获取一条分段任务
//     * <p>
//     * 该方法不会进行任务拆分的逻辑，只是从缓存中获取第一条数据，如果集合为空，则返回null
//     *
//     * @param agvId AGV编号
//     * @return 任务分段数据
//     */
//    public static TaskPath getTaskSection(String agvId) {
//        List<TaskPath> taskPaths = getTaskSections(agvId);
//        if (!taskPaths.isEmpty()) {
//            return taskPaths.getFirst();
//        }
//        return null;
//    }
//
//    /**
//     * 通用任务拆分
//     *
//     * @param agv        AGV
//     * @param rcsTask    SQL任务
//     * @param taskOrigin 任务起点
//     * @param taskDestin 任务终点
//     * @return 任务拆分集合
//     */
//    private static List<TaskPath> sectionCommonTask(RcsAgv agv, RcsTask rcsTask, RcsPoint taskOrigin, RcsPoint taskDestin) {
//        // 初始化任务路径列表
//        List<TaskPath> result = new ArrayList<>(2);
//        // 获取AGV标识
//        String agvId = agv.getAgvId();
//        // 从缓存中获取AGV所在位置的RcsPoint对象
//        RcsPoint rcsPoint = RcsPointCache.getRcsPoint(agv.getMapId(), agv.getPointId());
//
//        // 判断任务类型是否为搬运任务
//        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, rcsTask.getTaskType())) {
//            // 如果任务起点与AGV当前位置不一致
//            if (!taskOrigin.equals(rcsPoint)) {
//                // 添加起点任务路径
//                Map<String, Integer> originParams = TaskRegexUtils.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                result.add(createTaskPath(agvId, SubTaskTypeEnum.ORIGIN, rcsPoint, taskOrigin, originParams));
//
//                // 添加终点任务路径
//                Map<String, Integer> destinParams = TaskRegexUtils.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                result.add(createTaskPath(agvId, SubTaskTypeEnum.DESTIN, taskOrigin, taskDestin, destinParams));
//            } else {
//                // 如果任务起点与AGV当前位置一致，直接添加终点任务路径
//                Map<String, Integer> destinParams = TaskRegexUtils.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                result.add(createTaskPath(agvId, SubTaskTypeEnum.DESTIN, taskOrigin, taskDestin, destinParams));
//            }
//        } else {
//            // 如果任务类型不是搬运任务，添加终点任务路径
//            Map<String, Integer> destinParams = TaskRegexUtils.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//            result.add(createTaskPath(agvId, SubTaskTypeEnum.DESTIN, rcsPoint, taskDestin, destinParams));
//        }
//
//        return result;
//    }
//
//    /**
//     * 创建任务路径的通用方法
//     *
//     * @param agvId        AGV编号
//     * @param subTaskType  子任务类型
//     * @param origin       起点
//     * @param destin       终点
//     * @param actionParams 动作参数
//     * @return 任务路径
//     */
//    private static TaskPath createTaskPath(String agvId, SubTaskTypeEnum subTaskType, RcsPoint origin, RcsPoint destin, Map<String, Integer> actionParams) {
//        TaskPath taskPath = new TaskPath();
//        taskPath.setSubTaskType(subTaskType.code);
//        if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, subTaskType.code)) {
//            //最后任务 0非最终 1起点最终 2终点最终
//            taskPath.setFinallyTask(1);
//        } else if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, subTaskType.code)) {
//            //最后任务 0非最终 1起点最终 2终点最终
//            taskPath.setFinallyTask(2);
//        }
//        taskPath.setAgvId(agvId);
//        taskPath.setTaskOrigin(origin);
//        taskPath.setTaskDestin(destin);
//        taskPath.setTaskAction(actionParams.get("action"));
//        taskPath.setTaskParameter(actionParams.get("parameter"));
//        return taskPath;
//    }
//
//
//    /**
//     * 风淋室任务拆分
//     *
//     * @param taskPaths 任务拆分集合
//     * @return 任务拆分集合
//     */
//    private static List<TaskPath> sectionAirShowersTask(RcsTask rcsTask, List<TaskPath> taskPaths) {
//        List<TaskPath> tempPaths = new ArrayList<>();
//        for (TaskPath taskPath : taskPaths) {
//            String agvId = taskPath.getAgvId();
//            //子任务类型 0前往起点任务 1前往终点任务
//            int taskType = taskPath.getSubTaskType();
//            //只有前往起终点的任务才可能需要穿越风淋室
//            if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, taskType) || SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, taskType)) {
//                //获取风淋室配置
//                List<AirShowerEntity> airShowers = InteractionYaml.getAirShowers();
//                if (airShowers.isEmpty()) {
//                    return tempPaths;
//                }
//
//                SubTaskTypeEnum tempEnum = SubTaskTypeEnum.fromEnum(taskType);
//                RcsPoint taskOrigin = taskPath.getTaskOrigin();
//                RcsPoint taskDestin = taskPath.getTaskDestin();
//                //获取规划路径
//                RouteResult routeResult = AlgorithmDecision.aStarSearch(taskPath.getAgvId(), taskOrigin, taskDestin, false);
//                List<RcsPoint> paths = routeResult.getPaths();
//                if (paths.isEmpty()) {
//                    throw new RuntimeException("风淋室拆分任务时规划路径为空");
//                }
//
//                //遍历风淋室配置
//                for (AirShowerEntity airShower : airShowers) {
//                    if (airShower == null || airShower.getPoint() == null) {
//                        RcsLog.algorithmLog.error(RcsLog.formatTemplate(agvId, StrUtil.format("风淋室配置错误，airShower = {}，airShower.getPoint() = null", airShower)));
//                        continue;
//                    }
//
//                    //获取风淋室点位字符串
//                    String pointStr = airShower.getPoint();
//                    //获取风淋室点位点位
//                    RcsPoint airShowerPoint = RcsUtils.commonPointParse(pointStr);
//                    //获取风淋室点位
//                    int airShowerIndex = paths.indexOf(airShowerPoint);
//                    //判断路径中是否包含风淋室点位
//                    if (airShowerPoint != null && paths.contains(airShowerPoint)) {
//                        List<String> occupancys = airShower.getOccupancys();
//                        for (String doorPointStr : occupancys) {
//                            String[] split = doorPointStr.split(",");
//                            if (split.length != 2) {
//                                throw new RuntimeException("风淋室的Occupancys属性[" + doorPointStr + "]分割失败");
//                            }
//                            RcsPoint doorPoint = RcsUtils.commonPointParse(split[1]);
//                            if (doorPoint == null) {
//                                throw new RuntimeException("风淋室的Occupancys属性[" + split[1] + "]获取不到点位数据");
//                            }
//
//                            //获取入口门
//                            RcsPoint previousPoint = paths.get(airShowerIndex - 1);
//                            if (previousPoint != null && previousPoint.equals(doorPoint)) {
//                                //路径里有风淋室点位，则进行拆分
//                                List<Map<String, AirShowerInteractionBody>> interactions = airShower.getInteractions();
//                                if (interactions.isEmpty()) {
//                                    throw new RuntimeException("风淋室的Interactions属性获取不到数据");
//                                }
//
//                                for (Map<String, AirShowerInteractionBody> interactionsBody : interactions) {
//                                    //获取风淋室交互点位
//                                    AirShowerInteractionBody airShowerInteractionBody = interactionsBody.get(split[0]);
//                                    if (airShowerInteractionBody == null) {
//                                        continue;
//                                    }
//
//                                    InteractionBody start = airShowerInteractionBody.getStart();
//                                    InteractionBody end = airShowerInteractionBody.getEnd();
//                                    if (start == null) {
//                                        throw new RuntimeException("风淋室的Interactions属性获取不到start数据");
//                                    }
//                                    if (end == null) {
//                                        throw new RuntimeException("风淋室的Interactions属性获取不到end数据");
//                                    }
//                                    RcsPoint startPiont = RcsUtils.commonPointParse(start.getPoint());
//                                    RcsPoint endPiont = RcsUtils.commonPointParse(end.getPoint());
//                                    if (startPiont == null) {
//                                        throw new RuntimeException("风淋室的Interactions属性[" + start.getPoint() + "]获取不到点位数据");
//                                    }
//                                    if (endPiont == null) {
//                                        throw new RuntimeException("风淋室的Interactions属性[" + end.getPoint() + "]获取不到点位数据");
//                                    }
//
//                                    //创建从任务起点到风淋室对接起点的AGV任务
//                                    TaskPath taskPath1 = new TaskPath();
//                                    taskPath1.setSubTaskType(tempEnum.code);
//                                    taskPath1.setAgvId(taskPath.getAgvId());
//                                    taskPath1.setTaskOrigin(taskOrigin);
//                                    taskPath1.setTaskDestin(startPiont);
//                                    //设置任务动作参数
//                                    Map<String, Integer> destinParams1 = TaskRegexUtils.getDestinActionParameter(agvId, RcsUtils.commonFormat(startPiont.getMapId(), startPiont.getId()), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
//                                    taskPath1.setTaskAction(destinParams1.get("action"));
//                                    taskPath1.setTaskParameter(destinParams1.get("parameter"));
//
//                                    //创建从风淋室对接起点到风淋室对接终点的对接任务
//                                    TaskPath taskPath2 = new TaskPath();
//                                    taskPath2.setSubTaskType(taskType);
//                                    taskPath2.setAgvId(taskPath.getAgvId());
//                                    taskPath2.setTaskOrigin(startPiont);
//                                    taskPath2.setTaskDestin(endPiont);
//                                    //设置任务动作参数
//                                    Map<String, Integer> destinParams2 = TaskRegexUtils.getDestinActionParameter(agvId, RcsUtils.commonFormat(endPiont.getMapId(), endPiont.getId()), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
//                                    taskPath2.setTaskAction(destinParams2.get("action"));
//                                    taskPath2.setTaskParameter(destinParams2.get("parameter"));
//
//                                    //创建风淋室对接任务
//                                    Map<String, RcsPoint> frontDoors = new HashMap<>(2);
//                                    Map<String, RcsPoint> backDoors = new HashMap<>(2);
//                                    DockDevice dockDevice = new DockDevice();
//                                    start.getDoor().forEach(doorCode -> {
//                                        frontDoors.put(doorCode, doorPoint);
//                                    });
//                                    end.getDoor().forEach(doorCode -> {
//                                        backDoors.put(doorCode, getAirShowerOccupancyPoint(occupancys, doorCode));
//                                    });
//                                    dockDevice.setEquipmentId(airShower.getCode());
//                                    dockDevice.setDockType(DockTaskTypeEnum.AIRSHOWER.code);
//                                    dockDevice.setStartPoint(startPiont);
//                                    dockDevice.setEquipmentPoint(airShowerPoint);
//                                    dockDevice.setEndPoint(endPiont);
//                                    dockDevice.setFrontDoors(frontDoors);
//                                    dockDevice.setBackDoors(backDoors);
//                                    taskPath2.setDockDevice(dockDevice);
//
//                                    //创建从风淋室对接终点到任务终点的AGV任务
//                                    TaskPath taskPath3 = new TaskPath();
//                                    taskPath3.setSubTaskType(tempEnum.code);
//                                    taskPath3.setAgvId(taskPath.getAgvId());
//                                    taskPath3.setTaskOrigin(endPiont);
//                                    taskPath3.setTaskDestin(taskDestin);
//                                    //设置任务动作参数
//                                    if (tempEnum.equals(SubTaskTypeEnum.ORIGIN)) {
//                                        //最后任务 0非最终 1起点最终 2终点最终
//                                        taskPath3.setFinallyTask(1);
//                                        Map<String, Integer> originParams3 = TaskRegexUtils.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                                        taskPath3.setTaskAction(originParams3.get("action"));
//                                        taskPath3.setTaskParameter(originParams3.get("parameter"));
//                                    } else {
//                                        //最后任务 0非最终 1起点最终 2终点最终
//                                        taskPath3.setFinallyTask(2);
//                                        Map<String, Integer> destinParams3 = TaskRegexUtils.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                                        taskPath3.setTaskAction(destinParams3.get("action"));
//                                        taskPath3.setTaskParameter(destinParams3.get("parameter"));
//                                    }
//
//                                    //添加任务到集合
//                                    tempPaths.add(taskPath1);
//                                    tempPaths.add(taskPath2);
//                                    tempPaths.add(taskPath3);
//                                }
//                            } else {
//                                RcsLog.algorithmLog.warn(RcsLog.formatTemplate(agvId, StrUtil.format("进行下一轮寻找，风淋室入口自动门不匹配：previousPoint = {} previousPoint.equals(doorPoint) = {}", previousPoint, (previousPoint != null ? previousPoint.equals(doorPoint) : null))));
//                            }
//                        }
//                    } else {
//                        RcsLog.algorithmLog.warn(RcsLog.formatTemplate(agvId, StrUtil.format("路径中可能不包含风淋室点位：airShowerPoint = {} paths.contains(airShowerPoint) = {}", airShowerPoint, paths.contains(airShowerPoint))));
//                    }
//                }
//            } else {
//                //如果不是搬运任务，则直接添加到任务集合中
//                tempPaths.add(taskPath);
//            }
//            break;
//        }
//        return tempPaths;
//    }
//
//    /**
//     * 获取风淋室对接点位
//     *
//     * @param occupancys 占用点位集合
//     * @param doorCode   门编号
//     * @return 点位
//     */
//    private static RcsPoint getAirShowerOccupancyPoint(List<String> occupancys, String doorCode) {
//        RcsPoint doorPoint = null;
//        for (String occupancy : occupancys) {
//            String[] split = occupancy.split(",");
//            if (doorCode.equalsIgnoreCase(split[0])) {
//                doorPoint = RcsUtils.commonPointParse(split[1]);
//            }
//        }
//        if (doorPoint == null) {
//            throw new RuntimeException("风淋室的occupancys属性里[" + doorCode + "]获取不到点位数据");
//        }
//        return doorPoint;
//    }
//
//    /**
//     * 电梯任务拆分，将数据库的任务进行拆分，方便后续调度进行处理
//     *
//     * @param agvId       AGV编号
//     * @param rcsTask     SQL任务
//     * @param taskOrigin  任务起点
//     * @param taskDestin  任务终点
//     * @param subTaskType 子任务类型
//     * @return 任务拆分集合
//     */
//    private static List<TaskPath> sectionElevatorTask(String agvId, RcsTask rcsTask, RcsPoint taskOrigin, RcsPoint taskDestin, SubTaskTypeEnum subTaskType) {
//        List<TaskPath> result = new ArrayList<>(5);
//        //判断楼层是否一样，如果不一样先考虑跨楼层
//        if (taskOrigin.getMapId() != taskDestin.getMapId()) {
//            //获取最近的电梯
//            ElevatorEntity recentElevatorEntity = InteractionYaml.getRecentElevator(taskOrigin.getFloor(), taskDestin.getFloor());
//
//            // 如果找到最近的电梯
//            if (recentElevatorEntity != null) {
//                //电梯所有电梯门
//                List<String> occupancys = recentElevatorEntity.getOccupancys();
//                // 获取电梯交互点
//                List<Map<String, InteractionsBody>> interactions = recentElevatorEntity.getInteractions();
//                if (interactions.isEmpty()) {
//                    throw new RuntimeException("电梯的Interactions属性获取不到数据");
//                }
//                // 获取第一个传送点
//                TeleportBody firstTeleportBody = recentElevatorEntity.getTeleports().getFirst();
//                if (firstTeleportBody == null) {
//                    throw new RuntimeException("电梯的Teleports属性获取不到数据");
//                }
//                // 获取第一个交互点
//                Map<String, InteractionsBody> firstInteractionsBody = interactions.getFirst();
//                if (firstInteractionsBody.isEmpty()) {
//                    throw new RuntimeException("电梯的Teleports属性获取不到数据");
//                }
//                // 获取指定传送点ID的交互点
//                InteractionsBody interactionsBody = firstInteractionsBody.get(firstTeleportBody.getId());
//                if (interactionsBody != null) {
//                    // 获取对接起点
//                    String startPoint = interactionsBody.getStart().getPoint();
//                    // 获取对接终点
//                    String endPoint = interactionsBody.getEnd().getPoint();
//                    if (startPoint == null) {
//                        throw new RuntimeException("电梯的Interactions属性获取不到start数据");
//                    }
//                    if (endPoint == null) {
//                        throw new RuntimeException("电梯的Interactions属性获取不到end数据");
//                    }
//                    // 获取对接起点
//                    RcsPoint dockingOrigin = RcsUtils.commonPointParse(startPoint);
//                    // 获取对接终点
//                    RcsPoint dockingDestin = RcsUtils.commonPointParse(endPoint);
//                    if (dockingOrigin == null) {
//                        throw new RuntimeException("风淋室的Interactions属性[" + startPoint + "]获取不到点位数据");
//                    }
//                    if (dockingDestin == null) {
//                        throw new RuntimeException("风淋室的Interactions属性[" + endPoint + "]获取不到点位数据");
//                    }
//
//                    //创建从任务起点到电梯对接起点的AGV任务
//                    TaskPath taskPath1 = new TaskPath();
//                    taskPath1.setSubTaskType(subTaskType.code);
//                    taskPath1.setAgvId(agvId);
//                    taskPath1.setTaskOrigin(taskOrigin);
//                    taskPath1.setTaskDestin(dockingOrigin);
//                    //设置任务动作参数
//                    Map<String, Integer> destinParams1 = TaskRegexUtils.getDestinActionParameter(agvId, RcsUtils.commonFormat(dockingOrigin.getMapId(), dockingOrigin.getId()), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
//                    taskPath1.setTaskAction(destinParams1.get("action"));
//                    taskPath1.setTaskParameter(destinParams1.get("parameter"));
//
//                    //创建从电梯对接起点到电梯对接终点的对接任务
//                    TaskPath taskPath2 = new TaskPath();
//                    taskPath2.setSubTaskType(subTaskType.code);
//                    taskPath2.setAgvId(agvId);
//                    taskPath2.setTaskOrigin(dockingOrigin);
//                    taskPath2.setTaskDestin(dockingDestin);
//                    //设置任务动作参数
//                    Map<String, Integer> destinParams2 = TaskRegexUtils.getDestinActionParameter(agvId, RcsUtils.commonFormat(dockingDestin.getMapId(), dockingDestin.getId()), TaskTypeEnum.DOCK.code, rcsTask.getPalletType());
//                    taskPath2.setTaskAction(destinParams2.get("action"));
//                    taskPath2.setTaskParameter(destinParams2.get("parameter"));
//
//                    //创建电梯对接设备
//                    Map<String, RcsPoint> frontDoors = new HashMap<>(2);
//                    Map<String, RcsPoint> backDoors = new HashMap<>(2);
//                    DockDevice dockDevice = new DockDevice();
//                    InteractionBody startBody = interactionsBody.getStart();
//                    startBody.getDoor().forEach(doorCode -> {
//                        for (String occupancy : occupancys) {
//                            //{电梯门编号},{地图编号-点位}
//                            String[] split = occupancy.split(",");
//                            if (doorCode.equalsIgnoreCase(split[0])) {
//                                frontDoors.put(doorCode, RcsUtils.commonPointParse(split[0]));
//                            }
//                        }
//                    });
//                    InteractionBody endBody = interactionsBody.getEnd();
//                    endBody.getDoor().forEach(doorCode -> {
//                        for (String occupancy : occupancys) {
//                            //{电梯门编号},{地图编号-点位}
//                            String[] split = occupancy.split(",");
//                            if (doorCode.equalsIgnoreCase(split[0])) {
//                                backDoors.put(doorCode, RcsUtils.commonPointParse(split[0]));
//                            }
//                        }
//                    });
//
//                    dockDevice.setEquipmentId(recentElevatorEntity.getCode());
//                    dockDevice.setDockType(DockTaskTypeEnum.ELEVATOR.code);
//                    dockDevice.setStartPoint(dockingOrigin);
//                    dockDevice.setEndPoint(dockingDestin);
//                    dockDevice.setFrontDoors(frontDoors);
//                    dockDevice.setBackDoors(backDoors);
//                    taskPath2.setDockDevice(dockDevice);
//
//                    //创建从电梯对接终点到任务终点的AGV任务
//                    TaskPath taskPath3 = new TaskPath();
//                    taskPath3.setSubTaskType(subTaskType.code);
//                    taskPath3.setAgvId(agvId);
//                    taskPath3.setTaskOrigin(dockingDestin);
//                    taskPath3.setTaskDestin(taskDestin);
//                    //设置任务动作参数
//                    if (subTaskType.equals(SubTaskTypeEnum.ORIGIN)) {
//                        //最后任务 0非最终 1起点最终 2终点最终
//                        taskPath3.setFinallyTask(1);
//                        Map<String, Integer> originParams3 = TaskRegexUtils.getOriginActionParameter(agvId, rcsTask.getOrigin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                        taskPath3.setTaskAction(originParams3.get("action"));
//                        taskPath3.setTaskParameter(originParams3.get("parameter"));
//                    } else {
//                        //最后任务 0非最终 1起点最终 2终点最终
//                        taskPath3.setFinallyTask(2);
//                        Map<String, Integer> destinParams3 = TaskRegexUtils.getDestinActionParameter(agvId, rcsTask.getDestin(), rcsTask.getTaskType(), rcsTask.getPalletType());
//                        taskPath3.setTaskAction(destinParams3.get("action"));
//                        taskPath3.setTaskParameter(destinParams3.get("parameter"));
//                    }
//
//                    //添加任务到集合
//                    result.add(taskPath1);
//                    result.add(taskPath2);
//                    result.add(taskPath3);
//                } else {
//                    RcsLog.algorithmLog.error(RcsLog.formatTemplate(agvId, StrUtil.format("传送点：{}，获取不到InteractionsBody的电梯配置", firstTeleportBody.getId())));
//                }
//            } else {
//                RcsLog.algorithmLog.error(RcsLog.formatTemplate(agvId, StrUtil.format("任务起点：{}，终点：{}，获取不到获取最近的电梯配置", taskOrigin.getFloor(), taskDestin.getFloor())));
//            }
//        } else {
//            RcsLog.algorithmLog.info(RcsLog.formatTemplate(agvId, "任务的起点终点楼层一样，调度认为不需要对接电梯"));
//        }
//        return result;
//    }
}
