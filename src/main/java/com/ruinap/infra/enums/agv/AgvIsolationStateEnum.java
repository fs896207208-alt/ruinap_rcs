package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV隔离状态
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvIsolationStateEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 未隔离
     */
    NORMAL(0, "未隔离"),
    /**
     * 在线隔离
     */
    ONLINE_ISOLATION(1, "在线隔离"),
    /**
     * 离线隔离
     */
    OFFLINE_ISOLATION(2, "离线隔离"),
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
     * @return 枚举对象，如果找不到对应的状态代码，返回NULL枚举
     */
    public static AgvIsolationStateEnum fromEnum(Integer code) {
        for (AgvIsolationStateEnum obj : values()) {
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
    public static boolean isEnumByCode(AgvIsolationStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
