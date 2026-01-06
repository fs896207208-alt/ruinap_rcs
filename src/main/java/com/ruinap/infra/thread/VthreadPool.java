package com.ruinap.infra.thread;

import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.log.RcsLog;
import lombok.Getter;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 全局线程池 - 使用虚拟线程实现
 * <p>
 * 虚拟线程的优点
 * <p>
 * 高并发支持：
 * 虚拟线程非常适合处理大量并发任务，比如需要同时调度多个 AGV 的任务。每个任务可以独立运行，而不会占用过多的操作系统资源。
 * 如果系统需要调度大量的独立任务（例如，监控多个 AGV 的状态、处理传感器数据等），虚拟线程可以显著提升性能。
 * <p>
 * 轻量级线程管理：
 * 虚拟线程相比传统的操作系统线程非常轻量，创建和销毁的成本较低。
 * 如果 AGV 系统需要频繁创建和销毁线程（如定时任务调度），虚拟线程可以减少内存和上下文切换的开销。
 * <p>
 * 优化 I/O 密集型任务：
 * 虚拟线程特别适合 I/O 密集型任务，如果调度系统涉及大量的外部通信（例如与 AGV 通信、传感器数据获取、数据库查询等），虚拟线程非常适合这些 I/O 密集型任务。。
 * 对于等待或阻塞的操作，虚拟线程会让出 CPU 时间，从而避免线程阻塞带来的性能问题。
 * <p>
 * 虚拟线程的限制
 * <p>
 * 计算密集型任务：
 * 虚拟线程并不适合 CPU 密集型任务。虚拟线程的调度主要由 JVM 管理，过多的计算密集型操作可能会导致性能下降，因为虚拟线程并不会直接利用多个 CPU 核心（这依赖于底层的操作系统线程）。如果 AGV 系统有大量的计算任务（例如路径规划、实时障碍物检测等），传统的线程池可能更适合。
 * <p>
 * 调度粒度和复杂性：
 * 虚拟线程在大规模并发情况下非常高效，但如果调度任务的粒度非常细小且频繁，可能会增加 JVM 内部的调度负担。因此，需要谨慎评估系统中任务的执行方式。
 * <p>
 * 调试与诊断：
 * 虚拟线程的调试可能比传统线程更具挑战性，因为虚拟线程的生命周期更短，而且通常会被大规模创建和销毁。调度和诊断这类任务时需要特别注意。
 *
 * @author qianye
 * @create 2025-01-10 11:02
 */
@Component
public class VthreadPool {
    /**
     * 任务执行器
     */
    @Getter
    private ExecutorService executor;
    /**
     * 调度任务执行器
     * 仅用于"滴答"计时的调度器 (使用普通的平台线程更稳定用于轮询，因为只负责触发，不负责业务逻辑)
     * 具体的业务逻辑甩给了虚拟线程执行，这意味着 scheduler 里的线程几乎不干重活，它们瞬间就能完成任务投递
     * 对于调度器，2-4 个核心线程足以驱动成千上万个定时任务。过多的平台线程是资源浪费
     */
    @Getter
    private ScheduledExecutorService scheduler;

    /**
     * 启动线程池
     */
    @PostConstruct
    public void run() {
        // 初始化虚拟线程池
        executor = Executors.newThreadPerTaskExecutor(
                getDaemonThread("vt-")
        );

        // 初始化调度任务执行器
        // 调度器不需要 CPU 核心数那么多线程，因为它不执行业务，只负责抛出任务
        // 2-4 个线程足以驱动成千上万个定时任务
        scheduler = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "st-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                }
        );

        RcsLog.consoleLog.info("vThreadPool 初始化完成");
        RcsLog.algorithmLog.info("vThreadPool 初始化完成");
    }

    // ================== 核心执行方法 (统一入口) ==================

    /**
     * 提交任务 (无返回值，发后即忘)
     * <p>
     * 行为特点：
     * 1. **最彻底的异步**：方法调用瞬间完成，主线程无法得知任务何时开始、何时结束。
     * 2. **无返回值**：你拿不到任何控制句柄，无法取消，无法等待。
     * 3. **异常吞没**：如果任务内部抛出异常且没写 try-catch，异常会直接打印到控制台或丢失，主线程完全无感。
     * <p>
     * 适用场景：
     * 仅仅为了触发一个动作，且不在乎结果。例如：打印一条无关紧要的日志、定时器定时触发更新AGV状态到数据库。
     *
     * @param runnable 线程任务
     */
    public void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    /**
     * 提交任务 (无返回值，但返回 Future 用于控制)
     * <p>
     * 与 execute 的区别：
     * 虽然任务本身没有返回值，但它返回了一个 {@link Future} 对象。
     * <p>
     * 用法：
     * 1. **等待结束**：调用 {@code future.get()} 会阻塞当前线程，直到任务跑完。
     * 2. **捕获异常**：如果任务挂了，调用 {@code future.get()} 时会抛出 {@link ExecutionException}，让你知道它挂了。
     * 3. **取消任务**：调用 {@code future.cancel(true)} 可以尝试中断正在运行的任务。
     *
     * @param runnable 线程任务
     * @return Future<?>
     */
    public Future<?> submit(Runnable runnable) {
        return executor.submit(runnable);
    }

    /**
     * 提交任务（有返回值）
     * <p>
     * 这是一个标准的“异步获取结果”模式。
     * <p>
     * 场景举例：
     * 主线程需要计算一条从 A 到 B 的路径（耗时 500ms），可以调用此方法提交计算任务，
     * 然后主线程去干别的事，最后调用 {@code future.get()} 拿路径结果。
     *
     * @param callable 线程任务（带返回值的逻辑）
     * @param <T>      返回值类型
     * @return 提交任务的 Future，调用 get() 可获取返回值
     */
    public <T> Future<T> submit(Callable<T> callable) {
        return executor.submit(callable);
    }

    // ================== 调度相关方法 ==================

    /**
     * 提交单次延时调度任务
     * <p>
     * 行为：
     * “定个闹钟，时间到了响一次”。
     * 任务会在 delay 时间后，被投递到虚拟线程池中执行。
     * <p>
     * 返回值 {@link ScheduledFuture} 的作用：
     * 1. **后悔药**：在时间没到之前，调用 {@code future.cancel(false)} 可以取消任务，不让它执行。
     * 2. **查状态**：调用 {@code future.getDelay()} 可以看还剩多少时间触发。
     *
     * @param runnable 线程任务
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @return 调度任务的 Future
     */
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        // 包装任务：触发时，将任务提交给虚拟线程执行器
        return scheduler.schedule(() -> execute(runnable), delay, timeUnit);
    }

    /**
     * 提交延时异步任务 (CompletableFuture 版)
     * <p>
     * 这是对 {@link #schedule} 的高级封装，专为流式编程设计。
     * <p>
     * 独特优势：
     * 返回的是 {@link CompletableFuture}，这意味着你可以进行“链式调用”。
     * 例如：{@code scheduleAsync(task, 1, SECONDS).thenRun(() -> System.out.println("任务做完了"))}
     * <p>
     * 异常处理：
     * 如果任务执行出错，异常会被封装在 CompletableFuture 中，可以通过 {@code .exceptionally()} 统一处理。
     *
     * @param runnable 任务逻辑
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @return CompletableFuture<T>
     */
    public <T> CompletableFuture<T> scheduleAsync(Runnable runnable, long delay, TimeUnit timeUnit) {
        CompletableFuture<T> future = new CompletableFuture<>();
        // 调度器触发
        schedule(() -> {
            // 提交给虚拟线程执行
            execute(() -> {
                try {
                    // [关键] 在虚拟线程内部执行业务逻辑
                    runnable.run();
                    // 业务跑完，才标记 Future 完成
                    future.complete(null);
                } catch (Throwable e) {
                    // 捕获所有异常（包括 RuntimeException 和 Error）
                    future.completeExceptionally(e);
                }
            });
        }, delay, timeUnit);
        return future;
    }

    /**
     * 提交周期性调度任务 (FixedRate - 固定频率)
     * <p>
     * 逻辑：
     * “每隔 1 秒响一次闹钟，不管上一次有没有做完”。
     * 比如：0s 开始, 1s 开始, 2s 开始...
     * <p>
     * 注意：
     * 本方法内部已经做了优化，触发后立即甩给虚拟线程执行。
     * 所以即使你的业务逻辑耗时超过了间隔时间（比如间隔 1s，任务耗时 2s），也不会阻塞调度器，
     * 但会导致同一时刻有多个相同的任务在并行跑（重叠执行），请确保你的业务逻辑支持并发，否则请使用 {@link #scheduleWithFixedDelay}。
     *
     * @param runnable     线程任务
     * @param initialDelay 初始延迟
     * @param period       间隔时间
     * @param timeUnit     时间单位
     * @return 调度任务的 Future，调用 cancel() 可停止后续调度
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit timeUnit) {
        return scheduler.scheduleAtFixedRate(() -> execute(runnable), initialDelay, period, timeUnit);
    }

    /**
     * 提交周期性串行调度任务 (FixedDelay - 固定间隔) - 虚拟线程版
     * <p>
     * 逻辑：
     * “做完这次任务，休息一会儿，再做下一次”。
     * 顺序：Execute -> Done -> Sleep(Delay) -> Execute -> Done -> Sleep(Delay)...
     * <p>
     * 核心优势：
     * 1. **绝对防重叠**：永远等待上一次任务彻底结束后，才开始计时下一轮。适合不仅要定时，还要严格顺序的场景（如 AGV 状态轮询）。
     * 2. **虚拟线程独享**：该方法会启动一个独立的虚拟线程跑死循环，不占用调度线程资源。
     *
     * @param runnable     任务逻辑
     * @param initialDelay 初始延迟
     * @param delay        执行间隔 (上一轮结束到下一轮开始的时间)
     * @param timeUnit     时间单位
     * @return Future<?> 可用于强制中断循环 (future.cancel(true))
     */
    public Future<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit timeUnit) {
        // 直接提交给虚拟线程执行器，不走 Scheduler
        return submit(() -> {
            try {
                // 1. 处理初始延迟
                if (initialDelay > 0) {
                    TimeUnit.MILLISECONDS.sleep(timeUnit.toMillis(initialDelay));
                }

                // 2. 进入自驱循环
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // 执行业务逻辑 (无论执行多久，代码都会停在这里)
                        runnable.run();
                    } catch (Throwable e) {
                        // 捕获业务异常，防止循环意外终止 (除非是中断)
                        // 遇到严重错误是否退出循环？通常建议退出，避免死循环打印日志
                        // 但如果是偶发 Error，可以选择继续。这里建议根据业务决定，稳健起见暂时仅记录
                        RcsLog.sysLog.error("FixedDelay 调度任务执行异常", e);
                    }

                    // 3. 业务执行完后，进行间隔休眠
                    // 这就是 FixedDelay 的语义：End -> Sleep -> Start
                    TimeUnit.MILLISECONDS.sleep(timeUnit.toMillis(delay));
                }
            } catch (InterruptedException e) {
                // 外部调用 future.cancel(true) 时会触发中断，优雅退出
                Thread.currentThread().interrupt();
                RcsLog.sysLog.error("FixedDelay 调度任务执行时异常", e);
            }
        });
    }

    /**
     * 提交有限次数的周期性调度任务
     * <p>
     * 场景举例：
     * 1. **报警提示**：“AGV 遇到障碍物，蜂鸣器急促响 5 次后停止”。
     * 2. **有限重试**：“尝试连接交通管制服务，每隔 1秒 试一次，最多试 3 次，失败则放弃”。
     * <p>
     * 行为逻辑：
     * 1. 基于 {@link #scheduleAtFixedRate} 实现，保证频率稳定。
     * 2. 内部维护一个原子计数器 {@link AtomicInteger}，每执行一次减一。
     * 3. **自动停止**：当执行次数达到 {@code times} 后，它会自动调用 {@code future.cancel()} 自毁，停止后续调度。
     * <p>
     * 关于返回值：
     * 返回的 {@link ScheduledFuture} 非常重要。
     * 虽然它会自动停止，但如果你需要在次数未用完时**提前强制停止**（例如：报警响了第 2 次时障碍物被移除了，需要立即静音），
     * 可以直接调用 {@code future.cancel(false)}。
     *
     * @param runnable     线程任务
     * @param initialDelay 初始延迟
     * @param period       间隔时间
     * @param times        执行次数（包含首次）
     * @param unit         时间单位
     * @return 调度任务的 Future，可用于在次数耗尽前提前终止任务
     */
    public ScheduledFuture<?> scheduleAtFixedRateTimes(Runnable runnable, long initialDelay, long period, int times, TimeUnit unit) {
        AtomicInteger remainingTimes = new AtomicInteger(times);
        // 使用 AtomicReference 保证线程间可见性
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Runnable wrappedTask = () -> {
            // 1. 检查剩余次数
            if (remainingTimes.get() <= 0) {
                // 防御性编程：如果已经归零，尝试取消
                ScheduledFuture<?> f = futureRef.get();
                if (f != null) {
                    f.cancel(false);
                }
                return;
            }

            try {
                // 2. 提交业务任务到虚拟线程池 (异步执行，不阻塞调度器)
                execute(runnable);
            } catch (Exception e) {
                RcsLog.sysLog.error("周期任务提交失败", e);
            }

            // 3. 递减并检查是否需要终止
            if (remainingTimes.decrementAndGet() == 0) {
                ScheduledFuture<?> f = futureRef.get();
                if (f != null) {
                    f.cancel(false);
                }
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(wrappedTask, initialDelay, period, unit);
        futureRef.set(future);

        // 再次检查：如果是 0 次或任务极快结束，确保取消
        if (remainingTimes.get() == 0) {
            future.cancel(false);
        }
        return future;
    }

    /**
     * 提交异步任务（Runnable），返回 CompletableFuture
     * <p>
     * 不调用 .get() 或 .join()：主线程（调用者）提交任务后立即继续向下执行，不等待任务结束，拿不到返回值，如果任务内部抛出异常，主线程完全感知不到，异常会被吞没（除非你在任务内部写了 try-catch 或使用了 .exceptionally）
     * <p>
     * 调用 .get() 或 .join()：主线程暂停阻塞，直到异步任务执行完毕（成功或抛出异常）才继续，get()/join() 会返回任务结果（如果是 Void 则返回 null），任务内部的异常会被重新抛出（get 抛出 ExecutionException，join 抛出 CompletionException），主线程可以捕获处理
     * <p>
     * .get() 与 .join() 虽然两者都用于等待，但在异常处理上略有不同：
     * <p>
     * .get():
     * 来自 Future 接口。
     * 抛出 Checked Exception (InterruptedException, ExecutionException)。
     * 缺点：强制你写 try-catch，代码。
     * <p>
     * .join():
     * 来自 CompletableFuture 类。
     * 抛出 Unchecked Exception (CompletionException)。
     * 优点：在 Lambda 表达式和流式编程中更方便，不需要强制捕获异常。
     * 建议：在绝大多数业务代码中，优先使用 .join()，因为它让代码更整洁。
     *
     * @param runnable 任务逻辑
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    /**
     * 提交异步任务（Supplier - 带返回值），返回 CompletableFuture
     * <p>
     * 这是 {@link #runAsync(Runnable)} 的孪生兄弟，区别在于它可以<b>返回结果</b>。
     * <p>
     * 使用姿势：
     * 1. **不调用 .join()**：发后即忘。常用于不需要立即拿结果，但希望后续通过 {@code .thenAccept(result -> ...)} 回调处理结果的场景。
     * 2. **调用 .join()**：同步阻塞等待。主线程卡住，直到算出结果。
     * <p>
     * 异常处理：
     * 如果 Supplier 内部报错，{@code .join()} 会抛出 {@link CompletionException}。
     *
     * @param supplier 带返回值的任务逻辑 (例如：() -> "计算结果")
     * @return CompletableFuture<T>
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /**
     * 获取守护线程
     *
     * @param tName 线程名称
     * @return 线程工厂
     */
    public ThreadFactory getDaemonThread(String tName) {
        return Thread.ofVirtual()
                .name(tName, 0)
                .factory();
    }

    /**
     * 监控线程池状态 (轻量级安全版)
     */
    public String monitorThreadPool() {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        int totalThreads = threadMxBean.getThreadCount();
        int daemonThreads = threadMxBean.getDaemonThreadCount();
        // 注意：JDK 21 中，getTotalStartedThreadCount 可以反映虚拟线程的创建总量，但 ThreadCount 通常只反映平台线程
        long totalStarted = threadMxBean.getTotalStartedThreadCount();

        StringBuilder sb = new StringBuilder();
        sb.append("=== 线程池监控 (JDK 21) ===\n");
        sb.append("平台线程总数 (Platform Threads): ").append(totalThreads).append("\n");
        sb.append("守护线程数: ").append(daemonThreads).append("\n");
        sb.append("历史累计启动线程数 (含已销毁): ").append(totalStarted).append("\n");

        if (scheduler instanceof ThreadPoolExecutor tpe) {
            sb.append("调度器(Scheduler) 活跃线程: ").append(tpe.getActiveCount())
                    .append("/").append(tpe.getCorePoolSize()).append("\n");
            sb.append("调度器(Scheduler) 队列任务数: ").append(tpe.getQueue().size()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        // 1. 同时发出“停止接收新任务”的指令
        if (executor != null) {
            executor.shutdown();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        try {
            // 2. 优先处理 Scheduler (定时任务通常比较轻快)
            if (scheduler != null && !scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            // 3. 重点处理 Executor (业务任务都在这)
            // 给虚拟线程留足时间去完成最后的 I/O (比如给 AGV 回复一个 ACK)
            if (executor != null && !executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();

                // 再等一小会儿，让中断信号传播
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    RcsLog.consoleLog.error("vThreadPool 虚拟线程池未能彻底关闭");
                    RcsLog.algorithmLog.error("vThreadPool 虚拟线程池未能彻底关闭");
                }
            }
        } catch (InterruptedException e) {
            // 自身被中断，立即强制关闭所有
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }
        RcsLog.consoleLog.warn("vThreadPool 资源释放完毕");
        RcsLog.algorithmLog.warn("vThreadPool 资源释放完毕");
    }
}
