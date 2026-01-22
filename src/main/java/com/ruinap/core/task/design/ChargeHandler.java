package com.ruinap.core.task.design;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.db.Entity;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.equipment.pojo.RcsChargePile;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.design.filter.ChargePileFilter;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.config.pojo.task.ChargeCommonEntity;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.task.ChargeMatchModeEnum;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.AgvDB;
import com.ruinap.persistence.repository.ChargePeriodDB;
import com.ruinap.persistence.repository.ConfigDB;
import com.ruinap.persistence.repository.TaskDB;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 充电处理
 *
 * @author qianye
 * @create 2025-04-17 10:03
 */
@Component
public class ChargeHandler implements TaskFlowHandler {

    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private ConfigDB configDB;
    @Autowired
    private ChargePeriodDB chargePeriodDB;
    @Autowired
    private TaskDB taskDB;
    @Autowired
    private AgvDB agvDB;
    @Autowired
    private ChargePileFilter chargePileFilter;
    @Autowired
    private TaskPathManager taskPathManager;

    /**
     * RCS_LOCK实例
     */
    private final RcsLock RCS_LOCK = RcsLock.ofReadWrite();

    /**
     * 下一个处理者
     */
    private TaskFlowHandler nextHandler;

    /**
     * 设置下一个处理者
     *
     * @param nextHandler 下一个处理者
     */
    @Override
    public void setNextHandler(TaskFlowHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    /**
     * 处理请求
     * <p>
     * 两种充电模式，可充可不充（假设电量80%-30%），必须充（小于30%）
     * 可充可不充：有任务则不创建充电任务
     * 必须充：不管有无任务必须创建充电任务
     * <p>
     * <p>
     * 上位指定充电：分两种：
     * ①创建一条和搬运任务一样优先级的任务
     * ②创建一条最高优先级的充电任务
     */
    @Override
    public void handleRequest() {
        handleCharge();
        // 委托给下一个处理器执行
        if (nextHandler != null) {
            nextHandler.handleRequest();
        }
    }

    /**
     * 处理充电逻辑
     */
    private void handleCharge() {
        //获取充电桩匹配模式
        Integer chargepileMatchMode = taskYaml.getChargeCommon().getChargepileMatchMode();
        if (ChargeMatchModeEnum.isEnumByCode(ChargeMatchModeEnum.NOTCHARGE, chargepileMatchMode)) {
            //不充电则直接结束
            return;
        }

        // 获取空闲AGV集合
        Map<String, RcsAgv> rcsAgvMap = agvManager.getIdleRcsAgvMap();
        AGV_FOR:
        for (Map.Entry<String, RcsAgv> entry : rcsAgvMap.entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();

            //遍历任务集合
            for (RcsTask task : taskManager.taskCache.values()) {
                //任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 97取货运行中 98卸货运行中 99运行中
                Integer taskState = task.getTaskState();
                // 设备编码
                String equipmentCode = task.getEquipmentCode();
                if (taskState > 0 && rcsAgv.getAgvId().equalsIgnoreCase(equipmentCode)) {
                    //如果有充电任务直接跳过
                    if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CHARGE, task.getTaskType())) {
                        continue AGV_FOR;
                    }
                }
            }

            boolean flag = false;
            //优先级
            int taskPriority = -1;
            //获取AGV充电配置
            ChargeCommonEntity chargeCommon = taskYaml.getChargeCommon();
            //获取AGV当前电量
            Integer currentBattery = rcsAgv.getBattery();
            //充电信号 0正常 1高优先级信号 2低优先级信号
            Integer chargeSignal = rcsAgv.getChargeSignal();
            //AGV最低工作电量
            Integer lowestWorkPower = chargeCommon.getLowestWorkPower();

            //判断系统是否处于结束工作状态
            boolean isSystemLaidOff = configDB.getWorkState().equals(0);
            //判断AGV电量是否低于下班AGV允许充电电量
            boolean isBatteryBelowLaidOffChargeThreshold = currentBattery <= chargeCommon.getAllowChargePowerLaidoff();
            boolean isSystemLaidOffAndBatteryLow = isSystemLaidOff && isBatteryBelowLaidOffChargeThreshold;
            if (chargeSignal == 1 || currentBattery.compareTo(lowestWorkPower) <= 0 || isSystemLaidOffAndBatteryLow) {
                //高优先级充电任务触发条件：
                // 1.充电信号为高优先级信号
                // 2.AGV电量低于AGV允许充电电量
                // 3.系统处于下班状态且AGV电量低于下班AGV允许充电电量
                taskPriority = 99;
                flag = true;
                RcsLog.algorithmLog.warn("AGV 触发高优先级充电任务，chargeSignal = {}, currentBattery.compareTo(lowestWorkPower) <= 0 = {}, isSystemLaidOffAndBatteryLow = {}", chargeSignal, (currentBattery.compareTo(lowestWorkPower) <= 0), isSystemLaidOffAndBatteryLow);
            } else {
                //低优先级充电任务触发条件：
                // 1.无分段任务且无任务路径
                // 2.1.充电信号为低优先级信号
                // 2.2.AGV电量低于AGV允许充电电量
                boolean isLowPrioritySignal = chargeSignal == 2;
                if (isLowPrioritySignal) {
                    flag = true;
                    RcsLog.algorithmLog.warn("AGV 触发低优先级充电任务，isLowPrioritySignal = {}", isLowPrioritySignal);
                } else {
                    //遍历任务集合
                    for (RcsTask task : taskManager.taskCache.values()) {
                        //任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 97取货运行中 98卸货运行中 99运行中
                        Integer taskState = task.getTaskState();
                        // 设备编码
                        String equipmentCode = task.getEquipmentCode();
                        if (taskState > 0 && rcsAgv.getAgvId().equalsIgnoreCase(equipmentCode)) {
                            continue AGV_FOR;
                        }
                    }

                    //获取分段任务
//                    List<TaskPath> taskSections = TaskSectionManage.getTaskSections(rcsAgv.getAgvId());
                    //获取任务路径
                    List<TaskPath> taskPaths = taskPathManager.get(rcsAgv.getAgvId());
                    //判断AGV是否空闲
                    boolean isAgvIdle = agvManager.getRcsAgvIdle(rcsAgv) != null;
                    //判断AGV是否无任务
//                    boolean hasNoTask = taskSections.isEmpty() && taskPaths.isEmpty();
                    //判断AGV电量是否低于允许充电电量
                    boolean isBatteryBelowNormalChargeThreshold = currentBattery <= chargeCommon.getAllowChargePower();
                    //判断AGV是否空闲且无任务且电量是否低于允许充电电量
//                    boolean shouldCreateChargeTask = isAgvIdle && hasNoTask && isBatteryBelowNormalChargeThreshold;
//                    if (shouldCreateChargeTask) {
//                        flag = true;
//                        RcsLog.algorithmLog.warn("AGV 触发低优先级充电任务，shouldCreateChargeTask = {}", shouldCreateChargeTask);
//                    }
                }

                //如果不满足充电条件，则充电模式是否自定义并且AGV电量是否低于90
                if (!flag && !chargeCommon.getChargepileMatchMode().equals(-2) && rcsAgv.getBattery().compareTo(chargeCommon.getAllowChargePowerTimePeriod()) <= 0) {
                    // 使用Hutool的DateUtil获取小时分钟数据
                    Date now = new Date();
                    int currentHour = DateUtil.hour(now, true);
                    int currentMinute = DateUtil.minute(now);
                    int currentTotalMinutes = currentHour * 60 + currentMinute;

                    // 遍历map中的时间段
                    if (!flag) {
                        try {
                            List<Entity> entities = chargePeriodDB.selectChargePeriodList(new Entity().set("flag", 1));

                            for (Entity entity : entities) {
                                // 数据库取出的字符串，例如 "12:20:00"
                                String startDateStr = entity.getStr("start");
                                String endDateStr = entity.getStr("end");

                                // 使用 Hutool 解析时间字符串
                                DateTime startDt = DateUtil.parse(startDateStr);
                                DateTime endDt = DateUtil.parse(endDateStr);

                                // 计算配置的开始和结束时间的“总分钟数”
                                int startTotalMinutes = DateUtil.hour(startDt, true) * 60 + DateUtil.minute(startDt);
                                int endTotalMinutes = DateUtil.hour(endDt, true) * 60 + DateUtil.minute(endDt);

                                // 3. 比较逻辑：当前分钟数是否在 [开始, 结束) 区间内
                                // 包含开始时间，通常不包含结束时间（看具体业务需求，这里写的是 < end，即左闭右开）
                                if (currentTotalMinutes >= startTotalMinutes && currentTotalMinutes < endTotalMinutes) {
                                    flag = true;
                                    RcsLog.algorithmLog.warn("AGV 触发低优先级充电任务，currentHour >= startHour && currentHour < endHour = {}", true);
                                    break;
                                }
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if (!flag) {
                        ChargeCommonEntity chargeCommon1 = taskYaml.getChargeCommon();
                        if (chargeCommon1 != null) {
                            Map<Integer, Integer> chargeTimesAgv = chargeCommon1.getChargeTimesAgv();
                            if (chargeTimesAgv != null && !chargeTimesAgv.isEmpty()) {
                                for (Map.Entry<Integer, Integer> inChargeTimeEntry : chargeTimesAgv.entrySet()) {
                                    int startHour = inChargeTimeEntry.getKey();
                                    int endHour = inChargeTimeEntry.getValue();
                                    if (currentHour >= startHour && currentHour < endHour) {
                                        flag = true;
                                        RcsLog.algorithmLog.warn("AGV 触发低优先级充电任务，currentHour >= startHour && currentHour < endHour = {}", true);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (flag) {
                // taskPriority 在上面被修改过，不能直接在 lambda 中引用。
                // 我们定义一个临时的 final 变量来捕获它的最终值。
                final int finalTaskPriority = taskPriority;
                // 获取写锁
                // true 表示需要中断外部循环，false 表示继续
                boolean shouldBreakLoop = RCS_LOCK.supplyInWrite(() -> {
                    try {
                        //获取可用充电桩
                        RcsChargePile chargePile = getAvailableChargePile(rcsAgv);
                        if (chargePile != null) {
                            RcsLog.algorithmLog.info("{}AGV成功分配到充电桩[{}]", rcsAgv.getAgvId(), chargePile.getCode());
                            RcsLog.consoleLog.info("{}AGV成功分配到充电桩[{}]", rcsAgv.getAgvId(), chargePile.getCode());
                            RcsPoint rcsPoint = mapManager.getPointByAlias(chargePile.getPointId());
                            if (rcsPoint == null) {
                                RcsLog.consoleLog.info("{} 充电桩[{}]绑定的点位[{}]查询不到", rcsAgv.getAgvId(), chargePile.getCode(), chargePile.getPointId());
                                RcsLog.algorithmLog.info("{} 充电桩[{}]绑定的点位[{}]查询不到", rcsAgv.getAgvId(), chargePile.getCode(), chargePile.getPointId());
                                alarmManager.triggerAlarm(chargePile.getCode(), AlarmCodeEnum.E11001, chargePile.getPointId(), "rcs");
                                return true;
                            }
                            //获取地图的充电点
                            List<RcsPoint> chargeMap = mapManager.getSnapshot().chargePoints().get(chargePile.getFloor());
                            if (!chargeMap.contains(rcsPoint)) {
                                RcsLog.consoleLog.info("{} 充电桩[{}]绑定的点位[{}]不是地图设置的充电点", rcsAgv.getAgvId(), chargePile.getCode(), chargePile.getPointId());
                                RcsLog.algorithmLog.info("{} 充电桩[{}]绑定的点位[{}]不是地图设置的充电点", rcsAgv.getAgvId(), chargePile.getCode(), chargePile.getPointId());
                                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E11001, chargePile.getPointId(), "rcs");
                                return false;
                            }

                            RcsLog.algorithmLog.info("{} 分配的充电桩[{}]的点位[{}]", chargePile.getCode(), chargePile.getCode(), rcsPoint);
                            //创建充电任务
                            Entity entity = new Entity();
                            String taskCodeKey = configDB.taskCodeKey();
                            entity.setIgnoreNull("task_code", taskCodeKey);
                            entity.setIgnoreNull("task_type", TaskTypeEnum.CHARGE.code);
                            entity.setIgnoreNull("equipment_code", rcsAgv.getAgvId());
                            entity.setIgnoreNull("origin_floor", rcsAgv.getMapId());
                            entity.setIgnoreNull("origin", taskManager.formatPoint(rcsAgv.getMapId(), rcsAgv.getPointId()));
                            entity.setIgnoreNull("destin_floor", chargePile.getFloor());
                            entity.setIgnoreNull("destin_area", chargePile.getArea());
                            entity.setIgnoreNull("destin", taskManager.formatPoint(rcsPoint.getMapId(), rcsPoint.getId()));
                            entity.setIgnoreNull("task_priority", finalTaskPriority);
                            entity.setIgnoreNull("task_source", "rcs");

                            //创建充电任务
                            taskDB.createTask(entity);
                            //查询数据库创建的充电任务
                            Entity taskEntity = taskDB.queryTask(new Entity().set("task_code", taskCodeKey));
                            //将任务添加到任务缓存
                            taskManager.taskCache.putIfAbsent(taskCodeKey, taskEntity.toBean(RcsTask.class));
                            //更新点位选择占用
                            mapManager.addOccupyType(rcsAgv.getAgvId(), rcsPoint, PointOccupyTypeEnum.CHOOSE);
                            //判断AGV是否触发充电信号
                            if (chargeSignal > 0) {
                                //重置AGV充电信号
                                agvDB.updateAgvChargeSignal(rcsAgv, 0);
                            }
                        } else {
                            RcsLog.algorithmLog.error("{} AGV电量[{}]%需要进行充电，但没有分配到可用的充电桩", rcsAgv.getAgvId(), rcsAgv.getBattery());
                        }

                        return false;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                // 在锁释放后，在外部控制流中执行跳转
                if (shouldBreakLoop) {
                    break;
                }
            }
        }
    }

    /**
     * 获取所有可用充电桩
     *
     * @param rcsAgv AGV数据
     * @return 充电桩
     */
    private RcsChargePile getAvailableChargePile(RcsAgv rcsAgv) {
        RcsChargePile chargePile = null;
        //获取充电桩匹配模式 -2不充电 -1自定义 0距离匹配 1绑定匹配 2类型匹配 3区域匹配 4楼层匹配
        Integer chargepileMatchMode = taskYaml.getChargeCommon().getChargepileMatchMode();
        RcsLog.algorithmLog.error("{} 充电桩匹配模式：{}", rcsAgv.getAgvId(), ChargeMatchModeEnum.fromEnum(chargepileMatchMode));
        switch (chargepileMatchMode) {
            case -2:
                //不充电

                break;
            case -1:
                //自定义
                chargePile = chargePileFilter.filterChargePileByCustomization(rcsAgv);
                break;
            case 1:
                //绑定匹配
                chargePile = chargePileFilter.filterChargePileByBinding(rcsAgv);
                break;
            case 2:
                //类型匹配
                chargePile = chargePileFilter.filterChargePileByType(rcsAgv);
                break;
            case 3:
                //区域匹配
                chargePile = chargePileFilter.filterChargePileByArea(rcsAgv);
                break;
            case 4:
                //楼层匹配
                chargePile = chargePileFilter.filterChargePileByFloor(rcsAgv);
                break;
            case 5:
                //时间段匹配
                chargePile = chargePileFilter.filterChargePileByTimes(rcsAgv);
                break;
            case 0:
            default:
                //距离匹配
                chargePile = chargePileFilter.filterChargePileByDistance(rcsAgv);
                break;
        }

        return chargePile;
    }
}
