package com.ruinap.core.map.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 占用类型枚举
 *
 * @author qian
 * @create 2024-06-05 11:00
 */
@Getter
@AllArgsConstructor
public enum PointOccupyTypeEnum {
    /**
     * 未知
     */
    NULL(-2, "未知"),
    /**
     * 无占用，当前点位空闲
     */
    NOT(-1, "无"),
    /**
     * 任务占用，AGV路线占用
     */
    TASK(0, "任务占用"),
    DISTANCE(1, "车距占用"),
    CONTROLAREA(2, "管制区占用"),
    CONTROLPOINT(3, "管制点占用"),
    MANUAL(4, "手动占用"),
    PARK(5, "停车占用"),
    EQUIPMENT(6, "设备占用"),
    CHOOSE(7, "选择占用"),
    OFFLINE(8, "离线占用"),
    CONFIG(9, "配置占用"),
    BLOCK(10, "阻断占用"),
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
    public static PointOccupyTypeEnum getByCode(Integer code) {
        if (code == null) {
            return NULL;
        }
        for (PointOccupyTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
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
    public static boolean isEnumByCode(PointOccupyTypeEnum obj, Integer code) {
        return obj.equals(getByCode(code));
    }
}
