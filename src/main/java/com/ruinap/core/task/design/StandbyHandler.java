package com.ruinap.core.task.design;

import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.design.filter.StandbyFilter;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.ConfigDB;
import com.ruinap.persistence.repository.TaskDB;

import java.util.Map;

/**
 * 待机处理器
 *
 * @author qianye
 * @create 2025-04-17 10:20
 */
@Component
public class StandbyHandler implements TaskFlowHandler {

    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private ConfigDB configDB;
    @Autowired
    private TaskDB taskDB;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private StandbyFilter standbyFilter;

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
     *
     * @return 处理结果
     */
    @Override
    public void handleRequest() {
        handleStandby();

        // 委托给下一个处理器执行
        if (nextHandler != null) {
            nextHandler.handleRequest();
        }
    }

    /**
     * 处理待机
     */
    private void handleStandby() {
        //待机模式 -2不待机 -1自定义 0最近待机点 1绑定待机点
        Integer standbyMode = taskYaml.getStandbyCommon().getStandbyMode();
        if (standbyMode.equals(-2)) {
            return;
        }

        // 获取空闲AGV集合
        Map<String, RcsAgv> idleRcsAgvMap = agvManager.getIdleRcsAgvMap();
        AGV_FOR:
        for (Map.Entry<String, RcsAgv> entry : idleRcsAgvMap.entrySet()) {
            //获取AGV
            RcsAgv rcsAgv = entry.getValue();
            //获取分段任务
//            List<TaskPath> taskSections = TaskSectionManage.getTaskSections(rcsAgv.getAgvId());
            //获取任务路径
//            List<TaskPath> taskPaths = TaskPathManager.get(rcsAgv.getAgvId());
//            if (taskSections.isEmpty() && taskPaths.isEmpty()) {
//                RcsPoint agvPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
//                if (agvPoint == null) {
//                    break;
//                }
//                //判断AGV是否在待机屏蔽点
//                if (standbyFilter.isStandbyPointShield(agvPoint, rcsAgv.getAgvId())) {
//                    break;
//                }
//                //判断AGV是否在待机点
//                if (standbyFilter.inStandbyList(agvPoint)) {
//                    break;
//                }
//                // 获取写锁
//                // true 表示需要中断外部循环，false 表示继续
//                boolean shouldBreakLoop = RCS_LOCK.supplyInWrite(() -> {
//                    try {
//                        //遍历任务集合
//                        for (RcsTask task : taskManager.taskCache.values()) {
//                            //任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 97取货运行中 98卸货运行中 99运行中
//                            Integer taskState = task.getTaskState();
//                            // 设备编码
//                            String equipmentCode = task.getEquipmentCode();
//                            if (taskState > 0 && rcsAgv.getAgvId().equals(equipmentCode)) {
//                                return true;
//                            }
//                        }
//
//                        //获取可用待机点
//                        RcsPoint rcsPoint = getAvailableStandbyPoint(rcsAgv);
//                        if (rcsPoint != null) {
//                            RcsLog.algorithmLog.info("{} AGV成功分配到待机点[{}]", rcsAgv.getAgvId(), rcsPoint);
//
//                            //创建待机任务
//                            Entity entity = new Entity();
//                            String taskCodeKey = configDB.taskCodeKey();
//                            entity.setIgnoreNull("task_code", taskCodeKey);
//                            entity.setIgnoreNull("task_type", TaskTypeEnum.DOCK.code);
//                            entity.setIgnoreNull("equipment_code", rcsAgv.getAgvId());
//                            entity.setIgnoreNull("origin_floor", rcsAgv.getMapId());
//                            entity.setIgnoreNull("origin", taskManager.formatPoint(rcsAgv.getMapId(), rcsAgv.getPointId()));
//                            entity.setIgnoreNull("destin_floor", rcsPoint.getMapId());
//                            entity.setIgnoreNull("destin_area", rcsPoint.getAreaCode());
//                            entity.setIgnoreNull("destin", taskManager.formatPoint(rcsPoint.getMapId(), rcsPoint.getId()));
//                            entity.setIgnoreNull("task_priority", 99);
//                            entity.setIgnoreNull("task_source", "rcs");
//
//                            //创建待机任务
//                            taskDB.createTask(entity);
//                            //查询数据库创建的充电任务
//                            Entity taskEntity = taskDB.queryTask(new Entity().set("task_code", taskCodeKey));
//                            //将任务添加到任务缓存
//                            taskManager.taskCache.putIfAbsent(taskCodeKey, taskEntity.toBean(RcsTask.class));
//                            //更新点位选择占用
//                            mapManager.addOccupyType(rcsAgv.getAgvId(), rcsPoint, PointOccupyTypeEnum.CHOOSE);
//                        } else {
//                            RcsLog.algorithmLog.error("{} AGV希望进行待机，但没有分配到可用的待机点", rcsAgv.getAgvId());
//                        }
//
//                        return false;
//                    } catch (SQLException e) {
//                        throw new RuntimeException(e);
//                    }
//                });
//
//                // 在锁释放后，在外部控制流中执行跳转
//                if (shouldBreakLoop) {
//                    break;
//                }
//            } else {
//                RcsLog.algorithmLog.info("{} AGV已存在分段任务或者任务路径，跳过待机", rcsAgv.getAgvId());
//            }
        }
    }

    /**
     * 获取可用待机点
     *
     * @param rcsAgv AGV
     * @return 点位
     */
    private RcsPoint getAvailableStandbyPoint(RcsAgv rcsAgv) {
        RcsPoint returnRcsPoint = null;
        //待机模式 -2不待机 -1自定义 0最近待机点 1绑定待机点
        Integer standbyMode = taskYaml.getStandbyCommon().getStandbyMode();
        switch (standbyMode) {
            case -2:
                //不待机

                break;
            case -1:
                //自定义
                RcsLog.algorithmLog.error("{} 未实现的自定义功能", rcsAgv.getAgvId());
                break;
            case 1:
                //绑定待机点
                returnRcsPoint = standbyFilter.filterStandbyByBinding(rcsAgv);
                break;
            case 0:
            default:
                //最近待机点
                returnRcsPoint = standbyFilter.filterStandbyByDistance(rcsAgv);
                break;
        }
        return returnRcsPoint;
    }
}
