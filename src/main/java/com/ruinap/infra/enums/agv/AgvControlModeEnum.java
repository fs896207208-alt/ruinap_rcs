package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV控制模式
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvControlModeEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 单车调试
     */
    DEBUG(0, "单车调试"),
    /**
     * 点对点
     */
    P2P(1, "点对点"),
    /**
     * 调度
     */
    RCS(2, "调度"),
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
    public static AgvControlModeEnum fromEnum(Integer code) {
        for (AgvControlModeEnum obj : values()) {
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
    public static boolean isEnumByCode(AgvControlModeEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
