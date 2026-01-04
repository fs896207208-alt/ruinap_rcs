package com.ruinap.infra.config.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruinap.infra.config.pojo.interactions.AirShowerEntity;
import com.ruinap.infra.config.pojo.interactions.AutomaticDoorEntity;
import com.ruinap.infra.config.pojo.interactions.ConveyorLineEntity;
import com.ruinap.infra.config.pojo.interactions.ElevatorEntity;
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
     * 输送线配置
     */
    @JsonProperty("conveyor_line")
    private List<ConveyorLineEntity> conveyorLineEntities;

    /**
     * 自动门配置
     */
    @JsonProperty("automatic_doors")
    private List<AutomaticDoorEntity> automaticDoorEntities;

    /**
     * 电梯配置
     */
    @JsonProperty("elevators")
    private List<ElevatorEntity> elevatorEntities;

    /**
     * 风淋室配置
     */
    @JsonProperty("airShowers")
    private List<AirShowerEntity> airShowers;
}
