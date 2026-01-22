package com.ruinap.core.task.domain;

import cn.hutool.core.annotation.Alias;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 调度任务数据库映射类
 *
 * @author qianye
 * @create 2025-03-10 10:14
 */
@Data
public class RcsTask implements Serializable {

    /**
     * 主键
     */
    private Integer id;

    /**
     * 任务组
     */
    @Alias("task_group")
    private String taskGroup;

    /**
     * 任务编号
     */
    @Alias("task_code")
    private String taskCode;

    /**
     * 任务类型 0搬运任务 1充电任务 2停靠任务 3临时任务 4避让任务
     */
    @Alias("task_type")
    private Integer taskType;

    /**
     * 是否管制 0无管制 1有管制
     */
    @Alias("is_control")
    private Integer isControl;

    /**
     * 任务管制点
     */
    @Alias("task_control")
    private String taskControl;

    /**
     * 设备类型 0全类型 1差速潜伏式，2单舵叉车式
     */
    @Alias("equipment_type")
    private Integer equipmentType;

    /**
     * 设备标签
     */
    @Alias("equipment_label")
    private String equipmentLabel;

    /**
     * 设备编号
     */
    @Alias("equipment_code")
    private String equipmentCode;

    /**
     * 托盘类型
     */
    @Alias("pallet_type")
    private Integer palletType;

    /**
     * 起点楼层
     */
    @Alias("origin_floor")
    private Integer originFloor;

    /**
     * 起点区域
     */
    @Alias("origin_area")
    private String originArea;

    /**
     * 起点
     */
    @Alias("origin")
    private String origin;

    /**
     * 终点楼层
     */
    @Alias("destin_floor")
    private Integer destinFloor;

    /**
     * 终点区域
     */
    @Alias("destin_area")
    private String destinArea;

    /**
     * 终点
     */
    @Alias("destin")
    private String destin;

    /**
     * 优先级
     */
    @Alias("task_priority")
    private Integer taskPriority;

    /**
     * 置顶时间
     */
    @Alias("priority_time")
    private Integer priorityTime;

    /**
     * 任务顺序
     */
    @Alias("task_rank")
    private Integer taskRank;

    /**
     * 任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 97取货运行中 98卸货运行中 99运行中
     */
    @Alias("task_state")
    private Integer taskState;

    /**
     * 下发状态 0未下发 1已下发
     */
    @Alias("send_state")
    private Integer sendState;

    /**
     * 中断状态 0默认 1中断任务 2取消任务 3上位取消
     */
    @Alias("interrupt_state")
    private Integer interruptState;

    /**
     * 执行系统 0AGV 1PDA 2输送线 3电梯
     */
    @Alias("executive_system")
    private Integer executiveSystem;

    /**
     * 创建时间
     */
    @Alias("create_time")
    private Date createTime;

    /**
     * 开始时间
     */
    @Alias("start_time")
    private Date startTime;

    /**
     * 完成时间
     */
    @Alias("finish_time")
    private Date finishTime;

    /**
     * 最后更新时间
     */
    @Alias("update_time")
    private Date updateTime;

    /**
     * 最终任务 0是 1否
     */
    @Alias("finally_task")
    private Integer finallyTask;

    /**
     * 任务来源
     */
    @Alias("task_source")
    private String taskSource;

    /**
     * 备注
     */
    @Alias("remark")
    private String remark;

    /**
     * 任务时长
     */
    @Alias("task_duration")
    private Integer taskDuration;
}
