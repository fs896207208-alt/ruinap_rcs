package com.ruinap.core.job;

import com.ruinap.adapter.communicate.NettyManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.infra.command.agv.AgvCommandService;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.schedule.RcsScheduled;
import com.ruinap.infra.thread.VthreadPool;
import com.ruinap.persistence.DbManager;

import java.util.concurrent.TimeUnit;

/**
 * 系统定时任务
 * <p>
 * 调度系统内部运行所需的定时业务功能，请谨慎操作，以免影响系统功能
 *
 * @author qianye
 * @create 2025-02-28 14:13
 */
@Service
public class RcsJob {
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private NettyManager nettyManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private AgvCommandService agvCommandService;
    @Autowired
    private DbManager dbManager;

    /**
     * 数据库数据检查
     * <p>
     * 检查项目：AGV、充电桩
     * <p>
     * 防止数据库数据被人为误删导致异常
     */
    @RcsScheduled(delay = 60, period = 60, unit = TimeUnit.SECONDS)
    public void checkData() {
        vthreadPool.execute(dbManager::checkData);
    }

    /**
     * 同步数据到调度系统
     * <p>
     * 同步项目：AGV、充电桩、任务
     * <p>
     * 如果你需要单独控制各个业务的间隔时间，请将syncDataToDb方法拆分为多个方法，并使用@RcsScheduled注解控制间隔时间
     */
    @RcsScheduled(delay = 10, period = 1, unit = TimeUnit.SECONDS)
    public void syncDataToRcs() {
        vthreadPool.execute(dbManager::syncDataToRcs);
    }

    /**
     * 同步数据到数据库
     * <p>
     * 同步项目：AGV、充电桩、任务
     * <p>
     * 如果你需要单独控制各个业务的间隔时间，请将syncDataToDb方法拆分为多个方法，并使用@RcsScheduled注解控制间隔时间
     */
    @RcsScheduled(delay = 10, period = 1, unit = TimeUnit.SECONDS)
    public void syncDataToDb() {
        vthreadPool.execute(dbManager::syncDataToDb);
    }

    /**
     * 临时数据写入
     */
    @RcsScheduled(delay = 60, period = 5, unit = TimeUnit.SECONDS)
    public void tempDataWrite() {
//        vthreadPool.execute(TempDataConfig::writeConfig);
    }
}
