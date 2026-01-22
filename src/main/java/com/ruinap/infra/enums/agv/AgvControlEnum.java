package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV控制权
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvControlEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 调度
     */
    RCS(0, "调度"),
    /**
     * 其他
     */
    OTHER(1, "其他"),
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
    public static AgvControlEnum fromEnum(Integer code) {
        for (AgvControlEnum obj : values()) {
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
    public static boolean isEnumByCode(AgvControlEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
