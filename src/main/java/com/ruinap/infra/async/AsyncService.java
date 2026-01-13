package com.ruinap.infra.async;


import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 异步任务处理工具类，提供基于CompletableFuture的异步操作辅助方法，
 * 包括带超时控制的单任务等待、多任务全部等待等功能。
 *
 * @author qianye
 * @create 2024-10-18 20:12
 */
@Service
public class AsyncService {

    @Autowired
    private VthreadPool vThreadPool;

    /**
     * 提交异步任务（Runnable），返回 CompletableFuture，请注意该方法返回一个Supplier，需手动调用.get()方法
     *
     * @param runnable 任务逻辑（无参无返回值的函数）
     * @return Supplier<CompletableFuture < Void>>
     */
    public Supplier<CompletableFuture<Void>> runAsync(Runnable runnable) {
        return () -> vThreadPool.runAsync(runnable);
    }

    /**
     * 执行任务并等待结果（带超时）
     * <p>
     * 这是一个组合方法：自动提交到 vThreadPool + 自动等待
     *
     * @param supplier 业务逻辑
     * @param timeout  超时时间
     * @param unit     时间单位
     * @return 业务结果
     */
    public <T> T executeAndGet(Supplier<T> supplier, long timeout, TimeUnit unit) throws TimeoutException {
        // 1. 提交任务 (利用 vThreadPool)
        CompletableFuture<T> future = vThreadPool.supplyAsync(supplier);
        // 2. 等待结果 (复用底层的 wait 逻辑)
        return waitAnyTask(future, timeout, unit);
    }

    /**
     * 执行任务并等待完成（无返回值，带超时）
     */
    public void executeAndJoin(Runnable runnable, long timeout, TimeUnit unit) throws TimeoutException {
        CompletableFuture<Void> future = vThreadPool.runAsync(runnable);
        waitAnyTask(future, timeout, unit);
    }

    /**
     * 并行执行所有任务并等待完成
     * <p>
     * 场景：批量初始化、并行检查等不需要返回值的场景。
     * </p>
     *
     * @param tasks 任务列表 (Runnable)
     */
    public void executeAll(List<Runnable> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        // 将所有 Runnable 包装为 CompletableFuture 并提交到虚拟线程池
        // 使用 toArray(CompletableFuture[]::new) 转换为数组以适配 allOf
        CompletableFuture<?>[] futures = tasks.stream()
                .map(task -> vThreadPool.runAsync(() -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        // 捕获异常，防止单个任务失败导致整体 join 抛出异常（视业务需求而定，这里选择记录日志保全大局）
                        RcsLog.sysLog.error("AsyncService-executeAll 任务执行异常", e);
                    }
                }))
                .toArray(CompletableFuture[]::new);

        // 阻塞等待所有任务执行完毕
        CompletableFuture.allOf(futures).join();
    }

    /**
     * 等待单个异步任务完成（带超时和默认值）
     * 严禁在 Netty I/O 线程或 Scheduler 线程中调用此方法
     * 在 Netty 线程中： AGV 系统会有大量的 Netty 回调（如接收 AGV报文）。如果在 ChannelHandler 中直接调用 AsyncUtils.waitAnyTask，join() 会阻塞 Netty 的 EventLoop 线程（平台线程）。
     * 后果： 只要有几个这样的阻塞操作，Netty 的 I/O 线程池就会耗尽，导致无法处理新的心跳或指令，引发系统级假死。
     *
     * @param future       要等待的CompletableFuture
     * @param timeout      超时时间
     * @param unit         时间单位
     * @param defaultValue 超时时的默认返回值（非null时生效）
     * @param <T>          返回值类型
     * @return 任务结果或默认值（如果超时且默认值不为null）
     * @throws TimeoutException    当超时且未设置默认值时抛出
     * @throws CompletionException 如果任务执行过程中发生其他异常
     */
    public <T> T waitAnyTask(CompletableFuture<T> future, long timeout, TimeUnit unit, T defaultValue) throws TimeoutException {
        try {
            // JDK 9+ 的 orTimeout/completeOnTimeout 会直接修改原 future (如果它是源头) 或者返回新的 stage
            // 为了安全起见，我们使用 get(timeout) 传统方式，因为它更容易控制 cancellation
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            // [关键修复] 超时发生时，必须向原任务发送取消信号！
            // 虽然 CompletableFuture 的 cancel 不一定能中断线程（取决于实现），但这是必须做的“尽力而为”
            future.cancel(true);
            if (defaultValue != null) {
                return defaultValue;
            }
            throw e;
        } catch (ExecutionException e) {
            // [关键优化] 解包 ExecutionException，还原业务真相
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            // 如果是受检异常，包装为 RuntimeException 抛出，避免上层满屏 try-catch
            throw new CompletionException(cause);
        } catch (InterruptedException e) {
            // 响应中断
            Thread.currentThread().interrupt();
            throw new CompletionException("线程等待时被中断", e);
        }
    }

    /**
     * 等待单个异步任务完成（带超时，无默认值）
     *
     * @param future  要等待的CompletableFuture
     * @param timeout 超时时间
     * @param unit    时间单位
     * @param <T>     返回值类型
     * @return 任务结果
     * @throws TimeoutException 当超时时抛出
     */
    public <T> T waitAnyTask(CompletableFuture<T> future, long timeout, TimeUnit unit) throws TimeoutException {
        return waitAnyTask(future, timeout, unit, null);
    }

    /**
     * 等待所有任务完成 (Fail-Fast 策略)
     * 如果有一个失败，立即抛出异常，不再等待其他的。
     *
     * @param futures CompletableFuture列表（不能为空）
     * @param <T>     结果类型
     * @return 按输入顺序排列的任务结果列表
     */
    public <T> List<T> waitAllTasks(List<CompletableFuture<T>> futures) {
        if (futures == null || futures.isEmpty()) {
            return new ArrayList<>();
        }
        // 使用 join() 会在有一个报错时立即抛出
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
                .join();
    }

    /**
     * 严格按顺序执行任务 (Fail-Safe 策略：遇错即停)
     * 请注意：如果任务是阻塞任务，那么后续任务将无法启动
     * 1. 遇到异常立即停止，不再执行后续步骤（防止连环错误）。
     * 2. 只有全部成功才返回完整列表，否则抛出异常（原子性语义）。
     *
     * @param taskSuppliers 任务生成器列表（每个Supplier创建新Future）
     * @param <T>           结果类型
     * @return 按顺序执行的结果列表
     */
    public <T> List<T> executeStrictlySequential(List<Supplier<CompletableFuture<T>>> taskSuppliers) {
        List<T> results = new ArrayList<>();
        int index = 0;

        for (Supplier<CompletableFuture<T>> supplier : taskSuppliers) {
            try {
                CompletableFuture<T> future = supplier.get();
                // 必须 join，利用虚拟线程的阻塞优势
                T result = future.join();
                results.add(result);
            } catch (Exception e) {
                // [关键修复] 记录错误并中断链条，而不是塞入 null 继续跑
                RcsLog.sysLog.error("顺序任务链断裂 [Step {}/{}], 停止执行后续任务", index + 1, taskSuppliers.size(), e);
                throw new CompletionException("顺序执行在步骤中失败 " + (index + 1), e);
            }
            index++;
        }
        return results;
    }

    /**
     * 并行批处理任务（基于虚拟线程）
     * <p>
     * 适用于遍历处理集合。它会为集合中的每个元素启动一个虚拟线程并行处理。
     * 方法会阻塞直到所有任务处理完成（Fork-Join 模式）。
     *
     * @param items  要处理的数据集合
     * @param action 对每个数据的处理逻辑 (Consumer)
     * @param <T>    数据类型
     */
    public <T> void runParallel(Collection<T> items, Consumer<T> action) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // 1. 将集合映射为 CompletableFuture 列表
        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> vThreadPool.runAsync(() -> {
                    try {
                        action.accept(item);
                    } catch (Exception e) {
                        // 捕获单个任务的异常，确保不中断批处理
                        RcsLog.sysLog.error("并行批处理单项任务失败", e);
                    }
                }))
                .toList();

        // 2. 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 带防重/跳过逻辑的并行批处理
     * <p>
     * 自动处理"若任务正在运行则跳过"的逻辑，让业务层更专注核心代码。
     *
     * @param items         待处理集合
     * @param keyMapper     唯一Key生成器（例如：从 item 中提取 ID）
     * @param skipCondition 跳过条件（例如：检查 Key 是否已存在，返回 true 则跳过）
     * @param action        真正要执行的业务逻辑
     * @param <T>           集合元素类型
     */
    public <T> void runParallel(Collection<T> items, Function<T, String> keyMapper, Predicate<String> skipCondition, Consumer<T> action) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> vThreadPool.runAsync(() -> {
                    try {
                        // 1. 生成 Key
                        String key = keyMapper.apply(item);

                        // 2. 执行跳过检查 (防重逻辑)
                        if (skipCondition.test(key)) {
                            // 可选：在这里打印 debug 日志
                            return;
                        }

                        // 3. 执行业务逻辑
                        action.accept(item);
                    } catch (Exception e) {
                        // 统一异常捕获
                        RcsLog.sysLog.error("并行任务执行异常", e);
                    }
                }))
                .toList();

        // 等待所有虚拟线程完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
