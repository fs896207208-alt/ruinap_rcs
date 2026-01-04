package com.ruinap.infra.pool;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.framework.test.SpringRunner;
import com.ruinap.infra.thread.VthreadPool;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VthreadPool 单元测试
 * 基于 JDK 21 虚拟线程验证
 *
 * @author qianye
 * @create 2025-12-05 16:11
 */
// 1. 指定 Runner，确保容器启动
@RunWith(SpringRunner.class)
// 2. 指定这是个集成测试
@SpringBootTest
// 按名称顺序执行，保持日志整洁
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VthreadPoolTest {

    @Autowired
    private VthreadPool vthreadPool;

    // ----------------------------------------------------------------
    // 1. 基础执行测试
    // ----------------------------------------------------------------

    /**
     * 测试 execute: 验证任务是否在虚拟线程中执行
     */
    @Test
    public void test01_Execute() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        vthreadPool.execute(() -> {
            try {
                // JDK 21 核心验证
                Assert.assertTrue("execute任务必须在虚拟线程运行", Thread.currentThread().isVirtual());
                System.out.println("1. execute 测试通过: " + Thread.currentThread());
            } finally {
                latch.countDown();
            }
        });
        Assert.assertTrue("execute 超时", latch.await(2, TimeUnit.SECONDS));
    }

    /**
     * 测试 submit: 验证 Callable 返回值
     */
    @Test
    public void test02_Submit() throws ExecutionException, InterruptedException {
        String input = "AGV-001";
        Future<String> future = vthreadPool.submit(() -> {
            Assert.assertTrue("submit任务必须在虚拟线程运行", Thread.currentThread().isVirtual());
            return "Hello " + input;
        });

        Assert.assertEquals("Hello AGV-001", future.get());
        System.out.println("2. submit 测试通过，返回值正确");
    }

    // ----------------------------------------------------------------
    // 2. 异步编排测试 (CompletableFuture)
    // ----------------------------------------------------------------

    /**
     * 测试 runAsync: 验证 CompletableFuture<Void>
     */
    @Test
    public void test03_RunAsync() {
        CompletableFuture<Void> future = vthreadPool.runAsync(() -> {
            Assert.assertTrue(Thread.currentThread().isVirtual());
            System.out.println("3. runAsync 测试运行");
        });
        // 阻塞等待完成，无异常即通过
        future.join();
        System.out.println("3. runAsync 测试通过");
    }

    /**
     * 测试 supplyAsync: 验证 CompletableFuture<T>
     */
    @Test
    public void test04_SupplyAsync() {
        CompletableFuture<Integer> future = vthreadPool.supplyAsync(() -> {
            Assert.assertTrue(Thread.currentThread().isVirtual());
            return 100 + 200;
        });
        Assert.assertEquals(Integer.valueOf(300), future.join());
        System.out.println("4. supplyAsync 测试通过");
    }

    // ----------------------------------------------------------------
    // 3. 延时调度测试
    // ----------------------------------------------------------------

    /**
     * 测试 schedule: 单次延时 (ScheduledFuture)
     */
    @Test
    public void test05_Schedule() throws Exception {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        vthreadPool.schedule(() -> {
            Assert.assertTrue("schedule任务必须在虚拟线程运行", Thread.currentThread().isVirtual());
            latch.countDown();
        }, 200, TimeUnit.MILLISECONDS);

        Assert.assertTrue("schedule 任务丢失", latch.await(1, TimeUnit.SECONDS));
        long cost = System.currentTimeMillis() - start;

        // 允许少量误差，但不能早于 200ms
        Assert.assertTrue("执行过快，延时未生效: " + cost, cost >= 190);
        System.out.println("5. schedule 测试通过，实际延时: " + cost + "ms");
    }

    /**
     * 测试 scheduleAsync: 验证 Future 是否真正等待业务逻辑执行完毕
     * 场景：延迟 200ms + 业务执行 500ms -> 预期总耗时 >= 700ms
     */
    @Test
    public void test06_ScheduleAsync_Strict() {
        System.out.println("=== 开始严苛测试 scheduleAsync ===");
        long start = System.currentTimeMillis();

        // 1. 提交异步任务
        CompletableFuture<String> future = vthreadPool.scheduleAsync(() -> {
            try {
                // 模拟耗时的业务逻辑 (关键点！)
                Thread.sleep(500);
                System.out.println("业务逻辑执行完毕");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 200, TimeUnit.MILLISECONDS).thenApply(v -> "Done");

        // 2. 阻塞等待结果
        String res = future.join();
        long cost = System.currentTimeMillis() - start;

        System.out.println("总耗时: " + cost + "ms");

        // 3. 验证结果
        Assert.assertEquals("Done", res);

        // 【关键断言】
        // 如果代码有Bug，这里的 cost 只有 200ms 左右，断言会失败
        // 如果代码已修复，这里的 cost 应该是 200 + 500 = 700ms 左右
        Assert.assertTrue("Bug检测：Future未等待任务执行完毕就返回了！", cost >= 690);

        System.out.println("6. scheduleAsync 严苛测试通过");
    }

    // ----------------------------------------------------------------
    // 4. 周期性调度测试 (重点)
    // ----------------------------------------------------------------

    /**
     * 测试 scheduleAtFixedRate: 固定频率
     * 场景：每 100ms 触发一次，不管任务执行多久（前提是任务耗时 < 周期）
     */
    @Test
    public void test07_ScheduleAtFixedRate() throws InterruptedException {
        int expectCount = 5;
        CountDownLatch latch = new CountDownLatch(expectCount);
        AtomicLong lastRun = new AtomicLong(System.currentTimeMillis());

        System.out.println("7. 开始测试 FixedRate (预计500ms)...");

        ScheduledFuture<?> future = vthreadPool.scheduleAtFixedRate(() -> {
            Assert.assertTrue(Thread.currentThread().isVirtual());
            latch.countDown();
        }, 0, 100, TimeUnit.MILLISECONDS);

        boolean finished = latch.await(1000, TimeUnit.MILLISECONDS);
        future.cancel(true); // 停止任务

        Assert.assertTrue("未完成指定次数的调度", finished);
        System.out.println("7. scheduleAtFixedRate 测试通过");
    }

    /**
     * 测试 scheduleWithFixedDelay: 固定间隔 (串行)
     * 场景：任务耗时 50ms，间隔 50ms -> 实际周期应为 100ms
     */
    @Test
    public void test08_ScheduleWithFixedDelay() throws InterruptedException {
        int expectCount = 3;
        CountDownLatch latch = new CountDownLatch(expectCount);
        long start = System.currentTimeMillis();

        Future<?> future = vthreadPool.scheduleWithFixedDelay(() -> {
            try {
                // 模拟业务耗时 50ms
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            latch.countDown();
        }, 0, 50, TimeUnit.MILLISECONDS); // 间隔也是 50ms

        latch.await(2000, TimeUnit.MILLISECONDS);
        long totalCost = System.currentTimeMillis() - start;
        future.cancel(true);

        // 理论耗时：
        // 第1次：0ms开始 + 50ms执行
        // 间隔：50ms
        // 第2次：100ms开始 + 50ms执行
        // 间隔：50ms
        // 第3次：200ms开始 + 50ms执行 -> 结束时刻约 250ms
        // 所以 3次执行至少需要 200ms~250ms 以上，如果小于150ms说明是并发执行了（错误的）

        Assert.assertTrue("FixedDelay 应当串行执行，耗时过短: " + totalCost, totalCost >= 200);
        System.out.println("8. scheduleWithFixedDelay 测试通过，串行耗时: " + totalCost + "ms");
    }

    /**
     * 测试 scheduleAtFixedRateTimes: 有限次数
     */
    @Test
    public void test09_ScheduleAtFixedRateTimes() throws InterruptedException {
        int targetTimes = 3;
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(targetTimes + 2); // 多等一点，看会不会超发

        vthreadPool.scheduleAtFixedRateTimes(() -> {
            counter.incrementAndGet();
            latch.countDown();
        }, 0, 50, targetTimes, TimeUnit.MILLISECONDS);

        // 等待足够长的时间，确保如果有第4次也会被执行
        latch.await(300, TimeUnit.MILLISECONDS);

        Assert.assertEquals("执行次数必须严格等于 " + targetTimes, targetTimes, counter.get());
        System.out.println("9. scheduleAtFixedRateTimes 测试通过");
    }

    // ----------------------------------------------------------------
    // 5. 监控测试
    // ----------------------------------------------------------------

    @Test
    public void test10_Monitor() {
        String info = vthreadPool.monitorThreadPool();
        System.out.println("10. 监控信息输出:\n" + info);
        Assert.assertNotNull(info);
        Assert.assertTrue(info.contains("平台线程总数"));
        Assert.assertTrue(info.contains("JDK 21"));
    }
}