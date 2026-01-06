package com.ruinap.infra.thread;

import cn.hutool.core.date.DateUtil;
import org.junit.jupiter.api.*;

import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VthreadPool 单元测试
 * 基于 JDK 21 虚拟线程验证
 *
 * @author qianye
 * @create 2025-12-05 16:11
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VthreadPoolTest {

    private static VthreadPool vthreadPool;

    @BeforeAll
    static void init() {
        System.out.println("██████████ [START] 启动 VthreadPool 测试环境 ██████████");

        // 1. 初始化对象
        vthreadPool = new VthreadPool();

        // 2. 启动线程池
        // 注意：这里直接使用真实的 RcsLog，不再进行反射替换。
        // 日志会根据 log4j2.xml 的配置输出到控制台。
        vthreadPool.run();
    }

    @AfterAll
    static void destroy() {
        System.out.println("██████████ [END] 销毁 VthreadPool 测试环境 ██████████");
        vthreadPool.shutdown();
    }

    // ==========================================
    // 1. 核心提交与虚拟线程验证
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("验证 JDK21 虚拟线程特性")
    void testExecute_VirtualThreadIdentity() throws ExecutionException, InterruptedException, TimeoutException {
        printTitle("1. 虚拟线程身份验证");

        CompletableFuture<String> future = new CompletableFuture<>();

        vthreadPool.execute(() -> {
            Thread t = Thread.currentThread();
            // 获取线程信息
            String msg = String.format("线程名: %-10s | 是否虚拟线程: %-5s | 守护线程: %s",
                    t.getName(), t.isVirtual(), t.isDaemon());

            // 直接打印，方便观测
            System.out.println("   [TASK] " + msg);

            future.complete(msg);
        });

        String result = future.get(2, TimeUnit.SECONDS);

        // 断言验证
        assertTrue(result.contains("是否虚拟线程: true"), "错误：任务未运行在虚拟线程上 (JDK 21特性未生效)");
        assertTrue(result.contains("vt-"), "错误：线程名未包含 'vt-' 前缀");
    }

    @Test
    @Order(2)
    @DisplayName("Submit (Callable) 返回值测试")
    void testSubmit_Callable() throws ExecutionException, InterruptedException {
        printTitle("2. Submit(Callable) 测试");

        Future<Integer> future = vthreadPool.submit(() -> {
            System.out.println("   [TASK] 正在计算 100 + 200...");
            Thread.sleep(50);
            return 300;
        });

        Integer result = future.get();
        System.out.println("   [MAIN] 收到结果: " + result);
        assertEquals(300, result);
    }

    @Test
    @Order(3)
    @DisplayName("CompletableFuture 异步流式测试")
    void testAsync_CompletableFuture() {
        printTitle("3. runAsync / supplyAsync 测试");

        String finalResult = vthreadPool.supplyAsync(() -> {
            System.out.println("   [TASK] 阶段1: 获取数据 (run on " + Thread.currentThread().getName() + ")");
            return "DATA";
        }).thenApplyAsync(s -> {
            System.out.println("   [TASK] 阶段2: 加工数据 (run on " + Thread.currentThread().getName() + ")");
            return "PROCESSED_" + s;
        }, vthreadPool.getExecutor()).join();

        System.out.println("   [MAIN] 最终结果: " + finalResult);
        assertEquals("PROCESSED_DATA", finalResult);
    }

    // ==========================================
    // 2. 调度逻辑测试 (重头戏)
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("Schedule 单次延时")
    void testSchedule() throws ExecutionException, InterruptedException {
        printTitle("4. 单次延时调度 (100ms)");
        long start = System.currentTimeMillis();

        ScheduledFuture<?> future = vthreadPool.schedule(() -> {
            System.out.println("   [TASK] 闹钟响了！时间: " + System.currentTimeMillis());
        }, 100, TimeUnit.MILLISECONDS);

        future.get(); // 阻塞等待执行完
        long cost = System.currentTimeMillis() - start;
        System.out.println("   [MAIN] 实际耗时: " + cost + "ms");

        // 考虑到系统调度误差，放宽一点点限制
        assertTrue(cost >= 90, "延时时间不足，实际耗时太短");
    }

    @Test
    @Order(5)
    @DisplayName("ScheduleAsync 异步延时")
    void testScheduleAsync() {
        printTitle("5. 异步延时调度 (CompletableFuture)");

        String res = vthreadPool.scheduleAsync(() -> {
                    System.out.println("   [TASK] 异步延时任务执行");
                }, 50, TimeUnit.MILLISECONDS)
                .thenApply(v -> "DONE")
                .join();

        assertEquals("DONE", res);
    }

    @Test
    @Order(6)
    @DisplayName("ScheduleAtFixedRate 标准周期调度")
    void testScheduleAtFixedRate() throws InterruptedException {
        printTitle("6. 标准周期调度 (FixedRate)");
        CountDownLatch latch = new CountDownLatch(3);

        vthreadPool.scheduleAtFixedRate(() -> {
            System.out.println("   [TASK] FixedRate 滴答... " + DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS"));
            latch.countDown();
        }, 0, 1000, TimeUnit.MILLISECONDS);

        latch.await(8000, TimeUnit.MILLISECONDS);
    }

    /**
     * 重点验证：自定义的 "执行->休眠->循环" 逻辑
     */
    @Test
    @Order(7)
    @DisplayName("ScheduleWithFixedDelay 自定义串行调度")
    void testScheduleWithFixedDelay() throws InterruptedException {
        printTitle("7. 自定义串行调度 (FixedDelay)");
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        // 间隔 30ms
        Future<?> loopTask = vthreadPool.scheduleWithFixedDelay(() -> {
            int c = count.incrementAndGet();
            System.out.println("   [TASK] 第 " + c + " 次执行 (Thread: " + Thread.currentThread().getName() + ")");
            try {
                Thread.sleep(10);
            } catch (Exception e) {
            }
            latch.countDown();
        }, 0, 30, TimeUnit.MILLISECONDS);

        boolean done = latch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(done, "未完成3次调用");

        // 验证取消
        System.out.println("   [MAIN] >>> 发起取消指令 <<<");
        loopTask.cancel(true);

        int countBefore = count.get();
        Thread.sleep(100); // 等待几个周期，确保循环真正停止
        int countAfter = count.get();

        assertEquals(countBefore, countAfter, "任务应该停止，计数器不应增加");
        System.out.println("   [MAIN] 任务成功停止，最终次数: " + countAfter);
    }

    /**
     * 重点验证：有限次数调度
     */
    @Test
    @Order(8)
    @DisplayName("ScheduleAtFixedRateTimes 有限次数")
    void testScheduleAtFixedRateTimes() throws InterruptedException {
        printTitle("8. 有限次数调度 (Target: 4次)");
        int target = 4;
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(target);

        vthreadPool.scheduleAtFixedRateTimes(() -> {
            int c = count.incrementAndGet();
            System.out.println("   [TASK] 倒数执行: " + c + "/" + target);
            latch.countDown();
        }, 0, 20, target, TimeUnit.MILLISECONDS);

        latch.await(500, TimeUnit.MILLISECONDS);
        assertEquals(target, count.get());

        // 验证是否自动停止
        Thread.sleep(100);
        assertEquals(target, count.get(), "达到次数后必须自动停止");
        System.out.println("   [MAIN] 计数停在 " + count.get() + "，验证通过");
    }

    // ==========================================
    // 3. 监控与异常
    // ==========================================

    @Test
    @Order(9)
    @DisplayName("监控与工厂测试")
    void testMonitor() {
        printTitle("9. 监控信息打印");

        // 打印监控信息
        String info = vthreadPool.monitorThreadPool();
        System.out.println(info);

        assertNotNull(info);
        assertTrue(info.contains("JDK 21"), "监控信息应包含 JDK 21");

        // 验证工厂
        ThreadFactory factory = vthreadPool.getDaemonThread("my-vt-");
        Thread t = factory.newThread(() -> {
        });
        assertTrue(t.getName().startsWith("my-vt-"));
        assertTrue(t.isDaemon());
    }

    @Test
    @Order(10)
    @DisplayName("异常隔离性测试")
    void testExceptionSafe() {
        printTitle("10. 异常隔离测试");

        vthreadPool.execute(() -> {
            System.out.println("   [TASK] 我要抛异常了！Boom!");
            throw new RuntimeException("测试异常");
        });

        // 稍微等一下，让异常飞一会儿
        try {
            Thread.sleep(20);
        } catch (Exception e) {
        }

        String res = vthreadPool.supplyAsync(() -> "I'm OK").join();
        System.out.println("   [MAIN] 后续任务结果: " + res);
        assertEquals("I'm OK", res, "线程池不应受单个任务异常影响");
    }

    // ----------------------------------------------------
    // 辅助工具
    // ----------------------------------------------------

    private void printTitle(String title) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("★ " + title);
        System.out.println("--------------------------------------------------");
    }
}