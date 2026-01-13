package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AGV急停枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
@Getter
public enum AgvEstopEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 未急停
     */
    NORMAL(0, "未急停"),
    /**
     * 急停中
     */
    ESTOP(1, "急停中"),
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
     * @return 枚举对象，如果找不到对应的状态代码，返回null
     */
    public static AgvEstopEnum fromEnum(Integer code) {
        for (AgvEstopEnum obj : values()) {
            if (obj.code != null && obj.code.equals(code)) {
                return obj;
            }
        }
        return NULL;
    }

    /**
     * 判断是否未急停
     *
     * @param code 状态码
     * @return 是否未急停 true/false
     */
    public static boolean isNormal(Integer code) {
        return NORMAL.code.equals(code);
    }

    /**
     * 判断是否急停中
     *
     * @param code 状态码
     * @return 是否急停中 true/false
     */
    public static boolean isEstop(Integer code) {
        return ESTOP.code.equals(code);
    }
}
