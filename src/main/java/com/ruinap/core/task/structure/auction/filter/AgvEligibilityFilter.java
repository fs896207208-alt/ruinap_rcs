package com.ruinap.core.task.structure.auction.filter;

import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;

/**
 * AGV 竞标资格审查过滤器接口
 * <p>
 * 职责：在极其耗时的 A* 寻路算法执行前，通过极轻量级的属性比对，快速淘汰不符合业务规则的 AGV。
 * </p>
 *
 * @author qianye
 * @create 2026-02-27 09:52
 */
public interface AgvEligibilityFilter {

    /**
     * 校验 AGV 是否有资格接取该任务
     *
     * @param agv  待校验的 AGV
     * @param task 当前派发的任务
     * @return true=有资格参与竞标，false=无资格被淘汰
     */
    boolean isEligible(RcsAgv agv, RcsTask task);
}