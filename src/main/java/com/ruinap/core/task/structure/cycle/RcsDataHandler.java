package com.ruinap.core.task.structure.cycle;


import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.task.TaskStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;

import java.util.Date;

/**
 * 调度数据处理类
 *
 * @author qianye
 * @create 2025-03-10 15:19
 */
@Component
public class RcsDataHandler implements TaskStateHandler {
    @Autowired
    private AgvManager agvManager;

    /**
     * 处理状态变化
     *
     * @param task     任务
     * @param oldState 旧状态
     * @param newState 新状态
     */
    @Override
    public void handle(RcsTask task, TaskStateEnum oldState, TaskStateEnum newState) {
        // 根据设备编码获取设备信息
        String equipmentCode = task.getEquipmentCode();
        // 根据设备编码获取设备信息
        RcsAgv rcsAgv = agvManager.getRcsAgvByCode(equipmentCode);
        //如果任务状态为下发状态
        if (rcsAgv != null && TaskStateEnum.isEnumByCode(TaskStateEnum.ISSUED, newState.code)) {
            rcsAgv.setTaskOrigin(task.getOrigin());
            rcsAgv.setTaskDestin(task.getDestin());

            //设置任务开始时间
            task.setStartTime(new Date());
        }

        // 更新任务状态到数据库
        task.setTaskState(newState.code);

        //如果任务状态为完成状态
        if (rcsAgv != null) {
            if (TaskStateEnum.isEnumByCode(TaskStateEnum.FINISH, newState.code) || TaskStateEnum.isEnumByCode(TaskStateEnum.CANCEL, newState.code) || TaskStateEnum.isEnumByCode(TaskStateEnum.HIGHER_CANCEL, newState.code)) {
                //清空任务起点和终点
                rcsAgv.setTaskOrigin("");
                rcsAgv.setTaskDestin("");
            }
        }
    }
}
