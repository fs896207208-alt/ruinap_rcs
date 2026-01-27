package com.ruinap.core.job;

import com.ruinap.adapter.communicate.NettyManager;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.schedule.RcsScheduled;

import java.util.concurrent.TimeUnit;

/**
 * 调度定时任务
 *
 * @author qianye
 * @create 2025-12-12 18:20
 */
@Service
public class RcsJob {
    @Autowired
    private NettyManager nettyManager;

    /**
     * 连接所有Netty客户端
     */
    @RcsScheduled(delay = 10, period = 10, unit = TimeUnit.SECONDS)
    public void nettyClientStart() {
        // 启动WebSocket客户端
        nettyManager.startClients();
    }
}
