package com.ruinap.infra.thread;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RcsTaskExecutor 任务编排器测试
 * <p>
 * 核心验证：
 * 1. executeAllSuccess: 全员成功逻辑，以及"一损俱损"的中断机制。
 * 2. executeAnySuccess: 最快胜出逻辑，验证慢任务是否被取消。
 * 3. runParallel: 并行执行能力。
 * 4. 真实环境集成: 注入真实的 VthreadPool 工作。
 * </p>
 *
 * @author qianye
 * @create 2026-01-06 14:43
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RcsTaskExecutorTest {

    private static RcsTaskExecutor taskExecutor;
    private static VthreadPool vthreadPool;

    @BeforeAll
    static void init() {
        System.out.println("██████████ [START] 启动 RcsTaskExecutor 测试 ██████████");

        // 1. 初始化真实的 VthreadPool
        vthreadPool = new VthreadPool();
        vthreadPool.run(); // 启动线程池

        // 2. 初始化待测对象
        taskExecutor = new RcsTaskExecutor();

        // 3. 注入依赖 (利用反射)
        ReflectUtil.setFieldValue(taskExecutor, "vthreadPool", vthreadPool);

        System.out.println("   [INIT] VthreadPool & RcsTaskExecutor 就绪");
    }

    @AfterAll
    static void destroy() {
        vthreadPool.shutdown();
        System.out.println("██████████ [END] 测试结束 ██████████");
    }

    // ==========================================
    // 1. executeAllOrThrow (全有或全无 / Fail-Fast)
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("模式一：全员成功 (Happy Path)")
    void testExecuteAllOrThrow_Happy() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("\n--- 测试：全员成功 (正常流程) ---");
        long start = System.currentTimeMillis();

        // 3个任务并行，最慢的耗时 100ms
        List<Callable<String>> tasks = Arrays.asList(
                () -> {
                    ThreadUtil.sleep(50);
                    return "TaskA";
                },
                () -> {
                    ThreadUtil.sleep(100);
                    return "TaskB";
                },
                () -> {
                    ThreadUtil.sleep(20);
                    return "TaskC";
                }
        );

        // [修复] 方法名改为 executeAllOrThrow
        List<String> results = taskExecutor.executeAllOrThrow(tasks, null);
        long cost = System.currentTimeMillis() - start;

        System.out.println("   [RESULT] 耗时: " + cost + "ms, 结果: " + results);

        // 验证：
        // 1. 结果数量对
        assertEquals(3, results.size());
        // 2. 结果内容对
        assertTrue(results.contains("TaskA") && results.contains("TaskB") && results.contains("TaskC"));
        // 3. 并行验证：耗时应该接近最慢的任务 (100ms)，而不是总和 (170ms)
        assertTrue(cost < 150, "任务应该是并行执行的");
    }

    @Test
    @Order(2)
    @DisplayName("模式一：全员成功 (异常中断测试)")
    void testExecuteAllOrThrow_Exception() {
        System.out.println("\n--- 测试：全员成功 (异常熔断) ---");

        // 场景：TaskB 抛异常，TaskA 耗时很久。验证 TaskB 挂掉后，TaskA 是否会被 cancel。

        AtomicBoolean taskA_Interrupted = new AtomicBoolean(false);

        Callable<String> taskA = () -> {
            try {
                System.out.println("   [TaskA] 开始运行 (预计 500ms)");
                Thread.sleep(500); // 故意睡久点
                return "TaskA";
            } catch (InterruptedException e) {
                System.out.println("   [TaskA] 收到中断信号，停止运行！");
                taskA_Interrupted.set(true);
                throw e;
            }
        };

        Callable<String> taskB = () -> {
            System.out.println("   [TaskB] 运行并抛出异常");
            ThreadUtil.sleep(50);
            throw new RuntimeException("TaskB Boom!");
        };

        // 执行
        Exception exception = assertThrows(Exception.class, () -> {
            // [修复] 方法名改为 executeAllOrThrow
            taskExecutor.executeAllOrThrow(Arrays.asList(taskA, taskB), null);
        });

        System.out.println("   [MAIN] 捕获到预期异常: " + exception.getMessage());

        // 稍微等待一下中断传播
        ThreadUtil.sleep(100);

        // 验证：TaskA 应该被自动中断 (Cancel)
        assertTrue(taskA_Interrupted.get(), "TaskB 失败后，TaskA 应该被自动中断(Cancel)");
    }

    // ==========================================
    // 2. executeAnySuccess (最快胜出)
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("模式二：最快胜出 (Any Success)")
    void testExecuteAnySuccess() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("\n--- 测试：最快胜出 ---");

        List<Callable<String>> tasks = Arrays.asList(
                () -> {
                    ThreadUtil.sleep(200);
                    return "Slow";
                },
                () -> {
                    ThreadUtil.sleep(50);
                    return "Fast";
                },
                () -> {
                    ThreadUtil.sleep(100);
                    throw new RuntimeException("Error");
                }
        );

        String result = taskExecutor.executeAnySuccess(tasks, null);
        System.out.println("   [RESULT] 胜出者: " + result);

        assertEquals("Fast", result);
    }

    @Test
    @Order(4)
    @DisplayName("模式二：最快胜出 (超时测试)")
    void testExecuteAnySuccess_Timeout() {
        System.out.println("\n--- 测试：最快胜出 (整体超时) ---");

        List<Callable<String>> tasks = Arrays.asList(
                () -> {
                    ThreadUtil.sleep(200);
                    return "Slow1";
                },
                () -> {
                    ThreadUtil.sleep(300);
                    return "Slow2";
                }
        );

        Instant deadline = Instant.now().plusMillis(100);

        assertThrows(TimeoutException.class, () -> {
            taskExecutor.executeAnySuccess(tasks, deadline);
        });
        System.out.println("   [MAIN] 成功捕获 TimeoutException");
    }

    // ==========================================
    // 3. runParallel (普通并行)
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("模式三：普通并行批处理")
    void testRunParallel() {
        System.out.println("\n--- 测试：普通并行 ---");
        AtomicInteger counter = new AtomicInteger(0);
        int taskCount = 100;

        List<Runnable> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            tasks.add(counter::incrementAndGet);
        }

        long start = System.currentTimeMillis();
        taskExecutor.runParallel(tasks);
        long cost = System.currentTimeMillis() - start;

        System.out.println("   [RESULT] 执行 " + taskCount + " 个任务耗时: " + cost + "ms");

        assertEquals(taskCount, counter.get());
    }
}