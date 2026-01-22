package com.ruinap.core.task.structure.distribution;


import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.taskmode.TaskModeFactory;
import com.ruinap.core.task.structure.taskmode.TaskModeHandle;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;

/**
 * 新任务状态处理类
 *
 * @author qianye
 * @create 2025-03-11 09:57
 */
@Component
public class NewTaskStateHandle implements TaskStateHandle {

    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private AgvManager agvManager;

    /**
     * 处理新任务状态
     *
     * @param rcsTask 任务
     * @return AGV
     */
    @Override
    public RcsAgv handle(RcsTask rcsTask) {
        RcsAgv rcsAgv;
        //获取设备编码
        String equipmentCode = rcsTask.getEquipmentCode();
        //如果设备编码为空，则获取可用AGV
        if (equipmentCode == null || equipmentCode.isEmpty()) {
            //获取可用AGV
            rcsAgv = getAvailableAgv(rcsTask);
        } else {
            //设备编码不是空说明是指定了对应的AGV
            rcsAgv = agvManager.getRcsAgvIdle(equipmentCode);
            if (rcsAgv == null) {
                rcsAgv = agvManager.getAllowCancelChargeRcsAgv(equipmentCode);
            }
        }
        return rcsAgv;
    }

    /**
     * 获取可用AGV
     *
     * @param rcsTask 任务
     * @return agv
     */
    private RcsAgv getAvailableAgv(RcsTask rcsTask) {
        // 获取任务模式工厂
        TaskModeHandle taskModeFactory = TaskModeFactory.getTaskModeFactory(taskYaml.getTaskDistributeMode());
        return taskModeFactory.handle(rcsTask);
    }
}
