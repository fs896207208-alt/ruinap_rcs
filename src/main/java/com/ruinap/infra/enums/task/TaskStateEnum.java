package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * 任务状态枚举
 *
 * @author qian
 * @create 2024-06-05 11:00
 */
@AllArgsConstructor
public enum TaskStateEnum {
    /**
     * 上位取消
     */
    NULL(null, "未知"),
    /**
     * 上位取消
     */
    HIGHER_CANCEL(-2, "上位取消"),
    CANCEL(-1, "任务取消"),
    FINISH(0, "任务完成"),
    PAUSE(1, "暂停任务"),
    NEW(2, "新任务"),
    ACTION(3, "动作中"),
    TAKE_ACTION(4, "取货动作中"),
    UNLOAD_ACTION(5, "放货动作中"),
    TAKE_FINISH(6, "取货完成"),
    UNLOAD_FINISH(7, "放货完成"),
    ISSUED(90, "已下发"),
    TAKE_RUN(97, "取货运行中"),
    UNLOAD_RUN(98, "卸货运行中"),
    RUN(99, "运行中"),
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
    public static TaskStateEnum fromEnum(Integer code) {
        for (TaskStateEnum obj : values()) {
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
    public static boolean isEnumByCode(TaskStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
