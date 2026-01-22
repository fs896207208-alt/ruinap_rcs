package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * 子任务类型枚举
 *
 * @author qian
 * @create 2024-06-05 11:00
 */
@AllArgsConstructor
public enum SubTaskTypeEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 子任务类型枚举
     */
    ORIGIN(0, "前往起点任务"),
    DESTIN(1, "前往终点任务"),
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
    public static SubTaskTypeEnum fromEnum(Integer code) {
        for (SubTaskTypeEnum obj : values()) {
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
    public static boolean isEnumByCode(SubTaskTypeEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
