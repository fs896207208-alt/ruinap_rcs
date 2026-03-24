package com.ruinap.infra.enums.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务路径下发状态机 (严格控制算路与通信 I/O)
 *
 * @author qianye
 * @create 2026-03-13 10:10
 */
@Getter
@AllArgsConstructor
public enum CurrentPlanStateEnum {

    /**
     * 初始状态 / 需重新审查
     */
    REQUIRE_PLAN(0, "需审查/未规划"),

    /**
     * 弱网缓存重传
     */
    REQUIRE_RESEND(1, "待缓存重传"),

    /**
     * 防抖锁：下发中 / 待网关 ACK
     */
    WAITING_GATEWAY_ACK(2, "下发中/待网关ACK"),

    /**
     * 通信异常：网关下发失败
     */
    SEND_FAILED(3, "网关下发失败");

    public final int code;
    public final String desc;

    public static boolean isEnumByCode(CurrentPlanStateEnum enumObj, int code) {
        return enumObj.getCode() == code;
    }
}