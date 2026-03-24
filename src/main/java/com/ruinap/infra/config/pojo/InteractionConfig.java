package com.ruinap.infra.config.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruinap.infra.config.pojo.interactions.AirShowerEntity;
import com.ruinap.infra.config.pojo.interactions.AutomaticDoorEntity;
import com.ruinap.infra.config.pojo.interactions.ElevatorEntity;
import com.ruinap.infra.config.pojo.interactions.HandoverDeviceEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 交互配置类
 *
 * @author qianye
 * @create 2025-03-12 16:00
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractionConfig {

    /**
     * 交接设备配置
     */
    @JsonProperty("handover_device")
    private List<HandoverDeviceEntity> handoverDevice;

    /**
     * 自动门配置
     */
    @JsonProperty("automatic_doors")
    private List<AutomaticDoorEntity> automaticDoors;

    /**
     * 电梯配置
     * 注意：电梯配置会自动生成反转的配置，如1楼到2楼的配置会自动生成2楼到1楼的配置，避免重复配置，但是如果bridge_point未生成反转数据，可能会出现我map没有配置bidirectional: true，但是elevators还是生成反转数据的情况
     */
    @JsonProperty("elevators")
    private List<ElevatorEntity> elevators;

    /**
     * 风淋室配置
     */
    @JsonProperty("airShowers")
    private List<AirShowerEntity> airShowers;
}
