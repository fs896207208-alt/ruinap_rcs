package com.ruinap.infra.enums.equipment;

import lombok.AllArgsConstructor;

/**
 * 风淋室状态枚举
 *
 * @author qianye
 * @create 2025-07-03 17:21
 */
@AllArgsConstructor
public enum AirShowerStateEnum {
    /**
     * 其他
     */
    NULL(0, "其他"),
    /**
     * 风淋中
     */
    IN_SHOWER(1, "风淋中"),
    /**
     * 风淋完成
     */
    FINISH_SHOWER(2, "风淋完成"),
    ;

    /**
     * 状态代码
     */
    public final Integer code;
    /**
     * 状态描述
     */
    public final String name;

    /**
     * 根据状态码获取对应的枚举对象
     *
     * @param code 状态码
     * @return 枚举对象，如果找不到对应的状态代码，返回其他枚举对象
     */
    public static AirShowerStateEnum fromEnum(Integer code) {
        for (AirShowerStateEnum obj : values()) {
            if (obj.code != null && obj.code.equals(code)) {
                return obj;
            }
        }
        return NULL;
    }

    /**
     * 判断是否指定枚举对象
     *
     * @param code 状态码
     * @return 是否一致 true/false
     */
    public static boolean isEnumByCode(AirShowerStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
