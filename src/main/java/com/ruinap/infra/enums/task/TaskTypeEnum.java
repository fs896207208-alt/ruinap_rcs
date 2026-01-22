package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * 任务类型枚举
 *
 * @author qian
 * @create 2024-06-05 11:00
 */
@AllArgsConstructor
public enum TaskTypeEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 无
     */
    CARRY(0, "搬运任务"),
    CHARGE(1, "充电任务"),
    DOCK(2, "停靠任务"),
    TEMPORARY(3, "临时任务"),
    AVOIDANCE(4, "避让任务"),
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
    public static TaskTypeEnum fromEnum(Integer code) {
        for (TaskTypeEnum obj : values()) {
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
    public static boolean isEnumByCode(TaskTypeEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
