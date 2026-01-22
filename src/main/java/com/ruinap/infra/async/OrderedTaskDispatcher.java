package com.ruinap.infra.async;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 有序任务分发器 (基于 Key 的串行执行器)
 *
 * @author qianye
 * @create 2026-01-22 15:16
 */
@Component
public class OrderedTaskDispatcher {
    @Autowired
    private VthreadPool vThreadPool;

    /**
     * 串行执行器注册表
     * Key: 业务主键 (如 clientId)
     * Value: 对应的执行器实例
     */
    private final Map<String, SerialExecutor> executorMap = new ConcurrentHashMap<>();

    /**
     * 提交有序任务
     *
     * @param key  业务主键 (如 AGV 编号)，为 null 则直接异步执行不保序
     * @param task 任务逻辑
     */
    public void dispatch(String key, Runnable task) {
        if (key == null) {
            vThreadPool.execute(task);
            return;
        }
        // 获取或创建该 Key 对应的执行器，并提交任务
        executorMap.computeIfAbsent(key, k -> new SerialExecutor(vThreadPool)).execute(task);
    }

    /**
     * 清理资源 (当 AGV 下线时必须调用)
     *
     * @param key 业务主键
     */
    public void unregister(String key) {
        if (key != null) {
            executorMap.remove(key);
        }
    }

    /**
     * 内部核心类：单线程串行执行器 (无锁实现)
     * 原理：MPSC (Multi-Producer Single-Consumer) 模式
     */
    private static class SerialExecutor {
        private final VthreadPool threadPool;
        private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
        // WIP (Work In Progress) 原子计数器，确保任何时刻只有一个线程在消费队列
        private final AtomicInteger wip = new AtomicInteger(0);

        public SerialExecutor(VthreadPool threadPool) {
            this.threadPool = threadPool;
        }

        public void execute(Runnable task) {
            taskQueue.offer(task);
            schedule();
        }

        private void schedule() {
            // 只有当 wip 从 0 变为 1 时，才触发调度
            if (wip.getAndIncrement() == 0) {
                threadPool.execute(this::processLoop);
            }
        }

        private void processLoop() {
            int missed = 1;
            do {
                Runnable task;
                // 批量消费队列
                while ((task = taskQueue.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        RcsLog.sysLog.error("有序任务执行异常", e);
                    }
                }
                // 检查是否有新任务在处理期间加入 (Double-Check)
                missed = wip.addAndGet(-missed);
            } while (missed != 0);
        }
    }
}
