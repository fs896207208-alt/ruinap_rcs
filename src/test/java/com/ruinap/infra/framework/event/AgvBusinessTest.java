package com.ruinap.infra.framework.event;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author qianye
 * @create 2025-12-12 10:32
 */
@SpringBootTest // 组合注解，已包含 @ExtendWith(SpringRunner.class)
@DisplayName("AGV任务事件业务测试")
class AgvBusinessTest {

    @Autowired
    private AgvTaskService agvTaskService;

    @BeforeEach
    void setup() {
        // 每个测试方法执行前清空日志
        ExternalListeners.LOGS.clear();
    }

    /**
     * 测试场景：任务开始 (EXECUTING)
     * 预期：WMS 和 Web 收到通知 (异步)，WCS 不收 (因为不是 COMPLETED)
     */
    @Test
    @DisplayName("测试：任务开始事件 (异步+过滤)")
    void testTaskStart() throws InterruptedException {
        System.out.println("=== 测试场景：任务开始 ===");

        // 只有 2 个异步监听器会被触发 (WMS, Web)
        ExternalListeners.ASYNC_LATCH = new CountDownLatch(2);

        agvTaskService.updateTaskStatus("T001", "AGV_1", AgvTaskEvent.TaskStatus.EXECUTING, "出发");

        // 等待异步完成
        boolean ok = ExternalListeners.ASYNC_LATCH.await(2, TimeUnit.SECONDS);
        // JUnit 6: (condition, message)
        Assertions.assertTrue(ok, "异步任务超时，未在规定时间内收到所有事件回调");

        // 验证日志数量 (WMS + Web = 2)
        Assertions.assertEquals(2, ExternalListeners.LOGS.size(), "监听器触发数量不符合预期");

        // 验证异步执行 (检查日志中记录的线程名是否包含 vt- 或 task-)
        // 注意：ExternalListeners.LOGS 里的字符串格式是 "WMS收到:xxx|线程:vt-xx"
        boolean hasVirtualThread = ExternalListeners.LOGS.stream()
                .anyMatch(log -> log.contains("vt-") || log.contains("task-")); // 兼容虚拟线程池命名

        Assertions.assertTrue(hasVirtualThread, "监听器应当在虚拟线程或异步线程池中执行");

        System.out.println("=== 通过 ===");
    }

    /**
     * 测试场景：任务完成 (COMPLETED)
     * 预期：WMS, Web, WCS 全部收到
     */
    @Test
    @DisplayName("测试：任务完成事件 (全广播)")
    void testTaskComplete() throws InterruptedException {
        System.out.println("=== 测试场景：任务完成 ===");

        // 2 个异步监听器 (WMS, Web) + 1 个同步监听器 (WCS)
        // 注意：CountDownLatch 通常用于异步等待。同步监听器在 updateTaskStatus 返回前就执行完了。
        // 这里假设 ExternalListeners.ASYNC_LATCH 仅控制异步部分。
        ExternalListeners.ASYNC_LATCH = new CountDownLatch(2);

        agvTaskService.updateTaskStatus("T001", "AGV_1", AgvTaskEvent.TaskStatus.COMPLETED, "到站");

        // WCS 是同步监听，updateTaskStatus 返回时它应该已经写了日志
        // 等待另外两个异步的
        boolean ok = ExternalListeners.ASYNC_LATCH.await(2, TimeUnit.SECONDS);
        Assertions.assertTrue(ok, "异步任务超时");

        // 验证日志数量 (WMS + Web + WCS = 3)
        Assertions.assertEquals(3, ExternalListeners.LOGS.size(), "应当触发所有3个监听器");

        // 打印日志以供调试
        ExternalListeners.LOGS.forEach(System.out::println);

        System.out.println("=== 通过 ===");
    }
}
