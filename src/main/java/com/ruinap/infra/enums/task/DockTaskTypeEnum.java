package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * 对接任务类型枚举
 *
 * @author qian
 * @create 2024-06-05 11:00
 */
@AllArgsConstructor
public enum DockTaskTypeEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 下面是 DockDevice 的任务类型
     */
    ELEVATOR(2, "电梯对接任务"),
    AIRSHOWER(3, "风淋室对接任务"),
    CONVEYORLINE(4, "输送线对接任务"),
    ;

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
    public static DockTaskTypeEnum fromEnum(Integer code) {
        for (DockTaskTypeEnum obj : values()) {
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
    public static boolean isEnumByCode(DockTaskTypeEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
