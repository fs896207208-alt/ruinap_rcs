package com.ruinap.core.algorithm;

import com.ruinap.core.algorithm.domain.PathContext;
import com.ruinap.core.algorithm.domain.PathState;
import com.ruinap.core.algorithm.strategy.PathCheckState;
import com.ruinap.core.algorithm.strategy.PathNewState;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.task.TaskPathManager;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.enums.task.PlanStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.core.event.ApplicationEventPublisher;
import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.RcsTaskExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务规划管理器
 *
 * @author qianye
 * @create 2026-02-25 13:49
 */
@Service
public class RcsPlanManager {

    @Autowired
    private MapManager mapManager;
    @Autowired
    private RcsTaskExecutor rcsTaskExecutor;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TaskPathManager taskPathManager;

    /**
     * 创建一个 RcsLock 实例，用于管理并发访问的锁
     */
    private final RcsLock lock = RcsLock.ofReentrant();

    /**
     * 策略路由表
     */
    private static final Map<Integer, Class<? extends PathState>> STATE_STRATEGY_MAP = new ConcurrentHashMap<>();

    static {
        // 只有那些需要的状态，才注册处理类
        STATE_STRATEGY_MAP.put(PlanStateEnum.CHECK.code, PathCheckState.class);
        STATE_STRATEGY_MAP.put(PlanStateEnum.NEW.code, PathNewState.class);
    }

    /**
     * 任务规划
     *
     * @param taskPath 任务路径
     */
    public void plan(TaskPath taskPath) {
        // 1. 获取当前状态码（默认为 CHECK）
        Integer stateCode = taskPath.getState();

        // 2. 从路由表获取策略类
        Class<? extends PathState> stateClass = STATE_STRATEGY_MAP.get(stateCode);

        // 3. 【核心神操作：静默跳过】
        // 如果路由表里没有这个状态，说明当前阶段不需要引擎插手！
        if (stateClass == null) {
            // 🚨 直接 return 结束本次，让出 CPU
            return;
        }

        try {
            // 4. 只有需要处理的，才去拿 Bean 并执行
            PathContext pathContext = new PathContext(taskPath);
            PathState stateStrategy = SpringContextHolder.getBean(stateClass);

            pathContext.setState(stateStrategy);
            pathContext.request();

        } catch (Exception e) {
            RcsLog.sysLog.error("无法从上下文获取状态策略类: {}", stateClass.getSimpleName(), e);
        }
    }

}
