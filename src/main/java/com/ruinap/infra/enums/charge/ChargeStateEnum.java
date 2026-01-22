package com.ruinap.infra.enums.charge;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 充电桩状态枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@Getter
@AllArgsConstructor
public enum ChargeStateEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 离线
     */
    OFFLINE(0, "离线"),
    /**
     * 在线
     */
    ONLINE(1, "在线"),
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
    public static ChargeStateEnum fromEnum(Integer code) {
        for (ChargeStateEnum obj : values()) {
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
    public static boolean isEnumByCode(ChargeStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
