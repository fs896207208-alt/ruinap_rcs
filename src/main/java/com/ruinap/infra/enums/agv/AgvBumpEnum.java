package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV防撞条状态
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvBumpEnum {
    /**
     * 无信号
     */
    NULL(null, "无信号"),
    /**
     * 不触发
     */
    NORMAL(0, "不触发"),
    /**
     * 停车
     */
    STOP(1, "停车"),
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
    public static AgvBumpEnum fromEnum(Integer code) {
        for (AgvBumpEnum obj : values()) {
            if (code != null) {
                if (code.equals(obj.code)) {
                    return obj;
                }
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
    public static boolean isEnumByCode(AgvBumpEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
