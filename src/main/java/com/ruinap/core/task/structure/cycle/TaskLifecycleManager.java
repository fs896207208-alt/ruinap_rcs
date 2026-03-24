package com.ruinap.core.task.structure.cycle;

import cn.hutool.core.util.StrUtil;
import com.ruinap.core.equipment.manager.HandoverDeviceManager;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.task.TaskStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.core.event.ApplicationEventPublisher;
import com.ruinap.core.task.event.TaskStateChangeEvent;
import com.ruinap.infra.log.RcsLog;

/**
 * 任务生命周期管理类
 *
 * @author qianye
 * @create 2025-03-10 14:47
 */
@Component
public class TaskLifecycleManager {

    @Autowired
    private MapManager mapManager;
    @Autowired
    private HandoverDeviceManager handoverDeviceManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 处理任务状态变化
     * 发布事件，由各监听器解耦处理
     *
     * @param task     任务对象
     * @param newState 新状态
     */
    public void stateChange(RcsTask task, TaskStateEnum newState) {
        // 1. 幂等性校验：如果新状态与当前状态相同，则不执行
        if (TaskStateEnum.isEnumByCode(newState, task.getTaskState())) {
            return;
        }

        // 2. 核心业务同步处理 (如输送线逻辑，如果这部分逻辑很轻，可以留在这里，或者也拆分为监听器)
        // 建议：如果这是核心前置校验，保留在此；如果是副作用，建议也移至监听器
        if (TaskStateEnum.isEnumByCode(TaskStateEnum.TAKE_FINISH, newState.code)) {
            handoverDeviceManager.addLoadFinish(mapManager.getPointByAlias(task.getOrigin()));
        }

        Integer oldStateCode = task.getTaskState();
        TaskStateEnum oldState = TaskStateEnum.fromEnum(oldStateCode);

        // 3. 记录日志
        RcsLog.taskLog.info(RcsLog.getTemplate(2), StrUtil.format("{} 任务状态发生变化：{} -> {}", task.getTaskCode(), oldState.description, newState.description));

        // 4. 发布事件
        TaskStateChangeEvent event = new TaskStateChangeEvent(this, task, oldState, newState);
        eventPublisher.publishEvent(event);
    }
}
