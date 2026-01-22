package com.ruinap.core.task.structure.cycle;

import cn.hutool.json.JSONObject;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.task.TaskStateEnum;

/**
 * WebSocket广播
 *
 * @author qianye
 * @create 2025-03-10 15:21
 */
public class BroadcasterHandler implements TaskStateHandler {

    /**
     * 该类会将任务状态变化广播到所有WebSocket客户端，方便其他客户端监控任务状态变化
     *
     * @param task     任务
     * @param oldState 旧状态
     * @param newState 新状态
     */
    @Override
    public void handle(RcsTask task, TaskStateEnum oldState, TaskStateEnum newState) {
        JSONObject entries = new JSONObject();
        entries.set("id", task.getId());
        entries.set("task_group", task.getTaskGroup());
        entries.set("task_code", task.getTaskCode());
        entries.set("task_type", task.getTaskType());
        entries.set("is_control", task.getIsControl());
        entries.set("task_control", task.getTaskControl());
        entries.set("equipment_type", task.getEquipmentType());
        entries.set("equipment_code", task.getEquipmentCode());
        entries.set("pallet_type", task.getPalletType());
        entries.set("origin_floor", task.getOriginFloor());
        entries.set("origin_area", task.getOriginArea());
        entries.set("origin", task.getOrigin());
        entries.set("destin_floor", task.getDestinFloor());
        entries.set("destin_area", task.getDestinArea());
        entries.set("destin", task.getDestin());
        entries.set("task_priority", task.getTaskPriority());
        entries.set("task_rank", task.getTaskRank());
        entries.set("old_state", oldState.code);
        entries.set("old_desc", oldState.description);
        entries.set("new_state", newState.code);
        entries.set("new_desc", newState.description);
        entries.set("send_state", task.getSendState());
        entries.set("interrupt_state", task.getInterruptState());
        entries.set("executive_system", task.getExecutiveSystem());
        entries.set("create_time", task.getCreateTime());
        entries.set("start_time", task.getStartTime());
        entries.set("finish_time", task.getFinishTime());
        entries.set("update_time", task.getUpdateTime());
        entries.set("finally_task", task.getFinallyTask());
        entries.set("task_source", task.getTaskSource());
        entries.set("task_remark", task.getRemark());
        entries.set("task_duration", task.getTaskDuration());
        //获取状态上报指令
//        JSONObject jsonObject = BusinessCommand.taskStateChange(entries);
        // 处理格式化后的日志消息
//        NettyServer.sendMessageAll(ProtocolEnum.WEBSOCKET_SERVER, ServerRouteEnum.BUSINESS, jsonObject.toStringPretty());
    }
}
