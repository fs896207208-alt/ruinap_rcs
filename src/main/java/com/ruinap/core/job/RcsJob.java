package com.ruinap.core.job;

import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.schedule.RcsCron;
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

    @RcsCron("0/1 * * * * ?")
    public void cronJob() {
//        System.out.println(">>> [EXEC] Cron 任务执行!");

    }

    @RcsScheduled(configKey = "testDynamicTimer", period = 1, unit = TimeUnit.SECONDS)
    public void dynamicJob() {
        //RcsLog.consoleLog.info(RcsLog.getTemplate(2), RcsLog.randomInt(), ">>> [EXEC] 动态定时任务执行!");
    }
}
