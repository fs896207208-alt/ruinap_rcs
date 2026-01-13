package com.ruinap.adapter.communicate.client.registry;

import com.slamopto.common.enums.BrandEnum;
import com.slamopto.common.enums.LinkEquipmentTypeEnum;
import com.slamopto.communicate.base.event.AbstractClientEvent;
import com.slamopto.communicate.event.ChargePileTcpEvent;
import com.slamopto.communicate.event.SlamoptoAgvWebSocketClientEvent;
import com.slamopto.communicate.event.TransferWebSocketClientEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private static final ConcurrentMap<String, AbstractClientEvent> REGISTRY = new ConcurrentHashMap<>();

    static {
        // 注册司岚品牌事件
        registerEvent(BrandEnum.SLAMOPTO.getBrand(), LinkEquipmentTypeEnum.AGV.getEquipmentType(), new SlamoptoAgvWebSocketClientEvent());
        // 注册充电桩事件
        registerEvent(BrandEnum.DINGJIAN.getBrand(), LinkEquipmentTypeEnum.CHARGE_PILE.getEquipmentType(), new ChargePileTcpEvent());
        // 注册中转系统事件
        registerEvent(BrandEnum.SLAMOPTO.getBrand(), LinkEquipmentTypeEnum.TRANSFER.getEquipmentType(), new TransferWebSocketClientEvent());

        // 注册其他处理器
    }

    /**
     * 注册品牌事件
     *
     * @param brand         品牌
     * @param equipmentType 设备名称
     * @param event         处理器
     */
    public static void registerEvent(String brand, String equipmentType, AbstractClientEvent event) {
        REGISTRY.put(createKey(brand, equipmentType), event);
    }

    /**
     * 获取事件处理器
     *
     * @param brand         品牌
     * @param equipmentType 设备名称
     * @return 事件处理器
     */
    public static AbstractClientEvent getEvent(String brand, String equipmentType) {
        String key = createKey(brand, equipmentType);
        return REGISTRY.getOrDefault(key, REGISTRY.get(equipmentType.toLowerCase()));
    }

    /**
     * 创建 key
     *
     * @param brand         品牌
     * @param equipmentType 设备名称
     * @return key
     */
    private static String createKey(String brand, String equipmentType) {
        return brand.toLowerCase() + "::" + equipmentType.toLowerCase();
    }
}

