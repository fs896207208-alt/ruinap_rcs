package com.ruinap.core.task.structure.cycle;

import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.enums.task.TaskStateEnum;
import com.ruinap.infra.log.RcsLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务生命周期管理类
 *
 * @author qianye
 * @create 2025-03-10 14:47
 */
public class TaskLifecycleManage {

    /**
     * 状态处理器
     */
    private final static List<TaskStateHandler> HANDLERS = new ArrayList<>(2);

    /**
     * 初始化处理器
     */
    public static void initialize() {
        // 注册多个处理器
        registerHandler(new RcsDataHandler());
        registerHandler(new BroadcasterHandler());

        RcsLog.consoleLog.info("任务状态管理器 初始化成功");
        RcsLog.algorithmLog.info("任务状态管理器 初始化成功");
    }

    /**
     * 注册处理器
     *
     * @param handler 处理器
     */
    public static void registerHandler(TaskStateHandler handler) {
        // 将处理器添加到列表中
        HANDLERS.add(handler);
    }

    /**
     * 处理任务状态变化，调用对应状态的所有注册处理器。
     * 如果指定状态没有注册处理器，则不执行任何操作。
     *
     * @param task     任务对象
     * @param newState 新状态
     */
    public static void stateChange(RcsTask task, TaskStateEnum newState) {
        // 如果新状态与当前状态相同，则不执行任何操作
//        if (TaskStateEnum.isEnumByCode(newState, task.getTaskState())) {
//            return;
//        }
//
//        //  如果是取货完成状态
//        if (TaskStateEnum.isEnumByCode(TaskStateEnum.TAKE_FINISH, newState.code)) {
//            //  添加取货完成
//            RcsEquipmentManage.addLoadFinish(RcsUtils.commonPointParse(task.getOrigin()));
//        }
//        //获取旧状态
//        Integer oldState = task.getTaskState();
//        // 记录日志
//        RcsLog.taskLog.info(RcsLog.formatTemplateRandom(task.getTaskCode(), StrUtil.format("任务状态发生变化：{} -> {}", TaskStateEnum.fromEnum(oldState).description, newState.description)));
//        // 获取新状态对应的处理器并执行
//        HANDLERS.forEach(handler -> {
//            try {
//                handler.handle(task, TaskStateEnum.fromEnum(oldState), newState);
//            } catch (Exception e) {
//                // 记录异常
//                RcsException.logException(e);
//            }
//        });
    }
}
