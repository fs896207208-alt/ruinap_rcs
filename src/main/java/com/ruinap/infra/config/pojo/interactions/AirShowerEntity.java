package com.ruinap.infra.config.pojo.interactions;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 风淋室配置
 *
 * @author qianye
 * @create 2025-03-12 15:59
 */
@Getter
@Setter
public class AirShowerEntity {

    /**
     * 设备编号
     */
    private String code;

    /**
     * 启用状态，设置为false则不读取配置
     */
    private boolean enable;

    /**
     * 风淋室点位
     */
    private String point;

    /**
     * 设备的所有占用点
     */
    private List<String> occupancys;

    /**
     * 交互点
     */
    private List<Map<String, AirShowerInteractionBody>> interactions;
}

