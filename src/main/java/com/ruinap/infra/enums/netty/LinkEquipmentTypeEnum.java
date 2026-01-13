package com.ruinap.infra.enums.netty;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备种类枚举类
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@Getter
@AllArgsConstructor
public enum LinkEquipmentTypeEnum {
    /**
     * AGV
     */
    AGV("AGV"),

    /**
     * 充电桩
     */
    CHARGE_PILE("ChargePile"),

    /**
     * 中转系统
     */
    TRANSFER("Transfer");

    /**
     * 设备种类对象
     * <p>
     * -- GETTER --
     * 获取设备种类对象
     */
    private final String equipmentType;

    /**
     * 根据设备种类字符串获取对应的设备种类对象
     *
     * @param equipmentType 设备种类字符串
     * @return 设备种类对象
     */
    public static LinkEquipmentTypeEnum fromEquipmentType(String equipmentType) {
        for (LinkEquipmentTypeEnum tempEquipmentType : values()) {
            if (tempEquipmentType.equipmentType.equalsIgnoreCase(equipmentType)) {
                return tempEquipmentType;
            }
        }
        return null;
    }

    /**
     * 判断是否指定枚举对象
     *
     * @param code 枚举码
     * @return 是否一致 true/false
     */
    public static boolean isEnumByCode(LinkEquipmentTypeEnum obj, String code) {
        return obj.equals(fromEquipmentType(code));
    }
}
