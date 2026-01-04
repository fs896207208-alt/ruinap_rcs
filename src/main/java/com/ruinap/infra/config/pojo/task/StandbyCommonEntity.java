package com.ruinap.infra.config.pojo.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 待机配置类
 */
@Getter
@Setter
public class StandbyCommonEntity {
    /**
     * 待机模式
     */
    @JsonProperty("standby_mode")
    private Integer standbyMode;

    /**
     * 绑定待机点
     */
    @JsonProperty("standby_point_binding")
    private Map<String, String> standbyPointBinding;

    /**
     * 待机屏蔽点
     */
    @JsonProperty("standby_point_shield")
    private Map<String, String> standbyPointShield;
}
