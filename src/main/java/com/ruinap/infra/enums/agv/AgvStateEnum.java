package com.ruinap.infra.enums.agv;

import lombok.AllArgsConstructor;

/**
 * AGV状态枚举
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@AllArgsConstructor
public enum AgvStateEnum {
    /**
     * 未知
     */
    NULL(null, "未知"),
    /**
     * 离线
     */
    OFFLINE(-1, "离线"),
    /**
     * 待命中
     */
    IDLE(0, "待命中"),
    /**
     * 行走中
     */
    RUN(1, "行走中"),
    /**
     * 动作中
     */
    ACTION(2, "动作中"),
    /**
     * 充电中
     */
    CHARGE(3, "充电中"),
    /**
     * 暂停中
     */
    PAUSE(10, "暂停中"),
    /**
     * 等待中
     */
    WAIT(11, "等待中"),
    /**
     * 切换地图中
     */
    SWITCH(12, "更换地图中"),
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
    public static AgvStateEnum fromEnum(Integer code) {
        for (AgvStateEnum obj : values()) {
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
    public static boolean isEnumByCode(AgvStateEnum obj, Integer code) {
        return obj.equals(fromEnum(code));
    }
}
