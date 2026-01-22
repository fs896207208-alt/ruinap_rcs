package com.ruinap.infra.enums.charge;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 充电桩空闲状态枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@Getter
@AllArgsConstructor
public enum ChargeIdleEnum {
    /**
     * 未知
     */
    NULL(null, "无"),
    /**
     * 未知
     */
    UNKNOWN(-1, "未知"),
    /**
     * 空闲
     */
    IDLE(0, "空闲"),
    /**
     * 占用
     */
    OCCUPATION(1, "占用"),
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
    public static ChargeIdleEnum fromEnum(Integer code) {
        for (ChargeIdleEnum obj : values()) {
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
    public static boolean isEnumByCode(ChargeIdleEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
