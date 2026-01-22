package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * AGV动作号枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum TaskActionEnum {
    /**
     * 未知
     */
    NULL(-1, "未知"),
    /**
     * 未知
     */
    NOT(0, "无"),
    /**
     * 取货中
     */
    PICKUP(1, "取货"),
    /**
     * 卸货中
     */
    UNLOAD(2, "卸货"),
    /**
     * 充电中
     */
    CHARGE(3, "充电"),
    /**
     * 对接设备
     */
    BUTTED(4, "对接"),
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
    public static TaskActionEnum fromEnum(Integer code) {
        for (TaskActionEnum obj : values()) {
            if (obj.code.equals(code)) {
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
    public static boolean isEnumByCode(TaskActionEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
