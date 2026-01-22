package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * 充电桩匹配模式枚举
 *
 * @author qian
 * @create 2024-06-05 11:00
 */
@AllArgsConstructor
public enum ChargeMatchModeEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    NOTCHARGE(-2, "不充电"),
    CUSTOMIZE(-1, "自定义"),
    DISTANCE(0, "距离匹配"),
    BINDING(1, "绑定匹配"),
    TYPE(2, "类型匹配"),
    AREA(3, "区域匹配"),
    FLOOR(4, "楼层匹配");

    /**
     * 枚举码
     */
    public final Integer code;

    /**
     * 枚举描述
     */
    public final String description;

    /**
     * 根据枚举码获取对应的枚举对象
     *
     * @param code 枚举码
     * @return 枚举对象，如果找不到对应的枚举码，返回null
     */
    public static ChargeMatchModeEnum fromEnum(Integer code) {
        for (ChargeMatchModeEnum obj : values()) {
            if (obj.code != null && obj.code.equals(code)) {
                return obj;
            }
        }
        return NULL;
    }

    /**
     * 判断是否指定枚举对象
     *
     * @param code 枚举码
     * @return 是否一致 true/false
     */
    public static boolean isEnumByCode(ChargeMatchModeEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
