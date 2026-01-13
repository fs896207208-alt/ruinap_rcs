package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV任务状态枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvTaskStateEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 无任务
     */
    NONE(0, "无任务"),
    /**
     * 有任务
     */
    HAVE(1, "有任务"),
    /**
     * 已完成
     */
    FINISH(2, "已完成"),
    /**
     * 已取消
     */
    CANCEL(3, "已取消"),
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
    public static AgvTaskStateEnum fromEnum(Integer code) {
        for (AgvTaskStateEnum obj : values()) {
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
    public static boolean isEnumByCode(AgvTaskStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
