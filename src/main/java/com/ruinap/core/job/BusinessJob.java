package com.ruinap.core.job;

import com.ruinap.adapter.communicate.NettyManager;
import com.ruinap.core.algorithm.RcsPlanManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.equipment.pojo.RcsAgvAttribute;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.command.agv.AgvCommandService;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.enums.agv.AgvStateEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.exception.TaskFinishedException;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.schedule.RcsScheduled;
import com.ruinap.infra.thread.RcsTaskExecutor;
import com.ruinap.infra.thread.VthreadPool;
import com.ruinap.persistence.DbManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 调度业务定时任务
 * <p>
 * 非系统运行必须任务，基本是业务功能的定时任务类
 *
 * @author qianye
 * @create 2025-01-10 14:16
 */
@Service
public class BusinessJob {
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
    @Autowired
    private TaskPathManager taskPathManager;
    @Autowired
    private RcsPlanManager rcsPlanManager;
    @Autowired
    private RcsTaskExecutor rcsTaskExecutor;


    /**
     * 连接所有Netty客户端
     */
    @RcsScheduled(delay = 10, period = 10, unit = TimeUnit.SECONDS)
    public void nettyClientStart() {
        // 启动WebSocket客户端
        nettyManager.startClients();
    }

    /**
     * 定时发送查询AGV地图指令
     */
    @RcsScheduled(delay = 11, period = 10, unit = TimeUnit.SECONDS)
    public void getAgvMapCheck() {
        // 使用全局线程池执行任务
        vthreadPool.execute(() -> {
            Map<String, RcsAgv> rcsAgvMap = agvManager.getRcsAgvMap();
            for (Map.Entry<String, RcsAgv> entry : rcsAgvMap.entrySet()) {
                RcsAgv rcsAgv = entry.getValue();
                //AGV属性
                RcsAgvAttribute rcsAgvAttribute = rcsAgv.getRcsAgvAttribute();
                //协议枚举
                ProtocolEnum protocol = rcsAgvAttribute.getProtocol();

                ByteBuf byteBuf = Unpooled.buffer();
                //获取客户端的计数器
                Long clientCounter = nettyManager.getClientCounter(protocol.getProtocol(), rcsAgv.getAgvId());
                // 组装发送给客户端的消息
                agvCommandService.getMapCheckCommand(protocol.toString(), byteBuf, rcsAgv.getAgvId(), clientCounter);
                nettyManager.sendMessage(protocol, rcsAgv.getAgvId(), byteBuf);
            }
        });
    }

    /**
     * 任务路径规划点火器
     */
    @RcsScheduled(delay = 25000, period = 120, unit = TimeUnit.MILLISECONDS)
    public void taskPathPlan() {
        // 遍历缓存池中所有的 AGV 及其任务列表
        for (ConcurrentHashMap.Entry<String, List<TaskPath>> entry : taskPathManager.getAll().entrySet()) {
            // 取出 AGV 编号
            String agvId = entry.getKey();
            List<TaskPath> paths = entry.getValue();

            if (paths == null || paths.isEmpty()) {
                continue;
            }

            // 1. 【前置校验】：如果这辆车的任务已经在跑了，绝对不重复点火！
            if (rcsTaskExecutor.isTaskRunning(agvId)) {
                continue;
            }

            // 2. 校验 AGV 状态
            RcsAgv agv = agvManager.getRcsAgvByCode(agvId);
            if (agv == null || AgvStateEnum.isEnumByCode(AgvStateEnum.OFFLINE, agv.getAgvState())) {
                continue;
            }

            // 3. 【核心修复】：动态组装 Runnable，彻底消灭闭包固化！
            Runnable taskRunnable = () -> {
                // 注意：必须在 Lambda 内部实时去拉取第一条！不能在外面拉！
                TaskPath currentFirstTask = taskPathManager.getFirst(agvId);

                if (currentFirstTask == null) {
                    // 如果拉不到，主动抛出异常，触发 RcsTaskExecutor 的 finally 销毁线程
                    throw new TaskFinishedException();
                }

                // 将最新的路径丢给业务引擎
                rcsPlanManager.plan(currentFirstTask);
            };

            // 4. 将这辆车和它专属的 Runnable 丢给点火器！
            rcsTaskExecutor.submitTaskLifecycle(agvId, taskRunnable, 120, TimeUnit.MILLISECONDS);
        }
    }
}
