package com.ruinap.infra.config.pojo.interactions;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 交接设备配置
 *
 * @author qianye
 * @create 2025-03-12 15:56
 */
@Getter
@Setter
public class HandoverDeviceEntity {

    /**
     * 交接设备编号
     */
    private String code;

    /**
     * 启用状态，设置为false则不读取配置
     */
    private boolean enable;

    /**
     * 设备类型
     */
    @JsonProperty("device_type")
    private String deviceType;

    /**
     * 交接设备关联点位
     */
    @JsonProperty("relevancy_point")
    private List<String> relevancyPoint;

    /**
     * 交接设备对接点
     */
    @JsonProperty("docking_point")
    private String dockingPoint;

    /**
     * 取货模式 0任务下发前询问是否可取货 1在对接点询问是否可取货
     */
    @JsonProperty("pickup_mode")
    private int pickupMode;

    /**
     * 卸货模式 0任务下发前询问是否可卸货 1在对接点询问是否可卸货
     */
    @JsonProperty("unload_mode")
    private int unloadMode;

    /**
     * 对接提前点
     */
    @JsonProperty("docking_advance")
    private int dockingAdvance;
}
