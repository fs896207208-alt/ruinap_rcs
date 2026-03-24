package com.ruinap.infra.thread;

import com.ruinap.infra.exception.TaskCanceledException;
import com.ruinap.infra.exception.TaskFinishedException;
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
     * 任务防重注册表
     */
    private final ConcurrentHashMap<String, Boolean> activeTaskRegistry = new ConcurrentHashMap<>();

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
     * 判断任务是否正在执行
     *
     * @param taskCode 任务编号
     * @return true 表示正在执行，false 表示未开始或已结束
     */
    public boolean isTaskRunning(String taskCode) {
        return activeTaskRegistry.containsKey(taskCode);
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

    /**
     * 【模式四：带超时的独立批量下发】(专用于 AGV 任务下发/网络通信)
     * 特性：
     * 1. 互不干扰：A车失败不影响B车。
     * 2. 强制超时：应对工厂弱网环境，超时自动掐断，防止无限阻塞。
     * 3. 结果汇总：返回包含成功/失败详细状态的结果集，方便上层重试或告警。
     *
     * @param tasks   具体的下发任务合集 (建议返回下发结果的状态对象)
     * @param timeout 单个任务的绝对超时时间
     * @param unit    时间单位
     * @param <T>     结果类型
     * @return 所有任务的执行结果列表
     */
    public <T> List<T> dispatchTasksConcurrently(List<Callable<T>> tasks, long timeout, TimeUnit unit) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用 VthreadPool 提交有返回值的异步任务
        List<CompletableFuture<T>> futures = tasks.stream()
                .map(task -> vthreadPool.supplyAsync(() -> {
                            try {
                                // 1. 这里面执行具体的 Netty/MQTT 通信，例如发送 VDA5050 的 Order
                                // 2. 虚拟线程会在这里优雅地阻塞等待 AGV 的 ACK，不消耗 CPU 资源
                                return task.call();
                            } catch (Exception e) {
                                // 将受检异常包装抛出，交给后面的 exceptionally 处理
                                throw new CompletionException(e);
                            }
                        })
                        // 【核心防线】：JDK9+ 提供的原生超时机制，利用你 pom 里的 JDK21 特性
                        // 彻底解决弱网环境下 TCP 或业务 ACK 丢失导致的死等问题
                        .orTimeout(timeout, unit)
                        .exceptionally(ex -> {
                            // 【异常隔离兜底】：捕获超时或发送异常，转换为失败状态的返回值
                            // 注意：这里的泛型 T 需要你的业务对象支持一种“失败态”的构建，例如 DispatchResult.fail(ex)
                            RcsLog.sysLog.error("并发下发任务给AGV时发生异常或超时", ex);
                            // 战友，这里建议你根据具体的 T 返回一个业务上的 failed 对象，而不是 null
                            // 例如：return (T) DispatchResult.fail("网络超时: " + ex.getMessage());
                            return null;
                        }))
                .toList(); // JDK 16+ 原生 toList()

        // join() 收集结果：因为上面配置了 orTimeout 和 exceptionally，
        // 这里的 join 绝对是安全的，最多只会阻塞 `timeout` 的时间，绝不会死锁。
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 【模式五：独立隔离的自旋重试下发】（弱网高并发神器）
     * 为每个任务分配一个轻量级虚拟线程，不断尝试下发，直到成功或达到最大重试阈值。
     * * @param tasks 待下发的任务列表
     *
     * @param retryInterval 重试间隔时间（例如：3秒）
     * @param maxRetries    最大重试次数（防止AGV物理关机导致的无限死循环，例如：100次）
     */
    public void dispatchTasksWithRetryConcurrently(List<Runnable> tasks, long retryInterval, int maxRetries) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        // 遍历这 3 个任务，瞬间启动 3 个独立的虚拟线程去执行，互不干涉
        for (Runnable task : tasks) {
            vthreadPool.runAsync(() -> {
                int attempt = 0;
                while (attempt < maxRetries) {
                    try {
                        // 1. 执行具体的下发逻辑 (这里应当包含阻塞等待 Netty ACK 的逻辑)
                        task.run();

                        // 2. 如果没有任何异常抛出，说明收到了 AGV 的 ACK，下发成功！
                        RcsLog.sysLog.info("任务下发成功！结束当前重试虚拟线程。");
                        return; // 【核心】：成功后直接 return，虚拟线程自然死亡，完美回收资源

                    } catch (Exception e) {
                        attempt++;
                        RcsLog.sysLog.warn("任务下发失败，准备第 {} 次重试。原因: {}", attempt, e.getMessage());

                        try {
                            // 3. 【JDK21 的黑科技】：这里的 sleep 绝对不会阻塞操作系统的底层线程！
                            // 它只会挂起当前这一个虚拟线程，等时间到了再被唤醒继续 while 循环。
                            // 它的效果和 scheduleWithFixedDelay 是一模一样的，但代码优雅了 100 倍。
                            TimeUnit.MILLISECONDS.sleep(retryInterval);
                        } catch (InterruptedException ie) {
                            // 响应外部系统级的中断指令（如服务关闭）
                            Thread.currentThread().interrupt();
                            RcsLog.sysLog.error("任务下发重试线程被强制中断");
                            return;
                        }
                    }
                }

                // 4. 兜底处理：如果超过了 maxRetries 还没成功，说明彻底失联了
                // TODO: 这里必须接入你的 AlarmManager，抛出类似 "E10005 AGV通信中断，任务下发失败" 的严重告警
                RcsLog.sysLog.error("任务下发超过最大重试次数 ({})，放弃下发并触发告警！", maxRetries);
            });
        }
    }

    /**
     * 【模式六：单任务专属点火器】
     */
    public void submitTaskLifecycle(String taskKey, Runnable taskLogic, long interval, TimeUnit unit) {
        // 利用 ConcurrentHashMap 的原子特性防重，绝不会出现线程踩踏
        if (activeTaskRegistry.putIfAbsent(taskKey, Boolean.TRUE) != null) {
            return;
        }

        vthreadPool.runAsync(() -> {
            try {
                RcsLog.sysLog.info("🎯 为 {} 启动专属生命周期虚拟线程...", taskKey);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        taskLogic.run();
                        unit.sleep(interval);
                    } catch (TaskFinishedException | TaskCanceledException e) {
                        // 任务彻底做完或取消，跳出循环
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        RcsLog.sysLog.error("任务 {} 周期执行异常", taskKey, e);
                        try {
                            unit.sleep(interval);
                        } catch (InterruptedException ie) {
                            // 如果在异常重试休眠期间被强杀，也必须响应中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } finally {
                // 【绝对闭环】：不管线程是怎么死的（哪怕是空指针报错），
                // finally 绝对会把这辆车从注册表释放，保证它随时能被重新点火！
                activeTaskRegistry.remove(taskKey);
                RcsLog.sysLog.info("🛑 {} 的专属线程已安全销毁，退出接管", taskKey);
            }
        });
    }
}