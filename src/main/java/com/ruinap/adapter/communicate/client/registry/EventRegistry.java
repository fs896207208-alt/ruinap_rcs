package com.ruinap.adapter.communicate.client.registry;


import com.ruinap.adapter.communicate.base.event.AbstractClientEvent;
import com.ruinap.adapter.communicate.event.ChargePileTcpEvent;
import com.ruinap.adapter.communicate.event.SlamoptoAgvWebSocketClientEvent;
import com.ruinap.adapter.communicate.event.TransferWebSocketClientEvent;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件注册器
 *
 * @author qianye
 * @create 2025-02-27 14:51
 */
public class EventRegistry {
    /**
     * 事件处理器注册集合
     */
    private static final Map<String, AbstractClientEvent> REGISTRY = new ConcurrentHashMap<>();

    static {
        // 注册司岚品牌事件
        registerEvent(LinkEquipmentTypeEnum.AGV.getEquipmentType(), new SlamoptoAgvWebSocketClientEvent());
        // 注册充电桩事件
        registerEvent(LinkEquipmentTypeEnum.CHARGE_PILE.getEquipmentType(), new ChargePileTcpEvent());
        // 注册中转系统事件
        registerEvent(LinkEquipmentTypeEnum.TRANSFER.getEquipmentType(), new TransferWebSocketClientEvent());

        // 注册其他处理器
    }

    /**
     * 注册品牌事件
     *
     * @param equipmentType 设备名称
     * @param event         处理器
     */
    public static void registerEvent(String equipmentType, AbstractClientEvent event) {
        REGISTRY.put(equipmentType, event);
    }

    /**
     * 获取事件处理器
     *
     * @param equipmentType 设备名称
     * @return 事件处理器
     */
    public static AbstractClientEvent getEvent(String equipmentType) {
        return REGISTRY.getOrDefault(equipmentType, REGISTRY.get(equipmentType.toLowerCase()));
    }
}

