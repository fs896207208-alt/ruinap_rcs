package com.ruinap.core.task.structure.taskmode;

import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.auction.AuctionEngine;
import com.ruinap.core.task.structure.auction.BidResult;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;

/**
 * 任务拍卖模式
 *
 * @author qianye
 * @create 2026-02-28 09:26
 */
@Component
public class AuctionMode implements TaskModeHandle {
    @Autowired
    private AuctionEngine auctionEngine;

    @Override
    public BidResult handle(RcsTask task) {
        return auctionEngine.startAuction(task);
    }
}
