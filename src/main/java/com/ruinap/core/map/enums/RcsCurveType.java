package com.ruinap.core.map.enums;

/**
 * 线路类型
 *
 * @author qianye
 * @create 2026-01-04 10:56
 */
public enum RcsCurveType {
    /**
     * 未知
     */
    UNKNOWN(0),
    /**
     * 前后直线
     */
    STRAIGHT(1),
    /**
     * 前后二阶贝塞尔
     */
    QUADRATIC_BEZIER(2),
    /**
     * 前后三阶贝塞尔
     */
    CUBIC_BEZIER(3),
    /**
     * 左右横移
     */
    STRAIGHT_CONNECT(4),
    /**
     * 左右二阶贝塞尔
     */
    QUADRATIC_BEZIER_CONNECT(5),
    /**
     * 左右三阶贝塞尔
     */
    CUBIC_BEZIER_CONNECT(6);

    private final int value;

    RcsCurveType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static RcsCurveType of(int value) {
        for (RcsCurveType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return STRAIGHT;
    }
}
