package com.ruinap.infra.config.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruinap.infra.config.pojo.map.RcsMapSource;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * RCS地图配置类
 *
 * @author qianye
 * @create 2024-10-30 16:22
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapConfig {
    /**
     * 地图配置
     */
    @JsonProperty("rcs_map")
    private RcsMapSource rcsMap;

    /**
     * 地图桥接点配置
     */
    @JsonProperty("bridge_point")
    private LinkedHashMap<String, LinkedHashMap<String, String>> bridgePoint;

    /**
     * 待机点配置
     */
    @JsonProperty("standby_point")
    private LinkedHashMap<Integer, ArrayList<Integer>> standbyPoint;

    /**
     * 待机点屏蔽配置
     */
    @JsonProperty("standby_shield_point")
    private LinkedHashMap<Integer, ArrayList<Integer>> standbyShieldPoint;

    /**
     * 充电点配置
     */
    @JsonProperty("charge_point")
    private LinkedHashMap<Integer, ArrayList<Integer>> chargePoint;

    /**
     * 管制区配置
     */
    @JsonProperty("control_area")
    private LinkedHashMap<Integer, LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>>> controlArea;

    /**
     * 管制点配置
     */
    @JsonProperty("control_point")
    private LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, ArrayList<Integer>>>> controlPoint;

    /**
     * 避让点配置
     */
    @JsonProperty("avoidance_point")
    private LinkedHashMap<Integer, LinkedHashMap<String, ArrayList<Integer>>> avoidancePoint;
}
