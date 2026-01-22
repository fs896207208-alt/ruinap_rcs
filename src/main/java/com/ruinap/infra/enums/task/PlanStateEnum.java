package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;

/**
 * 规划状态枚举
 *
 * @author qian
 * @create 2025-03-24 11:00
 */
@AllArgsConstructor
public enum PlanStateEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 待检查
     */
    CHECK(0, "待检查"),
    DOCK_DEVICE(5, "对接设备中"),
    NEW(10, "新任务"),
    AGAIN_PLAN(20, "二次规划"),
    WAIT_START(30, "待开始"),
    RUN(40, "运行中"),
    PAUSE(50, "暂停任务"),
    ACTION(60, "动作中"),
    /**
     * 中断任务只是中断当前路径，然后重新下发新路径
     */
    @Deprecated(since = "自3.0版本后该类已不再使用")
    INTERRUPT(70, "中断任务"),
    /**
     * 取消任务是把任务取消
     */
    CANCEL(80, "取消任务"),
    FINISH(100, "任务完成"),
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
    public static PlanStateEnum fromEnum(Integer code) {
        for (PlanStateEnum obj : values()) {
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
    public static boolean isEnumByCode(PlanStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
