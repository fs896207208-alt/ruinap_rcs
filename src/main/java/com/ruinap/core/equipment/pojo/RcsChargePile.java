package com.ruinap.core.equipment.pojo;

import cn.hutool.core.annotation.Alias;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 调度充电桩实体类
 *
 * @author qianye
 * @create 2025-03-04 15:21
 */
@Getter
@Setter
@EqualsAndHashCode
public class RcsChargePile {
    /**
     * 主键
     */
    @Alias("id")
    private Integer id;

    /**
     * 充电桩编号
     */
    @Alias("code")
    private String code;

    /**
     * 充电桩名称
     */
    @Alias("name")
    private String name;

    /**
     * 充电桩区域
     */
    @Alias("area")
    private String area;

    /**
     * 充电桩楼层
     */
    @Alias("floor")
    private Integer floor;

    /**
     * 所属点位
     */
    @Alias("point_id")
    private String pointId;

    /**
     * 电压
     */
    @Alias("voltage")
    private Integer voltage;

    /**
     * 电流
     */
    @Alias("current")
    private Integer current;

    /**
     * 状态 0离线 1在线
     */
    @Alias("state")
    private Integer state;

    /**
     * 隔离状态 0未隔离 1已隔离 2离线隔离
     */
    @Alias("isolation_state")
    private Integer isolationState;

    /**
     * 匹配AGV类型 0通用 1潜伏式 2叉车式
     */
    @Alias("match_type")
    private Integer matchType;

    /**
     * 充电桩模式 -1离线 0自动 1手动
     */
    @Alias("mode")
    private Integer mode;

    /**
     * 伸缩臂状态 0未知 1退回到位 2伸出到位
     */
    @Alias("arm_state")
    private Integer armState;

    /**
     * 空闲状态 -1未知 0空闲 1占用
     */
    @Alias("idle_state")
    private Integer idleState;

    /**
     * 最后更新时间
     */
    @Alias("update_time")
    private Integer updateTime;
}
