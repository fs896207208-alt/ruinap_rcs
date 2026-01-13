package com.ruinap.core.equipment.pojo;

import cn.hutool.core.annotation.Alias;
import lombok.Data;

import java.io.Serializable;

/**
 * AGV车体任务
 *
 * @author qianye
 * @create 2025-04-01 11:27
 */
@Data
public class AgvTask implements Serializable {
    /**
     * 任务状态，0无任务，1有任务，2已完成，3已取消
     */
    @Alias("task_state")
    private Integer taskState;
    /**
     * 任务是否已接收开始指令 0否 1是
     */
    @Alias("task_start")
    private Integer taskStart;
    /**
     * 中断状态，0正常，1中断
     */
    @Alias("interrupt_state")
    private Integer interruptState;
    /**
     * 任务ID
     */
    @Alias("task_id")
    private String taskId;
    /**
     * 任务起点
     */
    @Alias("task_start_id")
    private Integer taskStartId;
    /**
     * 任务终点
     */
    @Alias("task_end_id")
    private Integer taskEndId;
    /**
     * 动作号 1取货 2放货 3充电 4对接设备
     */
    @Alias("task_act")
    private Integer taskAct;
    /**
     * 动作参数
     */
    @Alias("task_param")
    private Integer taskParam;
    /**
     * AGV 接收到的路径集个数，比调度的路径编号大1
     */
    @Alias("set_qua")
    private Integer setQua;
    /**
     * AGV当前在第几个路径集上
     */
    @Alias("set_id")
    private Integer setId;
    /**
     * AGV在当前路径集上的状态，0未开始，1执行中，2已完成
     */
    @Alias("set_state")
    private Integer setState;
    /**
     * 路径集起点ID
     */
    @Alias("set_start_id")
    private Integer setStartId;
    /**
     * 路径集终点ID
     */
    @Alias("set_end_id")
    private Integer setEndId;
    /**
     * 子路径个数
     */
    @Alias("path_qua")
    private Integer pathQua = -1;
    /**
     * 路径起点
     */
    @Alias("path_start_id")
    private Integer pathStartId;
    /**
     * 路径终点
     */
    @Alias("path_end_id")
    private Integer pathEndId;
    /**
     * 任务描述
     */
    @Alias("task_description")
    private String taskDescription;
    /**
     * 错误信息
     */
    @Alias("TASK_Err_Msg")
    private String taskErrMsg;
}
