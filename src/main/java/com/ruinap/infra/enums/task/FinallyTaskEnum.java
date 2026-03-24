package com.ruinap.infra.enums.task;

import lombok.Getter;

/**
 * 任务段最终状态枚举
 *
 * @author qianye
 * @create 2026-02-04 09:51
 */
public enum FinallyTaskEnum {
    NOT_FINAL(0, "非最终段"),
    ORIGIN_FINAL(1, "起点最终段"),
    DESTIN_FINAL(2, "终点最终段");

    @Getter
    private final int code;
    @Getter
    private final String desc;

    FinallyTaskEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
