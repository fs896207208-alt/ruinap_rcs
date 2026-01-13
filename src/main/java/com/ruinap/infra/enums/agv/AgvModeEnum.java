package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV模式枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvModeEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 手动
     */
    MANUAL(0, "手动"),
    /**
     * 自动
     */
    AUTO(1, "自动");

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
     * @return 枚举对象，如果找不到对应的状态代码，返回null
     */
    public static AgvModeEnum fromEnum(Integer code) {
        for (AgvModeEnum obj : values()) {
            if (obj.code != null && obj.code.equals(code)) {
                return obj;
            }
        }
        return NULL;
    }

    /**
     * 判断是否自动
     *
     * @param code 状态码
     * @return 是否自动 true/false
     */
    public static boolean isAuto(Integer code) {
        return AUTO.code.equals(code);
    }

    /**
     * 判断是否手动
     *
     * @param code 状态码
     * @return 是否手动 true/false
     */
    public static boolean isManual(Integer code) {
        return MANUAL.code.equals(code);
    }
}
