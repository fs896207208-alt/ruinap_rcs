package com.ruinap.infra.pool;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.thread.VthreadPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VthreadPool 单元测试
 * 基于 JDK 21 虚拟线程验证
 *
 * @author qianye
 * @create 2025-12-05 16:11
 */
// 1. 指定 Runner，确保容器启动
@SpringBootTest
// 2. 升级排序注解: 按方法名升序执行
@TestMethodOrder(MethodOrderer.MethodName.class)
class VthreadPoolTest {

    @Autowired
    private VthreadPool vthreadPool;

    // ----------------------------------------------------------------
    // 1. 基础执行测试
    // ----------------------------------------------------------------

    /**
     * 测试 execute: 验证任务是否在虚拟线程中执行
     */
    @Test
    void test01_ExecuteVirtualThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger check = new AtomicInteger(0);

        vthreadPool.execute(() -> {
            boolean isVirtual = Thread.currentThread().isVirtual();
            System.out.println("1. execute running in: " + Thread.currentThread());
            if (isVirtual) {
                check.set(1);
            }
            latch.countDown();
        });

        latch.await();
        // JUnit 6: assertTrue(condition, message)
        Assertions.assertTrue(check.get() == 1, "任务应当在虚拟线程中执行");
        System.out.println("1. execute 测试通过");
    }

    /**
     * 测试 runAsync (有返回值)
     */
    @Test
    void test02_RunAsyncReturnValue() {
        CompletableFuture<String> future = vthreadPool.supplyAsync(() -> {
            System.out.println("2. supplyAsync running...");
            return "Success";
        });

        String result = future.join();
        // JUnit 6: assertEquals(expected, actual, message)
        Assertions.assertEquals("Success", result, "异步返回值不匹配");
        System.out.println("2. supplyAsync 测试通过: " + result);
    }

    /**
     * 测试 runAsync (异常处理)
     */
    @Test
    void test03_RunAsyncException() {
        CompletableFuture<Void> future = vthreadPool.runAsync(() -> {
            throw new RuntimeException("Simulated Error");
        });

        boolean caught = false;
        try {
            future.join();
        } catch (Exception e) {
            caught = true;
            System.out.println("3. 捕获到预期异常: " + e.getMessage());
        }
        Assertions.assertTrue(caught, "应当捕获到异步任务的异常");
        System.out.println("3. runAsync 异常处理测试通过");
    }

    // ----------------------------------------------------------------
    // 2. Future 提交测试
    // ----------------------------------------------------------------

    @Test
    void test04_SubmitRunnable() throws ExecutionException, InterruptedException {
        Future<?> future = vthreadPool.submit(() -> {
            // do nothing
        });
        Object result = future.get();
        Assertions.assertNull(result, "Runnable submit 应该返回 null");
        System.out.println("4. submit(Runnable) 测试通过");
    }

    @Test
    void test05_SubmitCallable() throws ExecutionException, InterruptedException {
        Future<String> future = vthreadPool.submit(() -> "CallableResult");
        String result = future.get();
        Assertions.assertEquals("CallableResult", result, "Callable submit 返回值错误");
        System.out.println("5. submit(Callable) 测试通过");
    }

    // ----------------------------------------------------------------
    // 3. 延时任务测试
    // ----------------------------------------------------------------

    @Test
    void test06_ScheduleDelay() throws InterruptedException {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);

        vthreadPool.schedule(() -> {
            latch.countDown();
        }, 100, TimeUnit.MILLISECONDS);

        latch.await();
        long cost = System.currentTimeMillis() - start;
        System.out.println("6. 延时任务耗时: " + cost + "ms");

        Assertions.assertTrue(cost >= 100, "延时时间不足 100ms");
        System.out.println("6. schedule 延时测试通过");
    }

    // ----------------------------------------------------------------
    // 4. 周期任务测试 (重点)
    // ----------------------------------------------------------------

    /**
     * 测试 scheduleAtFixedRate: 固定频率
     * 这里的逻辑是让它跑几次然后手动停止
     */
    @Test
    void test07_ScheduleAtFixedRate() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        // 50ms 执行一次
        ScheduledFuture<?> future = vthreadPool.scheduleAtFixedRate(() -> {
            int c = counter.incrementAndGet();
            System.out.println("7. FixedRate running: " + c);
            latch.countDown();
        }, 0, 50, TimeUnit.MILLISECONDS);

        // 等待执行5次
        latch.await();
        future.cancel(true); // 停止任务

        Assertions.assertTrue(counter.get() >= 5, "至少应该执行5次");
        System.out.println("7. scheduleAtFixedRate 测试通过");
    }

    /**
     * 测试 scheduleWithFixedDelay: 固定延时 (串行保证)
     */
    @Test
    void test08_ScheduleWithFixedDelay() throws InterruptedException {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(3);

        // 任务本身耗时 50ms，间隔 50ms
        // 理论执行时间点：0ms(start) -> 50ms(end) -> [wait 50ms] -> 100ms(start) -> 150ms(end) ...
        vthreadPool.scheduleWithFixedDelay(() -> {
            try {
                Thread.sleep(50); // 模拟耗时
            } catch (InterruptedException e) {
            }
            latch.countDown();
        }, 0, 50, TimeUnit.MILLISECONDS);

        latch.await();
        long totalCost = System.currentTimeMillis() - start;

        // 3次执行：
        // 1. 0ms开始 -> 50ms结束
        // 2. 间隔50ms -> 100ms开始 -> 150ms结束
        // 3. 间隔50ms -> 200ms开始 -> 250ms结束
        // 所以 3次执行至少需要 200ms~250ms 以上，如果小于150ms说明是并发执行了（错误的）

        Assertions.assertTrue(totalCost >= 200, "FixedDelay 应当串行执行，耗时过短: " + totalCost);
        System.out.println("8. scheduleWithFixedDelay 测试通过，串行耗时: " + totalCost + "ms");
    }

    /**
     * 测试 scheduleAtFixedRateTimes: 有限次数
     */
    @Test
    void test09_ScheduleAtFixedRateTimes() throws InterruptedException {
        int targetTimes = 3;
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(targetTimes + 2); // 多等一点，看会不会超发

        vthreadPool.scheduleAtFixedRateTimes(() -> {
            counter.incrementAndGet();
            latch.countDown();
        }, 0, 50, targetTimes, TimeUnit.MILLISECONDS);

        // 等待足够长的时间，确保如果有第4次也会被执行
        latch.await(300, TimeUnit.MILLISECONDS);

        Assertions.assertEquals(targetTimes, counter.get(), "执行次数必须严格等于 " + targetTimes);
        System.out.println("9. scheduleAtFixedRateTimes 测试通过");
    }

    // ----------------------------------------------------------------
    // 5. 监控测试
    // ----------------------------------------------------------------

    @Test
    void test10_Monitor() {
        String info = vthreadPool.monitorThreadPool();
        System.out.println("10. 监控信息输出:\n" + info);
        Assertions.assertNotNull(info);
        Assertions.assertTrue(info.contains("平台线程总数"));
        Assertions.assertTrue(info.contains("JDK 21"));
    }
}