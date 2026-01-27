package com.ruinap.core.task;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.core.task.structure.cycle.TaskLifecycleManage;
import com.ruinap.core.task.structure.distribution.TaskDistributionFactory;
import com.ruinap.core.task.structure.distribution.TaskStateHandle;
import com.ruinap.infra.enums.task.PlanStateEnum;
import com.ruinap.infra.enums.task.TaskStateEnum;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.structure.FiFoConcurrentMap;
import com.ruinap.persistence.repository.ConfigDB;
import com.ruinap.persistence.repository.TaskDB;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务管理类
 *
 * @author qianye
 * @create 2025-03-10 09:43
 */
@Component
public class TaskManager {

    @Autowired
    private TaskDB taskDB;
    @Autowired
    private ConfigDB configDB;
    @Autowired
    private TaskPathManager taskPathManager;

    /**
     * 全局任务集合
     * <p>
     * 该集合需要等任务分发后才会添加数据进来
     */
    public final FiFoConcurrentMap<String, RcsTask> taskCache = new FiFoConcurrentMap<>();

    /**
     * 查询分组任务列表
     *
     * @return 任务
     */
    public List<Entity> selectTaskList() {
        // 从数据库获取任务列表
        List<Entity> entityList = taskDB.selectTaskGroupList();

        // 如果任务列表为空，直接返回空列表
        if (entityList.isEmpty()) {
            return entityList;
        }

        List<Entity> taskList = new ArrayList<>(entityList);
        // 遍历每个任务实体
        for (Entity entity : entityList) {
            RcsTask rcsTask = entity.toBean(RcsTask.class);
            // 用于收集原因
            List<String> reasons = new ArrayList<>();
            if (!isValidTask(rcsTask, reasons)) {
                taskList.remove(entity);
                // 如果任务无效，记录原因到日志
                RcsLog.algorithmLog.error("{} 任务不符合条件，原因: {}", rcsTask.getId(), String.join(", ", reasons));
            }

            //将任务添加到任务缓存
            this.taskCache.putIfAbsent(rcsTask.getTaskCode(), rcsTask);
        }

        return taskList;
    }

    /**
     * 任务分发
     */
    public void taskDistribution() {
        //获取任务列表
        List<Entity> taskList = selectTaskList();
        // 遍历任务
        for (Entity entity : taskList) {
            RcsTask rcsTask = this.taskCache.get(entity.getStr("task_code"));
            if (rcsTask == null) {
                RcsLog.consoleLog.error("{} 获取不到任务数据", entity.getStr("task_code"));
                continue;
            }

            //工作状态 0结束工作 1正在工作
            if (configDB.getWorkState().equals(0)) {
                if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, rcsTask.getTaskType())) {
                    RcsLog.consoleLog.error("{} 当前系统状态为【结束工作】，跳过下发搬运任务", rcsTask.getTaskCode());
                    RcsLog.algorithmLog.error("{} 当前系统状态为【结束工作】，跳过下发搬运任务", rcsTask.getTaskCode());
                    continue;
                }
            }
            //获取下发状态 0未下发 1已下发
            Integer sendState = rcsTask.getSendState();
            if (sendState == 1) {
                Integer taskState = rcsTask.getTaskState();
                if (!TaskStateEnum.isEnumByCode(TaskStateEnum.CANCEL, taskState) && !TaskStateEnum.isEnumByCode(TaskStateEnum.NEW, taskState) && taskState.compareTo(TaskStateEnum.FINISH.code) > 0) {
                    String equipmentCode = rcsTask.getEquipmentCode();
                    if (equipmentCode != null && !equipmentCode.isEmpty()) {
//                        TaskPath taskSection = TaskSectionManage.getTaskSection(equipmentCode);
//                        boolean flag = false;
//                        TaskPath taskPath = taskPathManager.getFirst(equipmentCode);
//                        if (taskSection == null && taskPath == null) {
//                            flag = true;
//                        } else {
//                            if (taskSection != null && !rcsTask.getId().equals(taskSection.getTaskId())) {
//                                flag = true;
//                            } else if (taskPath != null && !rcsTask.getId().equals(taskPath.getTaskId())) {
//                                flag = true;
//                            }
//                        }

//                        if (flag) {
//                            //任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 97取货运行中 98卸货运行中 99运行中
//                            rcsTask.setTaskState(TaskStateEnum.CANCEL.code);
//                            RcsLog.consoleLog.info("{} 任务与调度任务路径数据不匹配，将任务改为取消", rcsTask.getTaskCode());
//                        }
                    }
                }

                RcsLog.algorithmLog.warn("{} 跳过已下发的任务", rcsTask.getTaskCode());
                continue;
            }
            // 获取任务状态枚举
            TaskStateEnum taskStateEnum = TaskStateEnum.fromEnum(rcsTask.getTaskState());
            if (taskStateEnum == null) {
                RcsLog.algorithmLog.error("{} 任务分配失败，获取不到任务状态枚举：{}", rcsTask.getTaskCode(), rcsTask.getTaskState());
                continue;
            } else if (taskStateEnum.code.compareTo(TaskStateEnum.FINISH.code) <= 0) {
                RcsLog.algorithmLog.error("{} 任务跳过分配，任务状态已完成，状态枚举：{}", rcsTask.getTaskCode(), rcsTask.getTaskState());
                continue;
            }
            // 获取任务状态分发处理器
            TaskStateHandle stateHandler = TaskDistributionFactory.getFactory(taskStateEnum);
            if (stateHandler != null) {
                //调用具体的处理器
                RcsAgv rcsAgv = stateHandler.handle(rcsTask);
                if (rcsAgv != null) {
                    //判断AGV是否已经存在任务
                    List<TaskPath> taskPaths = taskPathManager.get(rcsAgv.getAgvId());
                    if (!taskPaths.isEmpty()) {
                        //后续可以在这里做一些处理，比如取消当前待机任务然后接取新的搬运任务，节省时间提高设备稼动率

                        //判断是否充电任务
                        TaskPath firstTaskPath = taskPaths.getFirst();
                        RcsTask firstRcsTask = taskCache.get(firstTaskPath.getTaskCode());
                        if (firstRcsTask != null && TaskTypeEnum.isEnumByCode(TaskTypeEnum.CHARGE, firstRcsTask.getTaskType())) {
                            //充电任务取消
                            firstTaskPath.setState(PlanStateEnum.CANCEL.code);
                            RcsLog.consoleLog.error("{} {} 任务需要分配给AGV，取消AGV当前充电任务", rcsAgv.getAgvId(), rcsTask.getTaskCode());
                            continue;
                        } else if (firstRcsTask != null && TaskTypeEnum.isEnumByCode(TaskTypeEnum.DOCK, firstRcsTask.getTaskType())) {
                            //停靠任务取消
                            firstTaskPath.setState(PlanStateEnum.CANCEL.code);
                            RcsLog.consoleLog.error("{} {} 任务需要分配给AGV，取消AGV当前停靠任务", rcsAgv.getAgvId(), rcsTask.getTaskCode());
                            continue;
                        } else {
                            RcsLog.consoleLog.error("{} AGV [{}]已经存在任务，跳过下发新任务", rcsTask.getTaskCode(), rcsAgv.getAgvId());
                            RcsLog.algorithmLog.error("{} AGV [{}]已经存在任务，跳过下发新任务", rcsTask.getTaskCode(), rcsAgv.getAgvId());
                            continue;
                        }
                    }

                    TaskPath taskPath;
                    //判断当前任务是否是新任务
                    if (taskStateEnum.equals(TaskStateEnum.NEW)) {
                        //这里加入检测，判断该任务是否适合运行
////                        Boolean flag = TaskBusinessManage.newTaskCheck(rcsTask, rcsAgv);
////                        if (!flag) {
////                            return;
////                        }
//                        //任务拆分
//                        taskPath = TaskSectionManage.getNewTaskSection(rcsTask, rcsAgv);
                    } else {
                        //直接获取新任务
//                        taskPath = TaskSectionManage.getTaskSection(rcsAgv.getAgvId());
//                        if (taskPath == null) {
//                            RcsLog.algorithmLog.error("{} 任务[{}]返回空数据，任务获取失败", rcsAgv.getAgvId(), rcsTask.getTaskCode());
//                            continue;
//                        }
                    }

//                    if (taskPath != null) {
//                        //添加任务路径
//                        addTaskPath(rcsAgv.getAgvId(), rcsTask, taskPath);
//                    } else {
//                        RcsLog.algorithmLog.error("{} 任务[{}]返回空数据，任务拆分失败", rcsAgv.getAgvId(), rcsTask.getTaskCode());
//                    }
                } else {
                    RcsLog.consoleLog.error("{} 任务分配失败，没有空闲的AGV", rcsTask.getTaskCode());
                    RcsLog.algorithmLog.error("{} 任务分配失败，没有空闲的AGV", rcsTask.getTaskCode());
                    //所有AGV都非空闲，则此节拍分发结束
                    break;
                }
            } else {
                RcsLog.algorithmLog.error("{} 任务分配失败，没有对应的任务状态处理器", rcsTask.getTaskCode());
            }
        }
    }

    /**
     * 添加任务路径
     *
     * @param agvCode  AGV编号
     * @param rcsTask  SQL任务
     * @param taskPath 任务路径
     */
    private void addTaskPath(String agvCode, RcsTask rcsTask, TaskPath taskPath) {
        List<TaskPath> taskPaths = taskPathManager.get(agvCode);
        if (taskPaths.isEmpty()) {
            RcsLog.algorithmLog.error("{} 任务[{}]成功分配到AGV", agvCode, rcsTask.getTaskCode());
            RcsLog.consoleLog.error("{} 任务[{}]成功分配到AGV", agvCode, rcsTask.getTaskCode());
            // 添加任务路径
            taskPathManager.put(agvCode, taskPath);
            // 设置设备编码
            rcsTask.setEquipmentCode(agvCode);
            // 设置下发状态 0未下发 1已下发
            rcsTask.setSendState(1);

            // 任务状态改成已下发
            TaskLifecycleManage.stateChange(rcsTask, TaskStateEnum.ISSUED);
        } else {
            RcsLog.algorithmLog.warn("{} 任务[{}]成功分配到AGV，但AGV[{}]已存在任务，等待AGV运行完成", agvCode, rcsTask.getTaskCode(), agvCode);
        }
    }


    /**
     * 任务信号检查
     * <p>
     * 检查项：任务中断
     */
    public void taskSignalCheck() {
        taskCache.forEach((key, value) -> {
            if (value.getEquipmentCode() != null) {
                List<TaskPath> taskPaths = taskPathManager.get(value.getEquipmentCode());
                if (!taskPaths.isEmpty()) {
                    TaskPath taskPath = taskPaths.getFirst();
                    if (taskPath != null) {
                        //判断任务状态
                        if (value.getInterruptState() == 1) {
                            //设置任务状态 中断任务
//                            taskPath.setState(PlanStateEnum.INTERRUPT.code);
                            //设置任务状态 取消任务
                            taskPath.setState(PlanStateEnum.CANCEL.code);
                            //设置取消类型 0正常取消 1强制取消
                            taskPath.setCancelType(1);
                            RcsLog.consoleLog.error("{} 检测到任务中断信号，将进入取消任务流程", key);
                        } else if (value.getInterruptState() == 2) {
                            //设置任务状态 取消任务
                            taskPath.setState(PlanStateEnum.CANCEL.code);
                            //设置取消类型 0正常取消 1强制取消
                            taskPath.setCancelType(0);
                            RcsLog.consoleLog.error("{} 检测到任务取消信号，将进入取消任务流程", key);
                        } else if (value.getInterruptState() == 3) {
                            //设置任务状态 上位取消
                            taskPath.setState(PlanStateEnum.CANCEL.code);
                            //设置取消类型 0正常取消 1强制取消
                            taskPath.setCancelType(1);
                            RcsLog.consoleLog.error("{} 检测到任务上位取消信号，将进入取消任务流程", key);
                        }
                    }
                }
            }
        });
    }

    /**
     * 检查任务是否有效
     *
     * @param rcsTask 任务
     * @param reasons 返回原因
     * @return 是否有效 true表示有效，false表示无效
     */
    private static boolean isValidTask(RcsTask rcsTask, List<String> reasons) {
        boolean isValid = true;

        // 检查任务编号
        if (rcsTask.getTaskCode() == null || rcsTask.getTaskCode().isEmpty()) {
            reasons.add("任务编号为空");
            isValid = false;
        }

        // 检查任务类型
        if (rcsTask.getTaskType() == null) {
            reasons.add("任务类型为空");
            isValid = false;
        } else {
            // 根据任务类型进行额外检查
            if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CARRY, rcsTask.getTaskType())) {
                if (rcsTask.getOriginFloor() == null) {
                    reasons.add("搬运任务起点楼层为空");
                    isValid = false;
                }
                if (rcsTask.getOrigin() == null || rcsTask.getOrigin().isEmpty()) {
                    reasons.add("搬运任务起点为空");
                    isValid = false;
                }
            } else if (rcsTask.getTaskType().compareTo(TaskTypeEnum.AVOIDANCE.code) > 0) {
                if (rcsTask.getDestinFloor() == null) {
                    reasons.add("任务终点楼层为空");
                    isValid = false;
                }
                if (rcsTask.getDestinArea() == null || rcsTask.getDestinArea().isEmpty()) {
                    reasons.add("任务终点区域为空");
                    isValid = false;
                }
            }
        }

        // 检查任务终点
        if (rcsTask.getDestin() == null || rcsTask.getDestin().isEmpty()) {
            reasons.add("任务终点为空");
            isValid = false;
        }

        // 检查任务状态
        if (rcsTask.getTaskState() == null) {
            reasons.add("任务状态为空");
            isValid = false;
        }

        // 检查下发状态
        if (rcsTask.getSendState() == null) {
            reasons.add("下发状态为空");
            isValid = false;
        }

        // 检查执行系统
        if (rcsTask.getExecutiveSystem() == null) {
            reasons.add("执行系统为空");
            isValid = false;
        }

        return isValid;
    }

    /**
     * 格式化点位编号
     *
     * @param mapId 地图编号
     * @param id    点位编号
     * @return 格式化后的点位编号
     */
    public String formatPoint(Integer mapId, Integer id) {
        return StrUtil.format("{}-{}", mapId, id);
    }

    /**
     * 格式化任务编号
     *
     * @param taskCode  任务编号
     * @param subTaskNo 子任务编号
     * @return 格式化后的任务编号
     */
    public String formatTask(String taskCode, Integer subTaskNo) {
        return StrUtil.format("{}-{}", taskCode, subTaskNo);
    }
}
