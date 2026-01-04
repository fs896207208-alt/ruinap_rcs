package com.ruinap.infra.thread;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * RCS 任务执行器 (高级编排层)
 * <p>
 * 架构变更：
 * 不再自行管理线程池，底层完全基于 {@link VthreadPool} 实现。
 * 专注于实现“结构化并发”模式和复杂的任务编排逻辑。
 *
 * @author qianye
 * @create 2025-12-10 15:10
 */
@Component
public class RcsTaskExecutor {

    @Autowired
    private VthreadPool vthreadPool;

    /**
     * 【单例模式优化】使用静态内部类 (Initialization-on-demand holder idiom)
     * 优势：
     * 1. 利用 JVM 类加载机制保证线程安全。
     * 2. 懒加载，只有第一次调用 getInstance 时才会初始化。
     * 3. 彻底消除虚拟线程 Pinning 风险。
     */
    private static class InstanceHolder {
        private static final RcsTaskExecutor INSTANCE = new RcsTaskExecutor();
    }

    public static RcsTaskExecutor getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * 私有构造
     */
    public RcsTaskExecutor() {
        RcsLog.consoleLog.info("RcsTaskExecutor (Delegated to VthreadPool) 初始化完成");
    }

    /**
     * 【模式一：全有或全无】(Fail-Fast)
     * 并行执行任务，只要有一个失败，立即取消其他任务并抛出异常。
     *
     * @param tasks    任务列表
     * @param deadline 截止时间
     * @return 结果列表
     */
    public <T> List<T> executeAllOrThrow(List<Callable<T>> tasks, Instant deadline) throws ExecutionException, InterruptedException, TimeoutException {
        // ... 原有逻辑不变 ...
        // 1. 提交所有任务到全局线程池
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(vthreadPool.submit(task));
        }

        List<T> results = new ArrayList<>(tasks.size());

        // 2. 遍历等待结果
        try {
            for (Future<T> future : futures) {
                // 计算动态剩余超时时间
                long timeoutMs = (deadline != null) ?
                        Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis()) : Long.MAX_VALUE;

                // 获取结果 (如果此任务失败，抛出异常进入 catch)
                results.add(future.get(timeoutMs, TimeUnit.MILLISECONDS));
            }
            return results;
        } catch (Exception e) {
            // 【关键】一旦发生异常，立即遍历所有 Future，取消那些还在运行的任务
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    // 向虚拟线程发送中断信号
                    f.cancel(true);
                }
            }
            // 重新抛出异常给上层
            throw e;
        }
    }

    /**
     * 【模式二：最快胜出】
     * 并行执行，返回最快成功的那个结果，取消其他任务。
     */
    public <T> T executeAnySuccess(List<Callable<T>> tasks, Instant deadline) throws ExecutionException, InterruptedException, TimeoutException {
        long timeoutMs = (deadline != null) ?
                Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis()) : Long.MAX_VALUE;

        // 直接使用 VthreadPool 暴露的底层 Executor
        return vthreadPool.getExecutor().invokeAny(tasks, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 【模式三：普通并行批处理】
     * 等待所有任务完成（无论成功失败）。
     */
    public void runParallel(List<Runnable> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> vthreadPool.runAsync(() -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        RcsLog.sysLog.error("并行任务执行异常", e);
                    }
                }))
                .toList();

        // 等待所有任务结束
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}