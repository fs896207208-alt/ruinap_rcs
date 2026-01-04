package com.ruinap.infra.async;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.framework.test.SpringRunner;
import com.ruinap.infra.thread.VthreadPool;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

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
// 指定使用我们的自定义运行器
@RunWith(SpringRunner.class)
// 指定扫描路径（这里简单指向自己，从而扫描当前包）
@SpringBootTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AsyncServiceTest {

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
    public void test01_WaitAnyTask_Success() throws TimeoutException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "Success");
        String result = asyncService.waitAnyTask(future, 1, TimeUnit.SECONDS);
        Assert.assertEquals("Success", result);
        System.out.println("1. waitAnyTask 正常返回测试通过");
    }

    /**
     * 测试：waitAnyTask - 触发超时异常
     */
    @Test
    public void test02_WaitAnyTask_TimeoutException() {
        // 模拟一个永远完不成的任务
        CompletableFuture<String> future = new CompletableFuture<>();

        System.out.println("2. 正在测试超时抛出异常...");
        long start = System.currentTimeMillis();

        try {
            // 设置 100ms 超时
            asyncService.waitAnyTask(future, 100, TimeUnit.MILLISECONDS);

            // 如果代码走到这里，说明没抛异常，那就是真的 Fail 了
            Assert.fail("【测试失败】预期应当抛出 TimeoutException，但未抛出！");
        } catch (TimeoutException e) {
            long cost = System.currentTimeMillis() - start;
            System.out.println(">>> [验证通过] 成功捕获预期异常: " + e.getClass().getSimpleName());
            System.out.println(">>> 耗时: " + cost + "ms");

            // 可以在这里断言一下确实是 TimeoutException
            Assert.assertTrue(e instanceof TimeoutException);
        } catch (Exception e) {
            Assert.fail("【测试失败】抛出了错误的异常类型: " + e.getClass().getName());
        }
    }

    /**
     * 测试：waitAnyTask - 超时返回默认值
     */
    @Test
    public void test03_WaitAnyTask_DefaultValue() throws TimeoutException {
        CompletableFuture<String> future = new CompletableFuture<>();

        long start = System.currentTimeMillis();
        // 设置 100ms 超时，期望返回 "Default"
        String result = asyncService.waitAnyTask(future, 100, TimeUnit.MILLISECONDS, "Default");
        long cost = System.currentTimeMillis() - start;

        Assert.assertEquals("Default", result);
        Assert.assertTrue("超时控制应当生效", cost >= 90);
        System.out.println("3. waitAnyTask 默认值回退测试通过");
    }

    // ==========================================
    // 2. 批量任务等待测试 (Wait All)
    // ==========================================

    /**
     * 测试：waitAllTasks - 等待所有完成并保持顺序
     */
    @Test
    public void test04_WaitAllTasks() {
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

        Assert.assertEquals(3, results.size());
        Assert.assertEquals(Integer.valueOf(1), results.get(0)); // 即使 f1 最慢，结果也该在第一个
        Assert.assertEquals(Integer.valueOf(2), results.get(1));
        Assert.assertEquals(Integer.valueOf(3), results.get(2));
        System.out.println("4. waitAllTasks 顺序一致性测试通过");
    }

    // ==========================================
    // 3. 严格顺序执行测试 (Strictly Sequential)
    // ==========================================

    /**
     * 测试：executeStrictlySequential - 验证串行时序
     */
    @Test
    public void test05_StrictlySequential() {
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
        Assert.assertEquals("ResA", results.get(0));
        Assert.assertEquals("ResB", results.get(1));
        Assert.assertEquals("ResC", results.get(2));

        // 验证执行顺序：必须是 A -> B -> C
        String logString = String.join("", logList);
        Assert.assertEquals("ABC", logString);

        // 验证耗时：至少包含 A 的 50ms 等待，说明 B 确实是在 A 之后才跑的
        Assert.assertTrue("必须串行执行", cost >= 50);

        System.out.println("5. executeStrictlySequential 串行逻辑测试通过");
    }

    /**
     * 测试：executeStrictlySequential - 异常处理
     * 场景：中间某个步骤失败，中断整个流程
     */
    @Test
    public void test06_StrictlySequential_WithException() {
        List<Supplier<CompletableFuture<String>>> tasks = Arrays.asList(
                () -> CompletableFuture.supplyAsync(() -> "OK1"),
                () -> CompletableFuture.failedFuture(new RuntimeException("Step 2 Failed")),
                () -> CompletableFuture.supplyAsync(() -> "OK3")
        );

        List<String> results = asyncService.executeStrictlySequential(tasks);

        Assert.assertEquals("OK1", results.get(0));
        Assert.assertNull("异常步骤应返回 null", results.get(1));
        Assert.assertEquals("OK3", results.get(2));
        System.out.println("6. executeStrictlySequential 异常容错测试通过");
    }

    // ==========================================
    // 4. 并行批处理测试 (Parallel Processing)
    // ==========================================

    /**
     * 测试：runParallel (基础版)
     */
    @Test
    public void test07_RunParallel_Basic() {
        List<Integer> inputs = Arrays.asList(1, 2, 3, 4, 5);
        AtomicInteger sum = new AtomicInteger(0);

        // 使用 CopyOnWriteArrayList 记录并发执行的线程名称，验证是否使用了不同的虚拟线程
        List<String> threads = new CopyOnWriteArrayList<>();

        asyncService.runParallel(inputs, num -> {
            threads.add(Thread.currentThread().toString());
            sum.addAndGet(num);
        });

        Assert.assertEquals(15, sum.get());
        // 简单验证使用了虚拟线程
        Assert.assertTrue("应使用虚拟线程", threads.getFirst().contains("VirtualThread") || threads.getFirst().contains("vt-"));
        System.out.println("7. runParallel 基础并发测试通过");
    }

    /**
     * 测试：runParallel (带防重/跳过逻辑)
     * 场景：输入 [A, B, A, C]，条件是“跳过 A”，期望只处理 B 和 C
     */
    @Test
    public void test08_RunParallel_WithSkip() {
        List<String> items = Arrays.asList("A", "B", "A", "C");
        List<String> processed = new CopyOnWriteArrayList<>();

        asyncService.runParallel(
                items,
                item -> item, // key mapper: item 本身就是 key
                key -> key.equals("A"), // skip condition: 如果是 A 就跳过
                item -> processed.add(item) // action
        );

        // 验证 A 被跳过，B C 被处理
        Assert.assertFalse("A 应该被跳过", processed.contains("A"));
        Assert.assertTrue("B 应该被处理", processed.contains("B"));
        Assert.assertTrue("C 应该被处理", processed.contains("C"));
        Assert.assertEquals(2, processed.size());

        System.out.println("8. runParallel 防重/跳过逻辑测试通过");
    }
}
