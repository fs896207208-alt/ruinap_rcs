package com.ruinap.infra.framework.schedule;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.framework.config.SchedulingFeatureControl;
import com.ruinap.infra.framework.core.AnnotationConfigApplicationContext;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.ApplicationContextAware;
import com.ruinap.infra.framework.core.event.ApplicationListener;
import com.ruinap.infra.framework.core.event.config.RcsCoreConfigRefreshEvent;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 【核心组件】智能定时任务调度管理器 (RcsSchedulerManager)
 * <p>
 * 该组件负责管理系统内所有定时任务的生命周期，具备以下核心能力：
 * 1. **启动自加载**：实现 CommandLineRunner，在 IoC 容器准备就绪后自动触发任务挂载。
 * 2. **静态调度 (Cron)**：支持基于 @RcsCron 注解的标准 Cron 表达式任务。
 * 3. **动态调度 (Scheduled)**：支持 @RcsScheduled 任务，并具备基于 CoreYaml 的实时参数覆盖能力。
 * 4. **热重载 (Hot-Reload)**：支持不重启系统的情况下，通过接口触发配置刷新。利用 MD5 指纹拦截无效扫描，并通过快照差异比对实现任务的平滑重启。
 * 5. **资源安全**：通过虚拟线程池执行，并在容器关闭时执行优雅停机，确保无僵尸线程。
 *
 * @author qianye
 * @create 2025-12-18
 */
@Component
@Order(4)
public class RcsSchedulerManager implements CommandLineRunner, ApplicationContextAware, ApplicationListener<RcsCoreConfigRefreshEvent> {

    /**
     * 注入虚拟线程池调度器，用于执行具体的任务逻辑
     */
    @Autowired
    private VthreadPool vthreadPool;

    @Autowired
    private CoreYaml coreYaml;

    /**
     * 应用上下文引用，用于扫描 IoC 容器中的 Bean
     */
    private ApplicationContext applicationContext;

    /**
     * 任务快照表 (一级缓存)
     * Key: identifyKey (任务唯一标识)
     * Value: TaskSnapshot (记录该任务当前运行时的配置指纹)
     * 作用：用于在热加载时对比配置是否发生实质性变化，避免无意义的任务重启。
     */
    private final Map<String, TaskSnapshot> taskSnapshots = new ConcurrentHashMap<>();

    /**
     * 任务句柄注册表
     * Key: identifyKey (任务唯一标识)
     * Value: Future (调度句柄)
     * 作用：保存正在运行的任务引用，以便在配置变更时执行 cancel 操作。
     */
    private final Map<String, Future<?>> taskRegistry = new ConcurrentHashMap<>();

    /**
     * 设置应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * IoC 容器启动回调入口
     */
    @Override
    public void run(String... args) throws Exception {
        // 检查全局调度功能开关
        if (!SchedulingFeatureControl.isEnabled()) {
            return;
        }
        RcsLog.consoleLog.info("RcsSchedulerManager 正在启动定时任务...");

        // 1. 初始化基于注解的 Cron 静态任务
        initCronTasks();

        // 2. 初始化可配置的 Scheduled 动态任务
        syncScheduledTasks();
    }

    /**
     * 【事件驱动接口】当监听到 ConfigRefreshEvent 时触发
     */
    @Override
    public void onApplicationEvent(RcsCoreConfigRefreshEvent event) {
        RcsLog.consoleLog.info(RcsLog.getTemplate(2), RcsLog.randomInt(), ">>> [热加载] 监听到配置刷新事件，开始执行任务平滑同步...");
        syncScheduledTasks();
    }

    /**
     * 同步 Scheduled 任务状态
     * 职责：遍历 IoC 容器中所有标记了 @RcsScheduled 的方法，并根据当前配置决定是否重启任务。
     */
    private void syncScheduledTasks() {
        if (!(applicationContext instanceof AnnotationConfigApplicationContext ctx)) {
            return;
        }
        // 获取容器内所有单例对象
        Map<Class<?>, Object> allBeans = ctx.getAllBeans();

        for (Object bean : allBeans.values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                RcsScheduled ann = method.getAnnotation(RcsScheduled.class);
                if (ann == null) {
                    continue;
                }

                // 1. 生成绝对唯一的标识符 (优先使用 configKey，其次使用方法全限定名)
                String identifyKey = StrUtil.isNotBlank(ann.configKey()) ?
                        ann.configKey() : method.getDeclaringClass().getSimpleName() + "#" + method.getName();

                // 2. 获取经过配置覆盖后的最终参数快照
                TaskSnapshot latest = fetchFinalSnapshot(ann, method, identifyKey);
                // 3. 获取当前正在内存中运行的任务快照
                TaskSnapshot running = taskSnapshots.get(identifyKey);

                if (running == null) {
                    // 初次挂载或由于之前禁用后重新启用的任务
                    registerAndRecord(bean, method, latest);
                } else if (!latest.equals(running)) {
                    // 【核心逻辑】参数变动，强制执行“彻底清理再注册”
                    // 只有当快照中的 delay/period/unit/async 等字段发生变化时才会进入此分支
                    RcsLog.consoleLog.warn(">>> [变更检测] 任务 [{}] 发生变动 ({} -> {})，正在执行彻底重启...",
                            identifyKey, running.source, latest.source);

                    // 彻底停止旧任务句柄，确保旧线程不再执行
                    stopAndClearTask(identifyKey);

                    // 重新挂载新任务
                    registerAndRecord(bean, method, latest);
                } else {
                    RcsLog.consoleLog.info(">>> [变更检测] 配置无变动，定时任务 [{}] 继续运行中...", identifyKey);
                }
            }
        }
        RcsLog.consoleLog.info("RcsSchedulerManager Scheduler任务状态同步完成");
    }

    /**
     * 【资源清理】彻底停止任务，防止新旧任务并存
     */
    private void stopAndClearTask(String identifyKey) {
        Future<?> oldFuture = taskRegistry.remove(identifyKey);
        if (oldFuture != null) {
            // 参数变动引起的重载使用 cancel(true) 强制尝试中断正在运行的虚拟线程
            // 避免新旧任务重叠执行导致的数据竞争或日志混乱
            oldFuture.cancel(true);
        }
        taskSnapshots.remove(identifyKey);
    }

    /**
     * 【策略模式】合并配置源，获取最终快照
     * 优先级：YAML 配置 (且 enabled=true) > 代码注解默认参数。
     */
    private TaskSnapshot fetchFinalSnapshot(RcsScheduled ann, Method method, String identifyKey) {
        // 初始值来自注解
        long finalDelay = ann.delay();
        long finalPeriod = ann.period();
        TimeUnit finalUnit = ann.unit();
        boolean finalAsync = ann.async();
        String source = "注解默认";

        String key = ann.configKey();
        if (StrUtil.isNotBlank(key)) {
            // 尝试获取 CoreYaml 中的动态配置
            LinkedHashMap<String, LinkedHashMap<String, String>> timerConfig = coreYaml.getTimerCommon();
            if (timerConfig != null && timerConfig.containsKey(key)) {
                LinkedHashMap<String, String> cfg = timerConfig.get(key);
                // 检查开关：只有在 enabled 为 true 时才执行配置覆盖
                if (Convert.toBool(cfg.get("enabled"), true)) {
                    finalDelay = Convert.toLong(cfg.get("delay"), finalDelay);
                    finalPeriod = Convert.toLong(cfg.get("period"), finalPeriod);
                    finalAsync = Convert.toBool(cfg.get("async"), finalAsync);
                    if (cfg.containsKey("unit")) {
                        try {
                            finalUnit = TimeUnit.valueOf(cfg.get("unit").toUpperCase());
                        } catch (Exception e) {
                            RcsLog.consoleLog.warn("YAML 时间单位无效: {}，回退注解", cfg.get("unit"));
                        }
                    }
                    source = "YAML(" + finalPeriod + " " + finalUnit + ")";
                } else {
                    // 若 YAML 设置为 enabled: false，则回退到注解
                    source = "YAML设置 enabled 为关闭(降级至注解)";
                }
            } else {
                source = StrUtil.format("YAML查找不到key为【{}】的配置(降级至注解)", key);
            }
        }

        // 构造快照：identifyKey 用于唯一标识，true 表示根据逻辑该任务需要运行
        return new TaskSnapshot(identifyKey, true, finalDelay, finalPeriod, finalUnit, finalAsync, source);
    }

    /**
     * 注册任务并持久化状态
     */
    private void registerAndRecord(Object bean, Method method, TaskSnapshot snap) {
        // 包装执行器逻辑
        Runnable runner = () -> {
            if (snap.async) {
                // 异步模式：由虚拟线程池再次开启新线程执行业务逻辑
                vthreadPool.execute(() -> executeMethodSafe(bean, method));
            } else {
                // 同步模式：直接在调度线程中执行业务逻辑
                executeMethodSafe(bean, method);
            }
        };

        // 提交到调度池 (固定延迟调度)
        Future<?> f = vthreadPool.scheduleWithFixedDelay(runner, snap.delay, snap.period, snap.unit);
        if (f != null) {
            taskRegistry.put(snap.identifyKey, f);
            taskSnapshots.put(snap.identifyKey, snap);
            RcsLog.consoleLog.info("RcsSchedulerManager Scheduler任务已启动: [{}] | 周期: {} {} | 来源: {}",
                    snap.identifyKey, snap.period, snap.unit, snap.source);
        }
    }

    /**
     * 反射执行业务方法，并捕获异常防止调度线程崩溃
     */
    private void executeMethodSafe(Object bean, Method method) {
        try {
            method.setAccessible(true);
            method.invoke(bean);
        } catch (Exception e) {
            RcsLog.consoleLog.error("任务执行异常: {}#{}", bean.getClass().getSimpleName(), method.getName(), e);
        }
    }

    /**
     * 初始化 @RcsCron 标注的静态任务 (一次性初始化)
     */
    private void initCronTasks() {
        if (!(applicationContext instanceof AnnotationConfigApplicationContext ctx)) {
            return;
        }
        Map<Class<?>, Object> allBeans = ctx.getAllBeans();
        int count = 0;
        for (Object bean : allBeans.values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(RcsCron.class)) {
                    RcsCron cron = method.getAnnotation(RcsCron.class);
                    Task logic = () -> executeMethodSafe(bean, method);
                    if (cron.async()) {
                        // 注册异步 Cron
                        CronUtil.schedule(cron.value(), (Task) () -> {
                            try {
                                vthreadPool.execute(logic::execute);
                            } catch (RejectedExecutionException e) {
                                // 如果捕获到 RejectedExecutionException，说明线程池已经关闭了。
                                // 这通常发生在系统关闭时，Cron 刚好触发。这时候直接忽略即可，不要打印 ERROR。
                                RcsLog.consoleLog.warn("Cron任务[{}]被丢弃，VThreadPool已停止接收新任务", StrUtil.format("{}.{}", method.getDeclaringClass().getSimpleName(), method.getName()));
                            }
                        });
                    } else {
                        // 注册同步 Cron
                        CronUtil.schedule(cron.value(), logic);
                    }
                    count++;
                }
            }
        }
        if (count > 0) {
            // 开启秒级匹配支持
            CronUtil.setMatchSecond(true);
            // 启动 Hutool Cron 调度引擎
            CronUtil.start(true);
            RcsLog.consoleLog.info("RcsSchedulerManager 成功注册 {} 个 Cron 任务", count);
        }
    }

    /**
     * 生命周期销毁阶段：执行优雅停机
     */
    @PreDestroy
    public void shutdown() {
        // 停止 Cron 引擎
        if (CronUtil.getScheduler().isStarted()) {
            CronUtil.stop();
        }
        // 遍历并强行取消所有动态任务
        taskRegistry.values().forEach(f -> {
            f.cancel(true);
        });
        // 清理缓存释放内存
        taskRegistry.clear();
        taskSnapshots.clear();
        RcsLog.consoleLog.warn("定时任务 资源释放完毕");
    }

    /**
     * 定时任务参数快照实体类
     * 通过实现 equals 和 hashCode 方法，支持参数一致性校验。
     */
    private static class TaskSnapshot {
        final String identifyKey;
        final boolean enabled;
        final long delay;
        final long period;
        final TimeUnit unit;
        final boolean async;
        final String source;

        TaskSnapshot(String k, boolean e, long d, long p, TimeUnit u, boolean a, String s) {
            this.identifyKey = k;
            this.enabled = e;
            this.delay = d;
            this.period = p;
            this.unit = u;
            this.async = a;
            this.source = s;
        }

        /**
         * 差异比对核心：判断 delay, period, unit, async, identifyKey, enabled 是否全部一致
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TaskSnapshot that)) {
                return false;
            }
            return enabled == that.enabled && delay == that.delay && period == that.period
                    && async == that.async && Objects.equals(identifyKey, that.identifyKey) && unit == that.unit;
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifyKey, enabled, delay, period, unit, async);
        }
    }
}