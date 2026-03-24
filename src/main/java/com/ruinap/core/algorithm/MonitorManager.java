package com.ruinap.core.algorithm;

import cn.hutool.db.Entity;
import com.ruinap.core.business.AgvSuggestionManager;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.AgvTask;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.structure.TaskSectionManager;
import com.ruinap.infra.async.AsyncService;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.enums.agv.*;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.task.PlanStateEnum;
import com.ruinap.infra.enums.task.TaskActionEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.TaskDB;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 调度监控管理类
 *
 * @author qianye
 * @create 2025-03-24 10:40
 */
@Service
public class MonitorManager {

    @Autowired
    private AgvManager agvManager;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private AgvSuggestionManager agvSuggestionManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AsyncService asyncService;
    @Autowired
    private TaskSectionManager taskSectionManager;
    @Autowired
    private TaskPathManager taskPathManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private TaskDB taskDB;
    @Autowired
    private CoreYaml coreYaml;

    /**
     * 任务监控
     */
    public void monitorTaskPath() {
        //遍历任务路径数据
        agvManager.getRcsAgvMap().forEach((key, rcsAgv) -> {
            //基本状态信息
            List<String> strings = agvStateInfo(rcsAgv);
            if (!strings.isEmpty()) {
                agvSuggestionManager.addSuggestion(rcsAgv.getAgvId(), strings);
            }

            //雷达状态信息
            List<String> radarCheck = radarStateInfo(rcsAgv);
            if (!radarCheck.isEmpty()) {
                agvSuggestionManager.addSuggestion(rcsAgv.getAgvId(), radarCheck);
            }

            //任务监控
            agvTaskMonitor(rcsAgv);
        });
    }

    /**
     * 基本状态信息
     *
     * @param rcsAgv AGV
     * @return 结果，true为正常，false为不正常
     */
    private List<String> agvStateInfo(RcsAgv rcsAgv) {
        List<String> result = new ArrayList<>();
        if (rcsAgv != null) {
            //AGV状态 -1离线 0待命 1自动行走 2自动动作 3充电中 10暂停 11等待中 12地图切换中
            Integer agvState = rcsAgv.getAgvState();
            if (AgvStateEnum.isEnumByCode(AgvStateEnum.OFFLINE, agvState)) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10002, "rcs");
                RcsLog.sysLog.error("{} AGV离线", rcsAgv.getAgvId());
                result.add("AGV离线");
            }
            if (AgvStateEnum.isEnumByCode(AgvStateEnum.PAUSE, agvState)) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10005, "rcs");
                RcsLog.consoleLog.error("{} AGV暂停", rcsAgv.getAgvId());
                result.add("AGV暂停");
            }

            Integer slamCov = rcsAgv.getSlamCov();
            if (slamCov != null && slamCov > 18) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10006, "rcs");
                RcsLog.sysLog.error("{} AGV协方差过大，可能已丢点位", rcsAgv.getAgvId());
                result.add("AGV协方差过大，可能已丢点位");
            }
            if (slamCov != null && slamCov < 0) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10006, "rcs");
                RcsLog.sysLog.error("{} AGV协方差过小，存在异常", rcsAgv.getAgvId());
                result.add("AGV协方差过小，存在异常");
            }

            Integer estopState = rcsAgv.getEstopState();
            if (AgvEstopEnum.isEstop(estopState)) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10007, "rcs");
                RcsLog.consoleLog.error("{} AGV急停", rcsAgv.getAgvId());
                result.add("AGV急停");
            }

            //AGV模式 0手动 1自动
            Integer agvMode = rcsAgv.getAgvMode();
            if (AgvModeEnum.isManual(agvMode)) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10008, "rcs");
                RcsLog.consoleLog.error("{} AGV模式手动", rcsAgv.getAgvId());
                result.add("AGV模式手动");
            }

            //获取当前点位和所在区域
            if (rcsAgv.getPointId() == null || rcsAgv.getPointId() < 0) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10003, "rcs");
                RcsLog.consoleLog.error("{} AGV不在点位上", rcsAgv.getAgvId());
                result.add("AGV不在点位上");
            } else {
                RcsPoint agvPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
                if (agvPoint == null) {
                    alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10003, "rcs");
                    RcsLog.consoleLog.error("{} AGV不在点位上", rcsAgv.getAgvId());
                    result.add("AGV不在点位上");
                } else {
                    RcsLog.consoleLog.info("{} AGV当前点位：{}，车体状态：{}", rcsAgv.getAgvId(), mapManager.getRcsPoint(agvPoint.getMapId(), agvPoint.getId()), AgvStateEnum.fromEnum(rcsAgv.getAgvState()));
                }
            }

            //获取AGV错误信息
            String agvErrMsg = rcsAgv.getAgvErrMsg();
            if (agvErrMsg != null && !agvErrMsg.isEmpty()) {
                alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10009, agvErrMsg, "rcs");
                RcsLog.consoleLog.error("{} AGV错误信息：{}", rcsAgv.getAgvId(), agvErrMsg);
                RcsLog.sysLog.error("{} AGV错误信息：{}", rcsAgv.getAgvId(), agvErrMsg);
                result.add("AGV错误信息：" + agvErrMsg);
            }

            //AGV重启和取消检查
            checkAgvRestartOrCancel(rcsAgv);

            //AGV点位检查
            checkAgvPoint(rcsAgv);
        }

        return result;
    }

    /**
     * 雷达状态信息
     *
     * @param rcsAgv AGV
     * @return 结果，true为正常，false为不正常
     */
    private List<String> radarStateInfo(RcsAgv rcsAgv) {
        List<String> result = new ArrayList<>();

        //雷达状态 null无信号 0不触发 1减速 2停车
        //前避障
        Integer frontArea = rcsAgv.getFrontArea();
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.NULL, frontArea)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10010, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV前避障无信号"));
//            result.add("AGV前避障无信号");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.SLOWDOWN, frontArea)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10011, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV前避障减速"));
//            result.add("AGV前避障减速");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.STOP, frontArea)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10012, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV前避障停车"));
//            result.add("AGV前避障停车");
//        }

        //左前避障
        Integer frontLeft = rcsAgv.getFrontLeft();
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.NULL, frontLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10013, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左前避障无信号"));
//            result.add("AGV左前避障无信号");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.SLOWDOWN, frontLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10014, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左前避障减速"));
//            result.add("AGV左前避障减速");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.STOP, frontLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10015, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左前避障停车"));
//            result.add("AGV左前避障停车");
//        }

        //右前避障
        Integer frontRight = rcsAgv.getFrontRight();
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.NULL, frontRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10016, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右前避障无信号"));
//            result.add("AGV右前避障无信号");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.SLOWDOWN, frontRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10017, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右前避障减速"));
//            result.add("AGV右前避障减速");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.STOP, frontRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10018, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右前避障停车"));
//            result.add("AGV右前避障停车");
//        }

        //后避障
        Integer backArea = rcsAgv.getBackArea();
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.NULL, backArea)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10019, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV后避障无信号"));
//            result.add("AGV后避障无信号");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.SLOWDOWN, backArea)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10020, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV后避障减速"));
//            result.add("AGV后避障减速");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.STOP, backArea)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10021, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV后避障停车"));
//            result.add("AGV后避障停车");
//        }

        //左后避障
        Integer backLeft = rcsAgv.getBackLeft();
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.NULL, backLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10022, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左后避障无信号"));
//            result.add("AGV左后避障无信号");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.SLOWDOWN, backLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10023, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左后避障减速"));
//            result.add("AGV左后避障减速");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.STOP, backLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10024, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左后避障停车"));
//            result.add("AGV左后避障停车");
//        }

        //右后避障
        Integer backRight = rcsAgv.getBackRight();
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.NULL, backRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10025, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右后避障无信号"));
//            result.add("AGV右后避障无信号");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.SLOWDOWN, backRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10026, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右后避障减速"));
//            result.add("AGV右后避障减速");
//        }
//        if (AgvRadarEnum.isEnumByCode(AgvRadarEnum.STOP, backRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10027, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右后避障停车"));
//            result.add("AGV右后避障停车");
//        }

        //防撞条状态 null无信号 1不触发
        //前防撞条
        Integer bumpFront = rcsAgv.getBumpFront();
//        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.NULL, bumpFront)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10028, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV前防撞条无信号"));
//            result.add("AGV前防撞条无信号");
//        }
        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.STOP, bumpFront)) {
            alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10029, "rcs");
            RcsLog.sysLog.error("{} AGV前防撞条触发", rcsAgv.getAgvId());
            result.add("AGV前防撞条触发");
        }

        //后防撞条
        Integer bumpBack = rcsAgv.getBumpBack();
//        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.NULL, bumpBack)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10030, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV后防撞条无信号"));
//            result.add("AGV后防撞条无信号");
//        }
        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.STOP, bumpBack)) {
            alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10031, "rcs");
            RcsLog.sysLog.error("{} AGV后防撞条触发", rcsAgv.getAgvId());
            result.add("AGV后防撞条触发");
        }

        //左防撞条
        Integer bumpLeft = rcsAgv.getBumpLeft();
//        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.NULL, bumpLeft)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10032, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV左防撞条无信号"));
//            result.add("AGV左防撞条无信号");
//        }
        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.STOP, bumpLeft)) {
            alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10033, "rcs");
            RcsLog.sysLog.error("{} AGV左防撞条触发", rcsAgv.getAgvId());
            result.add("AGV左防撞条触发");
        }

        //右防撞条
        Integer bumpRight = rcsAgv.getBumpRight();
//        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.NULL, bumpRight)) {
//            AlarmManage.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10034, "rcs");
//            RcsLog.sysLog.error(RcsLog.formatTemplate(rcsAgv.getAgvId(), "AGV右防撞条无信号"));
//            result.add("AGV右防撞条无信号");
//        }
        if (AgvBumpEnum.isEnumByCode(AgvBumpEnum.STOP, bumpRight)) {
            alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10035, "rcs");
            RcsLog.sysLog.error("{} AGV右防撞条触发", rcsAgv.getAgvId());
            result.add("AGV右防撞条触发");
        }

        return result;
    }

    /**
     * 任务监控
     *
     * @param rcsAgv AGV
     * @return 结果，true未发生异常，false发生异常
     */
    private boolean agvTaskMonitor(RcsAgv rcsAgv) {
        boolean flag = true;
        //获取AGV编号
        String agvId = rcsAgv.getAgvId();

        //获取分段任务
        List<TaskPath> taskSections = taskSectionManager.getTaskSections(agvId);
        //获取任务路径
        List<TaskPath> taskPaths = taskPathManager.get(agvId);

        if (taskSections.isEmpty() && taskPaths.isEmpty()) {
            //AGV任务校验
            asyncService.runAsync(() -> agvTaskVerify(rcsAgv)).get();
        }

        if (!taskPaths.isEmpty()) {
            //获取第一条消息
            TaskPath taskPath = taskPaths.getFirst();
            //获取规划状态
            int state = taskPath.getState();
            //判断AGV是否对接设备中，对接设备中则不处理
            if (!PlanStateEnum.isEnumByCode(PlanStateEnum.DOCK_DEVICE, state)) {
                //判断任务是否完成
                if (isTaskFinish(taskPath)) {
                    //任务完成
                    taskPath.setState(PlanStateEnum.FINISH.code);
                } else if (!PlanStateEnum.isEnumByCode(PlanStateEnum.CANCEL, state)) {
                    //判断AGV当前点位是否在系统规划路径上
                    isInPath(taskPath);

                    //判断AGV是否动作中
                    handleInAction(taskPath);

                    //判断是否需要二次路径规划
                    isPlanRouteAgain(taskPath);

                    //判断AGV是否需要从分段任务取数据
                    isNeedTaskSection(taskPath);
                }
            }

            RcsLog.consoleLog.info("{} AGV当前在执行任务数：{}，剩余分段任务数：{}", agvId, taskPaths.size(), taskSections.size());
        }
        return flag;
    }

    /**
     * AGV任务校验，如果AGV有任务但系统无任务则取消车体任务
     *
     * @param rcsAgv AGV
     */
    private void agvTaskVerify(RcsAgv rcsAgv) {
        //判断AGV是否被隔离
        if (!AgvIsolationStateEnum.isEnumByCode(AgvIsolationStateEnum.NORMAL, rcsAgv.getIsolationState())) {
            return;
        }
        //判断AGV是否有任务
        if (!AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState())) {
            return;
        }
        //判断AGV控制权是否调度
        if (!AgvControlEnum.isEnumByCode(AgvControlEnum.RCS, rcsAgv.getAgvControl())) {
            return;
        }

        AgvTask agvTask = agvManager.getAgvTaskCache().getOrDefault(rcsAgv.getAgvId(), null);
        if (agvTask == null) {
            return;
        }

        RcsLog.consoleLog.warn("{} AGV车体有任务，系统无任务，系统取消车体任务", rcsAgv.getAgvId());
        RcsLog.algorithmLog.warn("{} AGV车体有任务，系统无任务，系统取消车体任务", rcsAgv.getAgvId());

        String taskId = agvTask.getTaskId();
        String[] split = taskId.split("-");

        //车体有任务，所以要将AGV车体的任务取消
        TaskPath taskPath = new TaskPath();
        taskPath.setAgvId(rcsAgv.getAgvId());
        taskPath.setTaskCode(split[0]);
        taskPath.setSubTaskNo(Integer.parseInt(split[1]));
        taskPath.setPathCode(1);
        taskPath.setCurrentPlanOrigin(mapManager.getRcsPoint(rcsAgv.getMapId(), agvTask.getPathStartId()));
        taskPath.setCurrentPlanDestin(mapManager.getRcsPoint(rcsAgv.getMapId(), agvTask.getPathEndId()));
//        CancelState.sendAgvCancel(taskPath);
        try {
            taskDB.updateTask(new Entity().set("task_state", AgvTaskStateEnum.CANCEL.code), new Entity().set("task_id", taskId));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 判断AGV是否需要从分段任务取数据
     *
     * @param taskPath 任务路径
     */
    private void isNeedTaskSection(TaskPath taskPath) {
        //判断当前分段任务数量
        List<TaskPath> taskSections = taskSectionManager.getTaskSections(taskPath.getAgvId());
        if (taskSections.isEmpty()) {
            return;
        }
        //判断当前规划路径的任务数量
        if (taskPathManager.get(taskPath.getAgvId()).size() >= 2) {
            return;
        }
        //判断任务终点是否与当前规划路径终点一致
        if (taskPath.getTaskDestin().equals(taskPath.getCurrentPlanDestin()) || (taskPath.getEffectiveRunningPoints().size() <= 3)) {
            TaskPath firstSection = taskSections.getFirst();
            // 只有当分段任务中的第一条任务与当前任务不同时，才需要添加新任务
            if (!firstSection.equals(taskPath)) {
                RcsLog.algorithmLog.info("{} 从分段任务里取出一条任务添加到任务规划集合", taskPath.getAgvId());
                // 添加任务路径
                taskPathManager.put(taskPath.getAgvId(), taskSectionManager.getTaskSection(taskPath.getAgvId()));
            }
        }
    }

    /**
     * 判断是否需要二次路径规划
     *
     * @param taskPath 任务路径
     */
    private void isPlanRouteAgain(TaskPath taskPath) {
        if (PlanStateEnum.isEnumByCode(PlanStateEnum.NEW, taskPath.getState()) || PlanStateEnum.isEnumByCode(PlanStateEnum.WAIT_START, taskPath.getState()) || PlanStateEnum.isEnumByCode(PlanStateEnum.AGAIN_PLAN, taskPath.getState())) {
            RcsLog.algorithmLog.warn(RcsLog.getTemplate(4), RcsLog.randomInt(), taskPath.getAgvId(), taskPath.getTaskId(), "AGV规划状态为 NEW 或 WAIT_START 或 AGAIN_PLAN，跳过判断是否需要二次路径规划");
            return;
        }
        //获取任务
        RcsTask rcsTask = taskManager.taskCache.get(taskPath.getTaskCode());
        if (rcsTask == null) {
            RcsLog.consoleLog.warn(RcsLog.getTemplate(4), RcsLog.randomInt(), taskPath.getAgvId(), taskPath.getTaskId(), "任务不存在，取消设置 AGAIN_PLAN 状态");
            return;
        }
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(taskPath.getAgvId());
        //判断当前点位是否为规划终点
        if (!taskPath.getRunningRoutes().isEmpty() && !taskPath.getTaskDestin().equals(taskPath.getCurrentPlanDestin()) && taskPath.getTrafficState() == 0) {
            //获取二次路径规划提前量
            Integer howManyPointPlanPath = coreYaml.getAlgorithmCommon().getOrDefault("remaining_point_plan_path", 2);
            //获取规划点位最大数量
            Integer planPointMaxSize = coreYaml.getAlgorithmCommon().getOrDefault("plan_point_max_size", 10);
            //获取有效的运行中点位集合
            List<RcsPoint> runningPoints = taskPath.getEffectiveRunningPoints();

            boolean flag = false;
            //判断当前AGV是否等待中
            if (taskPath.getPathCode() >= 1
                    && AgvStateEnum.isEnumByCode(AgvStateEnum.WAIT, rcsAgv.getAgvState())
                    && PlanStateEnum.isEnumByCode(PlanStateEnum.RUN, taskPath.getState())
                    && AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState())) {
                RcsLog.consoleLog.warn(RcsLog.getTemplate(4), RcsLog.randomInt(), taskPath.getAgvId(), taskPath.getTaskId(), "isPlanRouteAgain 当前AGV是等待中需要再次规划路径");
                flag = true;
            }

            //判断有效点位是否大于规划点位最大数量
            if (flag && runningPoints.size() >= planPointMaxSize) {
                RcsLog.consoleLog.warn(RcsLog.getTemplate(4), RcsLog.randomInt(), taskPath.getAgvId(), taskPath.getTaskId(), "有效点位大于配置文件和的规划点位最大数量，取消设置 AGAIN_PLAN 任务状态");
                flag = false;
            }

            //判断二次路径规划提前量是否大于等于当前点位集合大小
            if (flag || !PlanStateEnum.isEnumByCode(PlanStateEnum.NEW, taskPath.getState()) && howManyPointPlanPath.compareTo(runningPoints.size()) >= 0 && !PlanStateEnum.isEnumByCode(PlanStateEnum.AGAIN_PLAN, taskPath.getState())) {
                RcsLog.consoleLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "二次规划提前量已满足，设置任务状态：" + PlanStateEnum.AGAIN_PLAN);
                //设置任务状态为二次规划
                taskPath.setState(PlanStateEnum.AGAIN_PLAN.code);
            }
        } else if (PlanStateEnum.isEnumByCode(PlanStateEnum.RUN, taskPath.getState())
                && AgvStateEnum.isEnumByCode(AgvStateEnum.WAIT, rcsAgv.getAgvState())
                && AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState())) {
            RcsLog.consoleLog.warn(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "检测到AGV变成等待中，而任务路径未下发，判断是否需要设置二次规划任务状态，设置二次规划任务状态：" + PlanStateEnum.AGAIN_PLAN);
            //设置任务状态为二次规划
            taskPath.setState(PlanStateEnum.AGAIN_PLAN.code);
            if (!taskPath.getNewPlanRoutes().isEmpty()) {
                taskPath.setCurrentPlan(1);
            }
        }
    }

    /**
     * AGV重启和取消检查
     *
     * @param rcsAgv AGV
     */
    public void checkAgvRestartOrCancel(RcsAgv rcsAgv) {
        if (rcsAgv != null) {
            boolean hasTask = taskPathManager.hasTask(rcsAgv.getAgvId());
            //判断是否有任务，没有任务则不需要取消任务
            if (!hasTask) {
                return;
            }
            //判断AGV状态
//            if (AgvStateEnum.isEnumByCode(AgvStateEnum.OFFLINE, rcsAgv.getAgvState())) {
//                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(rcsAgv.getAgvId(), "AGV被重启，将进入取消任务流程"));
//                //将数据存到AGV错误字段中
//                agvSuggestionManager.addSuggestion(rcsAgv.getAgvId(), "AGV被重启，将进入取消任务流程");
//                //获取任务路径
//                TaskPath taskPath = TaskPathCache.getFirst(rcsAgv.getAgvId());
//                //设置任务状态为取消
//                taskPath.setState(PlanStateEnum.CANCEL.code);
//            } else
            if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.CANCEL, rcsAgv.getTaskState())) {
                TaskPath taskPath = taskPathManager.getFirst(rcsAgv.getAgvId());
                if (rcsAgv.getTaskId().equalsIgnoreCase(taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()))) {
                    RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), rcsAgv.getAgvId(), "AGV车体取消了任务，将进入取消任务流程");
                    //将数据存到AGV错误字段中
                    agvSuggestionManager.addSuggestion(rcsAgv.getAgvId(), "AGV车体取消了任务，将进入取消任务流程");
                    //设置任务状态为取消
                    taskPath.setState(PlanStateEnum.CANCEL.code);
                }
            } else if (!AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, rcsAgv.getTaskState()) && "".equals(rcsAgv.getTaskId())) {
                //获取任务路径
                TaskPath taskPath = taskPathManager.getFirst(rcsAgv.getAgvId());
                if (!PlanStateEnum.isEnumByCode(PlanStateEnum.CHECK, taskPath.getState()) && !PlanStateEnum.isEnumByCode(PlanStateEnum.NEW, taskPath.getState())) {
                    RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), rcsAgv.getAgvId(), "AGV被重置，将进入取消任务流程");
                    //将数据存到AGV错误字段中
                    agvSuggestionManager.addSuggestion(rcsAgv.getAgvId(), "AGV被重置，将进入取消任务流程");
                    //设置任务状态为取消
                    taskPath.setState(PlanStateEnum.CANCEL.code);
                }
            }
        }
    }

    /**
     * 判断AGV是否在点位
     *
     * @param rcsAgv AGV
     */
    public void checkAgvPoint(RcsAgv rcsAgv) {
        // 获取AGV点位
        Integer pointId = rcsAgv.getPointId();
        if (pointId != null && pointId >= 0) {
            return;
        }

        RcsLog.consoleLog.error("{} AGV当前不在点位，正在根据容差获取点位", rcsAgv.getAgvId());

        // 获取容差配置，默认300毫米
        Integer agvXyTolerance = coreYaml.getAlgorithmCommon().getOrDefault("agv_xy_tolerance", 300);
        Integer mapId = rcsAgv.getMapId();
        Integer slamX = rcsAgv.getSlamX();
        Integer slamY = rcsAgv.getSlamY();

        // 检查必要参数
        if (mapId == null || slamX == null || slamY == null) {
            return;
        }

        // 查找最近的点
        RcsPoint nearestPoint = mapManager.getPointByLocation(mapId, slamX, slamY, agvXyTolerance);

        // 检查最近点是否在容差范围内
        if (nearestPoint != null) {
            //设置AGV点位
            rcsAgv.setPointId(nearestPoint.getId());
        } else {
            alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E10003, "rcs");
            agvSuggestionManager.addSuggestion(rcsAgv.getAgvId(), "AGV当前不在点位，请将AGV开到点位");
        }
    }

    /**
     * 判断AGV是否动作中
     *
     * @param taskPath 任务路径
     * @return 结果，true为动作中，false为非动作中
     */
    private boolean handleInAction(TaskPath taskPath) {
        //获取AGV编号
        String agvCode = taskPath.getAgvId();
        //获取AGV
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(agvCode);
        boolean flag = false;
        //判断AGV是否自动模式 并且 任务ID是否一致
        if (AgvModeEnum.isManual(rcsAgv.getAgvMode()) && rcsAgv.getTaskId().equalsIgnoreCase(taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()))) {
            //判断AGV是否动作中 动作号：1取货 2放货 3充电
            if (AgvStateEnum.isEnumByCode(AgvStateEnum.ACTION, rcsAgv.getAgvState()) || AgvStateEnum.isEnumByCode(AgvStateEnum.CHARGE, rcsAgv.getAgvState())) {
                //判断任务路径是否动作中
                if (!PlanStateEnum.isEnumByCode(PlanStateEnum.ACTION, taskPath.getState())) {
                    //设置发送成功标识
                    taskPath.setActionStart(0);
                    taskPath.setState(PlanStateEnum.ACTION.code);
                    RcsLog.consoleLog.error("{} 检测到AGV状态变成动作中，设置AGV动作中", agvCode);
                }

                flag = true;
            } else if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.FINISH, rcsAgv.getTaskState()) && TaskActionEnum.isEnumByCode(TaskActionEnum.PICKUP, rcsAgv.getTaskAct()) && !AgvGoodsEnum.isEnumByCode(AgvGoodsEnum.NONE, rcsAgv.getGoodsState())) {
                //判断是否已到达起点 0未到达起点 1已到达起点

                //设置发送成功标识
                taskPath.setActionStart(0);
                taskPath.setState(PlanStateEnum.ACTION.code);
                RcsLog.consoleLog.error("{} 检测到AGV状态跳过动作中变成取货完成，设置AGV动作中", agvCode);

                flag = true;
            } else if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.FINISH, rcsAgv.getTaskState()) && TaskActionEnum.isEnumByCode(TaskActionEnum.UNLOAD, rcsAgv.getTaskAct()) && AgvGoodsEnum.isEnumByCode(AgvGoodsEnum.NONE, rcsAgv.getGoodsState())) {
                //判断是否已到达起点 0未到达起点 1已到达起点

                //设置发送成功标识
                taskPath.setActionStart(0);
                taskPath.setState(PlanStateEnum.ACTION.code);
                RcsLog.consoleLog.error("{} 检测到AGV状态跳过动作中变成卸货完成，设置AGV动作中", agvCode);

                flag = true;
            }
        }

        return flag;
    }

    /**
     * 判断AGV当前点位是否在系统规划路径上。
     * 根据路径类型（直线或贝塞尔曲线），使用不同的容差来判断AGV是否偏离路径。
     *
     * @param taskPath 任务路径，包含当前点和下一点
     * @return 是否在路径上
     */
    public Boolean isInPath(TaskPath taskPath) {
        boolean flag;
        // 路径容差
        Integer tolerance = coreYaml.getAlgorithmCommon().getOrDefault("agv_path_tolerance", 500);

        //获取AGV编号
        String agvId = taskPath.getAgvId();
        //获取AGV
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(agvId);

        //获取AGV行驶中的路径
        List<RcsPoint> runningRoutes = taskPath.getRunningRoutes();
        //如果没有行驶的路径，也就没有脱轨这种说法
        if (runningRoutes.isEmpty()) {
            return true;
        }

        // 获取AGV当前坐标
        int agvX = rcsAgv.getSlamX();
        int agvY = rcsAgv.getSlamY();

        //判断AGV当前点是否在路径上
        flag = mapManager.isPointWithinPathTolerance(rcsAgv.getMapId(), runningRoutes, tolerance, agvX, agvY);
        if (!flag) {
            //判断AGV所在路径上的前后点位
            //如果不在则告警
            RcsLog.consoleLog.error("{} AGV脱轨", agvId);
            RcsLog.algorithmLog.error("{} AGV脱轨", agvId);
            alarmManager.triggerAlarm(agvId, taskPath.getTaskGroup(), taskPath.getTaskCode(), AlarmCodeEnum.E10004, null, "rcs");
        }

        return flag;
    }

    /**
     * 判断任务是否完成
     *
     * @param taskPath 任务路径
     * @return 是否完成
     */
    private Boolean isTaskFinish(TaskPath taskPath) {
        boolean flag = false;
        //获取AGV信息
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(taskPath.getAgvId());

        //判断当前点位是否等于任务目的地
        //条件1：AGV的任务状态是完成
        //条件2：AGV的任务ID和任务规划的任务ID一致
        if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.FINISH, rcsAgv.getTaskState()) && rcsAgv.getTaskId().equalsIgnoreCase(taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()))) {
            //任务完成
            flag = true;
            RcsLog.algorithmLog.info("{} 检测到任务[{}]已完成", taskPath.getAgvId(), taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
            RcsLog.consoleLog.info("{} 检测到任务[{}]已完成", taskPath.getAgvId(), taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        }

        return flag;
    }
}
