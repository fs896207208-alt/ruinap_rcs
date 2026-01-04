package com.ruinap.core.map.pojo;

import cn.hutool.core.annotation.Alias;
import lombok.Data;

import java.io.Serializable;

/**
 * 点位动作参数类
 *
 * @author qianye
 * @create 2025-09-09 10:47
 */
@Data
public class PointActionParam implements Serializable {

    /**
     * 别名
     */
    private String name;
    /**
     * 动参类型
     */
    @Alias("act_type")
    private int actType;
    /**
     * 任务类型
     */
    @Alias("task_type")
    private int taskType;
    /**
     * 托盘类型
     */
    @Alias("pallet_type")
    private int palletType;
    /**
     * 库位层
     */
    private int index;
    /**
     * 动作号
     */
    @Alias("task_act")
    private int taskAct;
    /**
     * 任务参数
     */
    @Alias("task_param")
    private String taskParam;
}
