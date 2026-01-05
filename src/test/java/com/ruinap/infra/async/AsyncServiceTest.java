package com.ruinap.infra.async;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.thread.VthreadPool;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * AsyncUtils 单元测试
 * 验证：超时控制、顺序执行、并行处理及去重逻辑
 *
 * @author qianye
 * @create 2025-12-05 17:24
 */
// 1. 移除 @RunWith，由 @SpringBootTest 组合注解接管
@SpringBootTest
// 2. 升级排序注解: 按方法名升序执行
@TestMethodOrder(MethodOrderer.MethodName.class)
@DisplayName("异步服务(AsyncService)测试")
class AsyncServiceTest {

    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private AsyncService asyncService;

    // ==========================================
    // 1. 超时控制测试 (Timeout Control)
    // ==========================================

    /**
     * 测试：waitAnyTask - 正常完成
     */
    @Test
    @DisplayName("测试：waitAnyTask 正常返回")
    void test01_WaitAnyTask_Success() throws TimeoutException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "Success");
        String result = asyncService.waitAnyTask(future, 1, TimeUnit.SECONDS);
        // JUnit 6: assertEquals(expected, actual)
        Assertions.assertEquals("Success", result);
        System.out.println("1. waitAnyTask 正常返回测试通过");
    }

    /**
     * 测试：waitAnyTask - 触发超时异常
     */
    @Test
    @DisplayName("测试：waitAnyTask 超时异常")
    void test02_WaitAnyTask_TimeoutException() {
        // 模拟一个永远完不成的任务
        CompletableFuture<String> future = new CompletableFuture<>();

        System.out.println("2. 正在测试超时抛出异常...");
        long start = System.currentTimeMillis();

        try {
            // 设置 100ms 超时
            asyncService.waitAnyTask(future, 100, TimeUnit.MILLISECONDS);

            // 如果代码走到这里，说明没抛异常，那就是真的 Fail 了
            Assertions.fail("【测试失败】预期应当抛出 TimeoutException，但未抛出！");
        } catch (TimeoutException e) {
            long cost = System.currentTimeMillis() - start;
            System.out.println(">>> [验证通过] 成功捕获预期异常: " + e.getClass().getSimpleName());
            System.out.println(">>> 耗时: " + cost + "ms");

            // 可以在这里断言一下确实是 TimeoutException
            Assertions.assertTrue(e instanceof TimeoutException);
        } catch (Exception e) {
            Assertions.fail("【测试失败】抛出了错误的异常类型: " + e.getClass().getName());
        }
    }

    /**
     * 测试：waitAnyTask - 超时返回默认值
     */
    @Test
    @DisplayName("测试：waitAnyTask 默认值回退")
    void test03_WaitAnyTask_DefaultValue() throws TimeoutException {
        CompletableFuture<String> future = new CompletableFuture<>();

        long start = System.currentTimeMillis();
        // 设置 100ms 超时，期望返回 "Default"
        String result = asyncService.waitAnyTask(future, 100, TimeUnit.MILLISECONDS, "Default");
        long cost = System.currentTimeMillis() - start;

        Assertions.assertEquals("Default", result);
        // JUnit 6: assertTrue(condition, message) - 参数互换
        Assertions.assertTrue(cost >= 90, "超时控制应当生效");
        System.out.println("3. waitAnyTask 默认值回退测试通过");
    }

    // ==========================================
    // 2. 批量任务等待测试 (Wait All)
    // ==========================================

    /**
     * 测试：waitAllTasks - 等待所有完成并保持顺序
     */
    @Test
    @DisplayName("测试：waitAllTasks 顺序一致性")
    void test04_WaitAllTasks() {
        // 创建三个不同耗时的任务
        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
            return 1;
        });
        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(() -> 2);
        CompletableFuture<Integer> f3 = CompletableFuture.supplyAsync(() -> 3);

        List<Integer> results = asyncService.waitAllTasks(Arrays.asList(f1, f2, f3));

        Assertions.assertEquals(3, results.size());
        Assertions.assertEquals(Integer.valueOf(1), results.get(0)); // 即使 f1 最慢，结果也该在第一个
        Assertions.assertEquals(Integer.valueOf(2), results.get(1));
        Assertions.assertEquals(Integer.valueOf(3), results.get(2));
        System.out.println("4. waitAllTasks 顺序一致性测试通过");
    }

    // ==========================================
    // 3. 严格顺序执行测试 (Strictly Sequential)
    // ==========================================

    /**
     * 测试：executeStrictlySequential - 验证串行时序
     */
    @Test
    @DisplayName("测试：executeStrictlySequential 串行逻辑")
    void test05_StrictlySequential() {
        // 使用并发安全的 List 记录执行痕迹
        List<String> logList = new CopyOnWriteArrayList<>();

        List<Supplier<CompletableFuture<String>>> tasks = Arrays.asList(
                () -> vthreadPool.supplyAsync(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (Exception e) {
                    }
                    logList.add("A");
                    return "ResA";
                }),
                () -> vthreadPool.supplyAsync(() -> {
                    logList.add("B");
                    return "ResB";
                }),
                () -> vthreadPool.supplyAsync(() -> {
                    logList.add("C");
                    return "ResC";
                })
        );

        long start = System.currentTimeMillis();
        List<String> results = asyncService.executeStrictlySequential(tasks);
        long cost = System.currentTimeMillis() - start;

        // 验证结果顺序
        Assertions.assertEquals("ResA", results.get(0));
        Assertions.assertEquals("ResB", results.get(1));
        Assertions.assertEquals("ResC", results.get(2));

        // 验证执行顺序：必须是 A -> B -> C
        String logString = String.join("", logList);
        // JUnit 6: assertEquals(expected, actual, message)
        Assertions.assertEquals("ABC", logString);

        // 验证耗时：至少包含 A 的 50ms 等待，说明 B 确实是在 A 之后才跑的
        Assertions.assertTrue(cost >= 50, "必须串行执行");

        System.out.println("5. executeStrictlySequential 串行逻辑测试通过");
    }

    /**
     * 测试：executeStrictlySequential - 异常处理
     * 场景：中间某个步骤失败，中断整个流程
     */
    @Test
    @DisplayName("测试：executeStrictlySequential 异常中断")
    void test06_StrictlySequential_WithException() {
        List<Supplier<CompletableFuture<String>>> tasks = Arrays.asList(
                () -> CompletableFuture.supplyAsync(() -> "OK1"),
                () -> CompletableFuture.failedFuture(new RuntimeException("Step 2 Failed")),
                () -> CompletableFuture.supplyAsync(() -> "OK3")
        );

        List<String> results = asyncService.executeStrictlySequential(tasks);

        Assertions.assertEquals("OK1", results.get(0));
        // JUnit 6: assertNull(actual, message) - 参数互换
        Assertions.assertNull(results.get(1), "异常步骤应返回 null");
        Assertions.assertEquals("OK3", results.get(2));
        System.out.println("6. executeStrictlySequential 异常容错测试通过");
    }

    // ==========================================
    // 4. 并行批处理测试 (Parallel Processing)
    // ==========================================

    /**
     * 测试：runParallel (基础版)
     */
    @Test
    @DisplayName("测试：runParallel 基础并发")
    void test07_RunParallel_Basic() {
        List<Integer> inputs = Arrays.asList(1, 2, 3, 4, 5);
        AtomicInteger sum = new AtomicInteger(0);

        // 使用 CopyOnWriteArrayList 记录并发执行的线程名称，验证是否使用了不同的虚拟线程
        List<String> threads = new CopyOnWriteArrayList<>();

        asyncService.runParallel(inputs, num -> {
            threads.add(Thread.currentThread().toString());
            sum.addAndGet(num);
        });

        Assertions.assertEquals(15, sum.get());
        // 简单验证使用了虚拟线程
        Assertions.assertTrue(threads.getFirst().contains("VirtualThread") || threads.getFirst().contains("vt-"), "应使用虚拟线程");
        System.out.println("7. runParallel 基础并发测试通过");
    }

    /**
     * 测试：runParallel (带防重/跳过逻辑)
     * 场景：输入 [A, B, A, C]，条件是“跳过 A”，期望只处理 B 和 C
     */
    @Test
    @DisplayName("测试：runParallel 防重逻辑")
    void test08_RunParallel_WithSkip() {
        List<String> items = Arrays.asList("A", "B", "A", "C");
        List<String> processed = new CopyOnWriteArrayList<>();

        asyncService.runParallel(
                items,
                item -> item, // key mapper: item 本身就是 key
                key -> key.equals("A"), // skip condition: 如果是 A 就跳过
                item -> processed.add(item) // action
        );

        // 验证 A 被跳过，B C 被处理
        Assertions.assertFalse(processed.contains("A"), "A 应该被跳过");
        Assertions.assertTrue(processed.contains("B"), "B 应该被处理");
        Assertions.assertTrue(processed.contains("C"), "C 应该被处理");
        Assertions.assertEquals(2, processed.size());

        System.out.println("8. runParallel 防重/跳过逻辑测试通过");
    }
}