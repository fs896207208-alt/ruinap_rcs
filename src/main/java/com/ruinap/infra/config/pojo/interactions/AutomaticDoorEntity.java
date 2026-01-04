package com.ruinap.infra.config.pojo.interactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 自动门配置
 *
 * @author qianye
 * @create 2025-03-12 15:57
 */
@Getter
@Setter
public class AutomaticDoorEntity {

    /**
     * 设备编号
     */
    private String code;

    /**
     * 启用状态，设置为false则不读取配置
     */
    private boolean enable;

    /**
     * 自动门点位
     */
    private String point;

    /**
     * 对接类型 0穿越 1进出
     */
    private int type;

    /**
     * 自动门开门提前点
     */
    @JsonProperty("open_advance")
    private int openAdvance;

    /**
     * 自动门关门延迟点
     */
    @JsonProperty("close_delay")
    private int closeDelay;
}
