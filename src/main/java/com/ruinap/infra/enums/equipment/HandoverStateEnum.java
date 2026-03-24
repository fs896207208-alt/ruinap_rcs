package com.ruinap.infra.enums.equipment;

import lombok.AllArgsConstructor;

/**
 * 交接设备状态枚举
 *
 * @author qianye
 * @create 2025-07-03 17:21
 */
@AllArgsConstructor
public enum HandoverStateEnum {
    /**
     * 未知
     */
    NULL(0, "其他"),
    /**
     * 允许取货
     */
    ALLOW_PICKUP(1, "允许取货"),
    /**
     * 允许放货
     */
    ALLOW_UNLOAD(2, "允许放货"),
    /**
     * 等待取货
     */
    WAIT_PICKUP(3, "等待取货"),
    /**
     * 等待放货
     */
    WAIT_UNLOAD(4, "等待放货"),
    /**
     * 运行中
     */
    RUN(5, "运行中"),
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
    public static HandoverStateEnum fromEnum(Integer code) {
        for (HandoverStateEnum obj : values()) {
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
    public static boolean isEnumByCode(HandoverStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
