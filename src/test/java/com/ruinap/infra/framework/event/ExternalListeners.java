package com.ruinap.infra.framework.event;

import com.ruinap.infra.framework.annotation.Async;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.EventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * @author qianye
 * @create 2025-12-12 10:31
 */
@Component
public class ExternalListeners {
    // === 测试探针 (用于 Assert 断言) ===
    public static final List<String> LOGS = new CopyOnWriteArrayList<>();
    // 我们预期会有 2 个异步任务 (WMS, Web) 和 1 个同步任务 (WCS-如果满足条件)
    // 为了测试方便，我们只卡异步的 Latch
    public static CountDownLatch ASYNC_LATCH;

    // 1. WMS (异步)
    @EventListener
    @Async
    public void notifyWms(AgvTaskEvent event) {
        try {
            Thread.sleep(50); // 模拟耗时
            String log = String.format("WMS收到:%s|线程:%s", event.getStatus(), Thread.currentThread().getName());
            System.out.println("  [WMS] " + log);

            LOGS.add(log);
        } catch (InterruptedException e) {
        } finally {
            if (ASYNC_LATCH != null) ASYNC_LATCH.countDown();
        }
    }

    // 2. Web (异步)
    @EventListener
    @Async
    public void pushToWeb(AgvTaskEvent event) {
        String log = String.format("Web收到:%s|线程:%s", event.getStatus(), Thread.currentThread().getName());
        System.out.println("  [Web] " + log);

        LOGS.add(log);
        if (ASYNC_LATCH != null) ASYNC_LATCH.countDown();
    }

    // 3. WCS (同步 - 仅完成时触发)
    @EventListener
    public void notifyWcs(AgvTaskEvent event) {
        if (event.getStatus() == AgvTaskEvent.TaskStatus.COMPLETED) {
            String log = String.format("WCS收到:%s|线程:%s", event.getStatus(), Thread.currentThread().getName());
            System.out.println("  [WCS] " + log);

            LOGS.add(log);
        }
    }
}
