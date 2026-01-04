package com.ruinap.infra.config.pojo.interactions;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 电梯配置
 *
 * @author qianye
 * @create 2025-03-12 15:58
 */
@Getter
@Setter
public class ElevatorEntity implements Serializable {

    /**
     * 设备编号
     */
    private String code;

    /**
     * 启用状态，设置为false则不读取配置
     */
    private boolean enable;
    /**
     * 电梯所有电梯门
     */
    private List<String> occupancys;

    /**
     * 电梯传送点
     */
    private List<TeleportBody> teleports;

    /**
     * 交互点
     */
    private List<Map<String, InteractionsBody>> interactions;
}

