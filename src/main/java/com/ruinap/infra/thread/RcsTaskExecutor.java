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
     * @return 结果列表（按传入顺序排序）
     */
    public <T> List<T> executeAllOrThrow(List<Callable<T>> tasks, Instant deadline) throws ExecutionException, InterruptedException, TimeoutException {
        int size = tasks.size();
        // 使用 CompletionService 来监听 "谁先结束"
        ExecutorCompletionService<T> completionService = new ExecutorCompletionService<>(vthreadPool.getExecutor());
        List<Future<T>> futures = new ArrayList<>(size);

        // 1. 提交所有任务
        for (Callable<T> task : tasks) {
            // 注意：这里必须用 completionService.submit，这样结果才会进入内部队列
            futures.add(completionService.submit(task));
        }

        try {
            // 2. 按完成顺序获取结果 (这里是 Fail-Fast 的关键)
            // 我们不关心谁是 taskA 谁是 taskB，我们只关心"下一个完成的任务"
            for (int i = 0; i < size; i++) {
                long timeLeft = (deadline != null) ?
                        Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis()) : Long.MAX_VALUE;

                // poll 会获取"最早完成"的那个任务（无论是成功还是异常）
                Future<T> doneFuture = completionService.poll(timeLeft, TimeUnit.MILLISECONDS);
                if (doneFuture == null) {
                    throw new TimeoutException("任务整体等待超时");
                }

                // 立即检查结果。如果该任务抛出了异常，get() 会立刻抛出 ExecutionException，进入 catch 块
                doneFuture.get();
            }

            // 3. 如果能走到这里，说明 loop 跑完没有任何异常，所有任务都成功了。
            // 现在按原始顺序收集结果返回
            List<T> results = new ArrayList<>(size);
            for (Future<T> future : futures) {
                // 此时 future 肯定已完成且无异常
                results.add(future.get());
            }
            return results;

        } catch (Exception e) {
            // 【熔断机制】一旦捕捉到任何异常（包括 ExecutionException）
            // 立即遍历所有 Future，取消那些还在运行的任务
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    // 发送中断信号，让还在跑的慢任务停下来
                    f.cancel(true);
                }
            }
            // 将异常抛给上层
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