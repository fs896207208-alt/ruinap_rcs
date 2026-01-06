package com.ruinap.infra.async;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.thread.VthreadPool;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncUtils 单元测试
 * 验证：超时控制、顺序执行、并行处理及去重逻辑
 *
 * @author qianye
 * @create 2025-12-05 17:24
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsyncServiceTest {

    private static AsyncService asyncService;
    private static VthreadPool vthreadPool;

    @BeforeAll
    static void initGlobal() {
        System.out.println("██████████ [START] 启动 AsyncService 测试 ██████████");

        // 1. 启动真实的 VthreadPool
        vthreadPool = new VthreadPool();
        vthreadPool.run();

        // 2. 初始化待测对象
        asyncService = new AsyncService();

        // 3. 注入依赖
        // 注意：根据源码，字段名为 "vThreadPool" (驼峰)
        ReflectUtil.setFieldValue(asyncService, "vThreadPool", vthreadPool);
    }

    @AfterAll
    static void destroyGlobal() {
        vthreadPool.shutdown();
        System.out.println("██████████ [END] 测试结束 ██████████");
    }

    // ==========================================
    // 1. executeAndGet (单任务超时等待)
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("执行并获取结果 - 正常场景")
    void testExecuteAndGet_Success() throws TimeoutException {
        System.out.println("\n--- 测试：executeAndGet (Success) ---");

        String result = asyncService.executeAndGet(() -> {
            ThreadUtil.sleep(50);
            return "OK";
        }, 1000, TimeUnit.MILLISECONDS);

        assertEquals("OK", result);
        System.out.println("   [PASS] 成功获取结果");
    }

    @Test
    @Order(2)
    @DisplayName("执行并获取结果 - 超时异常")
    void testExecuteAndGet_Timeout() {
        System.out.println("\n--- 测试：executeAndGet (Timeout) ---");

        // 任务耗时 200ms，超时设置 50ms
        // [修复] AsyncService 直接抛出了 JDK 的 TimeoutException (Checked Exception)，而非 RuntimeException
        assertThrows(TimeoutException.class, () -> {
            asyncService.executeAndGet(() -> {
                ThreadUtil.sleep(200);
                return "Slow";
            }, 50, TimeUnit.MILLISECONDS);
        });

        System.out.println("   [PASS] 成功捕获超时异常: TimeoutException");
    }

    // ==========================================
    // 2. executeAll (并行等待所有)
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("并行执行所有任务 (Runnable)")
    void testExecuteAll() {
        System.out.println("\n--- 测试：executeAll ---");

        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        List<Runnable> tasks = Arrays.asList(
                () -> {
                    ThreadUtil.sleep(50);
                    counter.incrementAndGet();
                },
                () -> {
                    ThreadUtil.sleep(100);
                    counter.incrementAndGet();
                },
                () -> {
                    ThreadUtil.sleep(20);
                    counter.incrementAndGet();
                }
        );

        asyncService.executeAll(tasks);
        long cost = System.currentTimeMillis() - start;

        assertEquals(3, counter.get());
        // 验证并行性：耗时应接近最慢的任务 (100ms)，而非总和 (170ms)
        assertTrue(cost < 160, "任务应并行执行");
        System.out.println("   [PASS] 所有任务完成，耗时: " + cost + "ms");
    }

    // ==========================================
    // 3. runParallel (高级批处理)
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("高级批处理 - 正常逻辑")
    void testRunParallel_Normal() {
        System.out.println("\n--- 测试：runParallel (Normal) ---");

        List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);
        List<String> results = new CopyOnWriteArrayList<>(); // 线程安全List收集结果

        asyncService.runParallel(
                items,
                item -> "Key-" + item, // KeyMapper
                key -> false,          // SkipCondition (不跳过)
                item -> {              // Action
                    String res = "Processed-" + item;
                    results.add(res);
                    System.out.println("   [TASK] " + res);
                }
        );

        assertEquals(5, results.size());
        assertTrue(results.contains("Processed-1"));
        assertTrue(results.contains("Processed-5"));
    }

    @Test
    @Order(5)
    @DisplayName("高级批处理 - 跳过逻辑 (SkipCondition)")
    void testRunParallel_Skip() {
        System.out.println("\n--- 测试：runParallel (Skip) ---");

        List<String> items = Arrays.asList("A", "B", "C");
        List<String> executed = new CopyOnWriteArrayList<>();

        asyncService.runParallel(
                items,
                item -> item, // Key就是本身
                key -> "B".equals(key), // 跳过 "B"
                item -> {
                    executed.add(item);
                    System.out.println("   [TASK] 执行: " + item);
                }
        );

        assertEquals(2, executed.size());
        assertTrue(executed.contains("A"));
        assertTrue(executed.contains("C"));
        assertFalse(executed.contains("B"), "元素 B 应该被跳过");
    }

    @Test
    @Order(6)
    @DisplayName("高级批处理 - 异常隔离 (One Fail, Others Continue)")
    void testRunParallel_Exception() {
        System.out.println("\n--- 测试：runParallel (Exception Isolation) ---");

        List<Integer> items = Arrays.asList(1, 2, 3);
        AtomicInteger successCount = new AtomicInteger(0);

        asyncService.runParallel(
                items,
                String::valueOf,
                key -> false,
                item -> {
                    if (item == 2) {
                        throw new RuntimeException("Task 2 Boom!");
                    }
                    successCount.incrementAndGet();
                    System.out.println("   [TASK] 成功处理: " + item);
                }
        );

        // 验证：任务2失败不应阻塞任务1和3
        assertEquals(2, successCount.get(), "即使任务2失败，任务1和3也应成功");
    }

    @Test
    @Order(7)
    @DisplayName("边界测试 - 空列表")
    void testEmptyList() {
        System.out.println("\n--- 测试：空列表输入 ---");
        assertDoesNotThrow(() -> asyncService.executeAll(null));
        assertDoesNotThrow(() -> asyncService.executeAll(Collections.emptyList()));
        assertDoesNotThrow(() -> asyncService.runParallel(null, i -> "", s -> false, i -> {
        }));
    }
}