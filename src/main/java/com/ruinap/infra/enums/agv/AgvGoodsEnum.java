package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV货物枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvGoodsEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 无货
     */
    NONE(0, "无货"),
    /**
     * 单左货
     */
    LEFT(1, "单左货"),
    /**
     * 单右货
     */
    RIGHT(2, "单右货"),
    /**
     * 左右货
     */
    LEFT_RIGHT(3, "左右货"),
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
    public static AgvGoodsEnum fromEnum(Integer code) {
        for (AgvGoodsEnum obj : values()) {
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
    public static boolean isEnumByCode(AgvGoodsEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
