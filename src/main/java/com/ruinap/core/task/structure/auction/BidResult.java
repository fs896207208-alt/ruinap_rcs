package com.ruinap.core.task.structure.auction;

import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import lombok.Data;

/**
 * 任务竞标结果
 *
 * @author qianye
 * @create 2026-02-26 17:27
 */
@Data
public class BidResult {
    private RcsAgv rcsAgv;
    private RcsTask rcsTask;
    // 综合代价 (越小越容易中标)
    private double totalCost;
    // 附带规划好的路径，中标后直接用，省去二次计算
    private RouteResult route;

    public BidResult(RcsAgv rcsAgv, RcsTask rcsTask, double totalCost, RouteResult route) {
        this.rcsAgv = rcsAgv;
        this.rcsTask = rcsTask;
        this.totalCost = totalCost;
        this.route = route;
    }
}
