package com.ruinap.core.algorithm.strategy;

import com.ruinap.core.algorithm.domain.PathContext;
import com.ruinap.core.algorithm.domain.PathState;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.AgvTask;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.structure.TaskSectionManager;
import com.ruinap.infra.enums.agv.AgvTaskStateEnum;
import com.ruinap.infra.enums.task.PlanStateEnum;
import com.ruinap.infra.enums.task.SubTaskTypeEnum;
import com.ruinap.infra.enums.task.TaskActionEnum;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Scope;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.config.ConfigurableBeanFactory;
import com.ruinap.infra.log.RcsLog;

/**
 * 路径检查状态
 *
 * @author qianye
 * @create 2025-03-24 14:05
 */
@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PathCheckState implements PathState {

    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private TaskSectionManager taskSectionManager;

    /**
     * 处理器
     *
     * @param context 上下文
     */
    @Override
    public void handle(PathContext context) {
        // 从上下文获取任务路径
        TaskPath taskPath = context.getTaskPath();

        // ==========================================
        // 0. 【完美ACK】：移除上游分段任务的第一条数据
        // ==========================================
        TaskPath taskSection = taskSectionManager.getTaskSection(taskPath.getAgvId());
        if (taskSection != null && taskSection.equals(taskPath)) {
            TaskPath removePath = taskSectionManager.removeFirst(taskPath.getAgvId());
            if (removePath != null) {
                RcsLog.consoleLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "移除分段任务数据，任务[" + taskPath.getTaskCode() + "-" + taskPath.getSubTaskNo() + "]");
                RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "移除分段任务数据，任务[" + taskPath.getTaskCode() + "-" + taskPath.getSubTaskNo() + "]");
            }
        }

        // ==========================================
        // 1. 第一道安检：AGV是否存在 (防空指针)
        // ==========================================
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(taskPath.getAgvId());
        if (rcsAgv == null) {
            // 绝不手动 remove，只改变状态让引擎自然推演至 CancelState 收尸！
            taskPath.setState(PlanStateEnum.CANCEL.code);
            RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "获取不到AGV，任务[" + taskPath.getTaskCode() + "]发生致命异常，流转至CANCEL状态");
            RcsLog.algorithmLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "获取不到AGV，任务[" + taskPath.getTaskCode() + "]发生致命异常，流转至CANCEL状态");
            // 卫语句：立刻终止
            return;
        }

        // ==========================================
        // 2. 第二道安检：任务是否存在 (防数据库游离)
        // ==========================================
        RcsTask rcsTask = taskManager.taskCache.get(taskPath.getTaskCode());
        if (rcsTask == null) {
            taskPath.setState(PlanStateEnum.CANCEL.code);
            RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), rcsAgv.getAgvId(), "任务[" + taskPath.getTaskCode() + "]从任务列表中获取不到，流转至CANCEL状态");
            // 卫语句：立刻终止
            return;
        }

        // ==========================================
        // 3. 第三道安检：是否已抵达终点 (幸福路径起点)
        // ==========================================
        RcsPoint rcsPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
        if (taskPath.getTaskDestin().equals(rcsPoint) && !TaskTypeEnum.isEnumByCode(TaskTypeEnum.CHARGE, rcsTask.getTaskType())) {
            RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV当前点位和任务终点一致，设置任务状态：" + PlanStateEnum.FINISH);
            taskPath.setState(PlanStateEnum.FINISH.code);
            // 卫语句：立刻终止，下个周期走 FINISH 逻辑
            return;
        }

        // ==========================================
        // 4. 【核心残局恢复】：判断AGV车体任务状态是否匹配
        // ==========================================
        String taskId = rcsAgv.getTaskId();
        //获取AGV任务状态 0无任务 1有任务 2已完成 3已取消
        Integer taskState = rcsAgv.getTaskState();
        if (taskId.equalsIgnoreCase(taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()))) {

            // 4.1 判断是否对接设备
            if (taskPath.getDockDevice() != null) {
                taskPath.setState(PlanStateEnum.DOCK_DEVICE.code);
                RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "设置任务状态为对接设备中");
                return;
            }

            // 4.2 判断AGV任务终点和规划终点是否一样
            if (taskPath.getTaskDestin().equals(taskPath.getCurrentPlanDestin())) {
                //判断AGV任务状态是否完成
                if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.FINISH, taskState)) {
                    //动作号 1取货 2放货 3充电 4对接
                    TaskActionEnum taskActionEnum = TaskActionEnum.fromEnum(rcsAgv.getTaskAct());
                    //判断动作是否为空
                    if (!taskActionEnum.equals(TaskActionEnum.NOT) && !taskActionEnum.equals(TaskActionEnum.NULL)) {
                        RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV任务动作号，设置任务状态：" + PlanStateEnum.ACTION);
                        taskPath.setState(PlanStateEnum.ACTION.code);
                    } else {
                        RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "未监测到AGV任务动作号，设置任务状态：" + PlanStateEnum.FINISH);
                        taskPath.setState(PlanStateEnum.FINISH.code);
                    }
                    return;
                } else {
                    RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "任务路径已规划完毕，等待AGV任务状态设置为完成");
                }
            }

            // 4.3 判断AGV任务状态是否取消
            if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.CANCEL, taskState)) {
                RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV任务状态为取消，设置任务状态：" + PlanStateEnum.CANCEL);
                taskPath.setState(PlanStateEnum.CANCEL.code);
                return;
            }

            // 4.4 判断是否待开始 (路径一致校验)
            AgvTask agvTask = agvManager.getAgvTaskCache().get(taskPath.getAgvId());
            if (agvTask != null) {
                Integer pathCode = agvTask.getSetQua() - 1;
                if (AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, taskState) && pathCode.equals(taskPath.getPathCode())) {
                    if (taskPath.getPathCode() == 1) {
                        RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV当前任务已下发路径待开始，设置任务状态：" + PlanStateEnum.WAIT_START);
                        taskPath.setState(PlanStateEnum.WAIT_START.code);
                        return;
                    }
                }
            }

            // 4.5 都不是，那就是在跑着呢 (RUN)
            RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV状态无变化，设置任务状态：" + PlanStateEnum.RUN);
            taskPath.setState(PlanStateEnum.RUN.code);
            return; // 恢复完毕，退出当前推演周期
        }

        // ==========================================
        // 5. 新任务下发：条件判定
        // ==========================================
        int subTaskType = taskPath.getSubTaskType();
        if ((agvManager.getRcsAgvIdle(rcsAgv) != null || taskPath.getSubTaskNo() > 1)
                && !AgvTaskStateEnum.isEnumByCode(AgvTaskStateEnum.HAVE, taskState)) {

            if (taskPath.getDockDevice() != null) {
                RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV存在设备对接数据，设置任务状态：" + PlanStateEnum.DOCK_DEVICE);
                taskPath.setState(PlanStateEnum.DOCK_DEVICE.code);
            } else if (SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.ORIGIN, subTaskType) || SubTaskTypeEnum.isEnumByCode(SubTaskTypeEnum.DESTIN, subTaskType)) {
                RcsLog.algorithmLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "监测到AGV子任务枚举，设置任务状态：" + PlanStateEnum.NEW);
                taskPath.setState(PlanStateEnum.NEW.code);
            }
        } else {
            // ==========================================
            // 6. 兜底等待：降级日志，静默重试 (防日志风暴)
            // ==========================================
            // 此时状态依然是 CHECK，虚拟线程将在下一个 120ms 后自动重试。
            RcsLog.algorithmLog.debug(RcsLog.getTemplate(3), RcsLog.randomInt(), taskPath.getAgvId(), "AGV不是空闲或子任务号大于1或已有任务，继续在 CHECK 状态静默等待...");
        }
    }
}
