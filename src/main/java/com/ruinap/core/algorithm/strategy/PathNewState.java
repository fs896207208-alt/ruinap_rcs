package com.ruinap.core.algorithm.strategy;

import cn.hutool.core.util.StrUtil;
import com.ruinap.core.algorithm.RcsPlanManager;
import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.algorithm.TrafficManager;
import com.ruinap.core.algorithm.domain.PathContext;
import com.ruinap.core.algorithm.domain.PathState;
import com.ruinap.core.algorithm.search.RcsAstarSearch;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.AgvTask;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.PointOccupyManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.event.AgvCommandEvent;
import com.ruinap.core.task.structure.cycle.TaskLifecycleManager;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.enums.agv.AgvStateEnum;
import com.ruinap.infra.enums.agv.AgvTaskStateEnum;
import com.ruinap.infra.enums.task.*;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Scope;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.config.ConfigurableBeanFactory;
import com.ruinap.infra.framework.core.event.ApplicationEventPublisher;
import com.ruinap.infra.log.RcsLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新任务状态
 *
 * @author qianye
 * @create 2025-03-27 10:47
 */
@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PathNewState implements PathState {

    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private RcsPlanManager rcsPlanManager;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private TaskLifecycleManager taskLifecycleManager;
    @Autowired
    private RcsAstarSearch rcsAstarSearch;
    @Autowired
    private TrafficManager trafficManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private SlideTimeWindow slideTimeWindow;
    @Autowired
    private PointOccupyManager pointOccupyManager;

    @Override
    public void handle(PathContext context) {
        // 从上下文获取任务路径
        TaskPath taskPath = context.getTaskPath();
        // 获取AGV编号
        String agvId = taskPath.getAgvId();
        //获取AGV信息
        RcsAgv agv = agvManager.getRcsAgvByCode(agvId);
        if (agv == null) {
            RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "获取不到AGV数据，无法规划路径");
            return;
        }

        // 打印记录日志
        String formatTemplate = StrUtil.format(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "新任务[" + taskPath.getTaskCode() + "]", "进行第一次路径规划，当前AGV状态为：" + (AgvStateEnum.isEnumByCode(AgvStateEnum.IDLE, agv.getAgvState()) ? "空闲" : "非空闲"));
        RcsLog.consoleLog.info(formatTemplate);
        RcsLog.algorithmLog.info(formatTemplate);
        RcsLog.taskLog.info(formatTemplate);

        //获取AGV当前点位
        RcsPoint currentPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());
        AgvTask agvTask = agvManager.getAgvTaskCache().get(agvId);

        // 判断AGV是空闲 或者 当前规划路径不是任务终点
        if (AgvStateEnum.isEnumByCode(AgvStateEnum.IDLE, agv.getAgvState())) {
            if (currentPoint != null) {
                if (!AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, agv.getTaskState())) {
                    //未下发过
                    firstPlanPath(taskPath, agv, currentPoint);
                } else if (agv.getTaskId().equalsIgnoreCase(taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()))
                        && AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, agv.getTaskState())
                        && agvTask != null
                        && (agvTask.getSetQua() - 1) == taskPath.getPathCode()
                ) {
                    RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "监控到当前AGV状态为等待中，将进入请求开始阶段");
                    RcsLog.algorithmLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "监控到当前AGV状态为等待中，将进入请求开始阶段");
                    taskPath.setState(PlanStateEnum.WAIT_START.code);
                    // 任务状态改变
                    stateChange(taskPath);
                }
            } else {
                RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "当前AGV不在点位，无法进行路径规划");
                RcsLog.algorithmLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "当前AGV不在点位，无法进行路径规划");
            }
        } else {
            // 打印记录日志
            formatTemplate = StrUtil.format(RcsLog.getTemplate(3), RcsLog.randomInt(), agvId, "AGV非空闲状态，无法接收新任务");
            RcsLog.consoleLog.info(formatTemplate);
            RcsLog.algorithmLog.info(formatTemplate);
            RcsLog.taskLog.info(formatTemplate);
        }
    }

    /**
     * 第一次规划路径 (极简调度流转 & 绝对安全防线)
     */
    private void firstPlanPath(TaskPath taskPath, RcsAgv agv, RcsPoint currentPoint) {
        String agvId = taskPath.getAgvId();
        int currentPlan = taskPath.getCurrentPlan();

        // ==========================================
        // 1. 防爆护盾：正在发送中，静默跳过
        // ==========================================
        if (currentPlan == CurrentPlanStateEnum.WAITING_GATEWAY_ACK.code) {
            return;
        }

        // ==========================================
        // 2. 补偿恢复：如果是网络发送失败，冷却后重试
        // ==========================================
        if (currentPlan == CurrentPlanStateEnum.SEND_FAILED.code) {
            RcsLog.algorithmLog.info("{} 从网络失败状态冷却恢复，准备重新获取路径下发", agvId);
            taskPath.setCurrentPlan(CurrentPlanStateEnum.REQUIRE_PLAN.code);
            return;
        }

        // ==========================================
        // 3. 状态 0：审查并下发
        // ==========================================
        if (currentPlan == CurrentPlanStateEnum.REQUIRE_PLAN.code) {
            RcsLog.algorithmLog.info("{} 开始路径审查与下发", agvId);
            taskPath.setCurrentPlan(CurrentPlanStateEnum.WAITING_GATEWAY_ACK.code);

            // 无论 TrafficManager 返回的是 50个点(畅通) 还是 1个点(驻留)，我只管下发！
            List<RcsPoint> planRcsPoints = trafficManager.pruneAndReviewPath(agvId, currentPoint, taskPath.getExpectRoutes());

            // --- 异常降级兜底 ---
            if (planRcsPoints == null || planRcsPoints.isEmpty()) {
                RcsLog.algorithmLog.warn("{} 预期路径彻底失效，触发 A* 重新规划", agvId);

                // 【修复2】：起点必须是 currentPoint！绝不能是 taskOrigin！
                planRcsPoints = rcsAstarSearch.aStarSearch(agvId, currentPoint, taskPath.getTaskDestin()).getPaths();

                if (planRcsPoints == null || planRcsPoints.isEmpty()) {
                    RcsLog.algorithmLog.error("{} A* 重新规划彻底失败(可能处于死胡同)", agvId);
                    taskPath.setCurrentPlan(CurrentPlanStateEnum.REQUIRE_PLAN.code);
                    return;
                }

                // 【修复3】：A* 算出的新路绝对不能直接“裸奔下发”！
                // 将新路更新为预期路线，并把状态打回 0。让下一个调度周期重新执行 pruneAndReviewPath(过安检)！
                taskPath.setExpectRoutes(planRcsPoints);
                taskPath.setCurrentPlan(CurrentPlanStateEnum.REQUIRE_PLAN.code);
                RcsLog.algorithmLog.info("{} A* 新路已生成，打回 REQUIRE_PLAN 等待下一帧交管审查", agvId);
                return;
            }

            // 更新实体缓存
            taskPath.setCurrentPlanOrigin(planRcsPoints.getFirst());
            taskPath.setCurrentPlanDestin(planRcsPoints.getLast());
            taskPath.setNewPlanRoutes(planRcsPoints);

            // 1. 构建下发事件
            AgvCommandEvent event = new AgvCommandEvent(this, AgvCommandEvent.CommandType.MOVE, taskPath, agv);
            // 2. 【核心架构魔法】：异步非阻塞回调
            // 当 AgvCommandListener 中执行 event.getAckFuture().complete(true) 时，这段代码会自动触发！
            List<RcsPoint> finalPlanRcsPoints = planRcsPoints;
            event.getAckFuture().thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    // 发送成功，执行所有后置的物理空间与交管数据更新
                    handlePostSendSuccess(taskPath, agvId, finalPlanRcsPoints);
                }
            });
            // 3. 发布事件，主调度线程直接 return，决不阻塞！
            eventPublisher.publishEvent(event);
        }

        // ==========================================
        // 4. 状态 1：弱网重传机制
        // ==========================================
        else if (currentPlan == CurrentPlanStateEnum.REQUIRE_RESEND.code) {
            List<RcsPoint> planRcsPoints = new ArrayList<>(taskPath.getNewPlanRoutes());
            if (planRcsPoints.isEmpty()) {
                taskPath.setCurrentPlan(CurrentPlanStateEnum.REQUIRE_PLAN.code);
                return;
            }
            RcsLog.algorithmLog.warn("{} 触发弱网重传机制，重新发布 Move 事件", agvId);
            taskPath.setCurrentPlan(CurrentPlanStateEnum.WAITING_GATEWAY_ACK.code);


            // 1. 构建下发事件
            AgvCommandEvent event = new AgvCommandEvent(this, AgvCommandEvent.CommandType.MOVE, taskPath, agv);
            // 2. 【核心架构魔法】：异步非阻塞回调
            // 当 AgvCommandListener 中执行 event.getAckFuture().complete(true) 时，这段代码会自动触发！
            event.getAckFuture().thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    // 发送成功，执行所有后置的物理空间与交管数据更新
                    handlePostSendSuccess(taskPath, agvId, planRcsPoints);
                }
            });
            // 3. 发布事件，主调度线程直接 return，决不阻塞！
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 网关发送成功后的异步数据处理 (更新物理缓冲区、交管锁、权重)
     */
    private void handlePostSendSuccess(TaskPath taskPath, String agvId, List<RcsPoint> planRcsPoints) {
        try {
            // 1. 追加 AGV 运行路径
            taskPath.addRunningRoutes(planRcsPoints);

            // 2. 添加点位时间窗权重 (预防拥堵)
            slideTimeWindow.addWeight(planRcsPoints);

            // 3. 动态获取车距 (car_range)
            int carRange = 0;
            Map<String, Map<String, String>> agvLinks = linkYaml.getAgvLink();
            if (agvLinks != null && agvLinks.containsKey(agvId)) {
                String rangeStr = agvLinks.get(agvId).get("car_range");
                if (StrUtil.isNotBlank(rangeStr)) {
                    carRange = Integer.parseInt(rangeStr);
                }
            }

            // 4. 更新整个 AGV 路径缓冲区 (JTS 几何模型)
            trafficManager.updateAgvBuffer(agvId, taskPath.getEffectiveRunningPoints(), carRange);

            // 5. 设置 AGV 管制区占用 & 车距点位占用
            pointOccupyManager.agvControlOccupy();
            pointOccupyManager.handleVehicleDistanceOccupy();

            // 6. 维护逻辑状态码
            taskPath.setLastPathCode(taskPath.getPathCode());
            // 网关确认发出，将调度状态推进为已下发 (或由心跳监听器推进)
            taskPath.setCurrentPlan(1);

            RcsLog.algorithmLog.info("{} [数据落盘] 运行路径、时间窗权重、几何缓冲区及占用区已更新完毕", agvId);

        } catch (Exception e) {
            RcsLog.algorithmLog.error("{} [数据落盘] 网关发送成功，但后置数据处理发生异常", agvId, e);
        }
    }

    /**
     * 处理任务状态变化
     *
     * @param taskPath 任务路径
     */
    public void stateChange(TaskPath taskPath) {
        RcsTask rcsTask = taskManager.taskCache.get(taskPath.getTaskCode());
        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, rcsTask.getTaskType())) {
            //子任务类型 0前往起点任务 1前往终点任务
            int subTaskType = taskPath.getSubTaskType();
            if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, subTaskType)) {
                // 任务状态修改
                taskLifecycleManager.stateChange(rcsTask, TaskStateEnum.TAKE_RUN);
            } else if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, subTaskType)) {
                // 任务状态修改
                taskLifecycleManager.stateChange(rcsTask, TaskStateEnum.UNLOAD_RUN);
            } else {
                // 任务状态修改
                taskLifecycleManager.stateChange(rcsTask, TaskStateEnum.RUN);
            }
        } else {
            // 任务状态修改
            taskLifecycleManager.stateChange(rcsTask, TaskStateEnum.RUN);
        }
    }

}
