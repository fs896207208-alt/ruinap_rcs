package com.ruinap.infra.config.pojo.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 充电配置类
 */
@Getter
@Setter
public class ChargeCommonEntity {
    /**
     * AGV允许充电阈值
     */
    @JsonProperty("allow_charge_power")
    private Integer allowChargePower;

    /**
     * AGV最低工作电量
     */
    @JsonProperty("lowest_work_power")
    private Integer lowestWorkPower;

    /**
     * 下班AGV允许充电阈值
     */
    @JsonProperty("allow_charge_power_laidoff")
    private Integer allowChargePowerLaidoff;

    /**
     * 允许时间段内的充电阈值
     */
    @JsonProperty("allow_charge_power_time_period")
    private Integer allowChargePowerTimePeriod;

    /**
     * 充电桩匹配模式
     */
    @JsonProperty("chargepile_match_mode")
    private Integer chargepileMatchMode;

    /**
     * 充电桩绑定AGV
     */
    @JsonProperty("charge_binding_agv")
    private Map<String, String> chargeBindingAgv;

    /**
     * 充电桩匹配区域
     */
    @JsonProperty("charge_pile_area")
    private Map<String, String> chargePileArea;

    /**
     * 充电桩匹配楼层
     */
    @JsonProperty("charge_pile_floor")
    private Map<String, String> chargePileFloor;

    /**
     * 充电桩匹配时间段
     */
    @JsonProperty("charge_times_agv")
    private Map<Integer, Integer> chargeTimesAgv;
}
