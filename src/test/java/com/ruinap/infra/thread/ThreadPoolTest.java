package com.ruinap.infra.thread;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.config.CoreYaml;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ThreadPool (CPU密集型线程池) 核心测试
 * <p>
 * 测试目标：
 * 1. 验证平台线程特性 (非虚拟线程)。
 * 2. 验证 CoreYaml 配置注入是否生效。
 * 3. [核心] 验证拒绝策略 CallerRunsPolicy (反压机制)。
 * 4. 验证任务提交与结果获取。
 * </p>
 *
 * @author qianye
 * @create 2026-01-06 14:23
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ThreadPoolTest {

    private static ThreadPool threadPool;
    private static CoreYaml mockCoreYaml;

    @BeforeEach
    void setUp() {
        System.out.println("\n══════════════════════════════════════════════════════");

        // 1. 初始化待测对象
        threadPool = new ThreadPool();

        // 2. Mock 依赖配置 (CoreYaml)
        // 为了方便测试拒绝策略，我们将池子设得很小：1个核心，1个最大，1个队列
        mockCoreYaml = Mockito.mock(CoreYaml.class);
        when(mockCoreYaml.getAlgoCorePoolSize()).thenReturn(1);
        when(mockCoreYaml.getAlgoMaxPoolSize()).thenReturn(1);
        when(mockCoreYaml.getAlgoQueueCapacity()).thenReturn(1); // 队列容量仅为 1
        when(mockCoreYaml.getAlgoKeepAliveSeconds()).thenReturn(60);

        // 3. 注入 Mock 依赖
        ReflectUtil.setFieldValue(threadPool, "coreYaml", mockCoreYaml);

        // 4. 手动触发初始化
        threadPool.init();

        System.out.println("   [INIT] ThreadPool 初始化完成 (Mock配置: Core=1, Max=1, Queue=1)");
    }

    @AfterEach
    void tearDown() {
        // 销毁
        threadPool.shutdown();
        System.out.println("   [DESTROY] ThreadPool 已关闭");
    }

    // ==========================================
    // 1. 基础特性测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("平台线程特性验证")
    void testPlatformThreadFeature() throws ExecutionException, InterruptedException {
        System.out.println("★ 1. 验证线程类型 (必须是平台线程)");

        CompletableFuture<String> future = new CompletableFuture<>();

        threadPool.execute(() -> {
            Thread t = Thread.currentThread();
            String info = String.format("Name=%s, isVirtual=%s, isDaemon=%s",
                    t.getName(), t.isVirtual(), t.isDaemon());
            System.out.println("   [TASK] 执行线程信息: " + info);
            future.complete(info);
        });

        String result = future.get();

        // 断言
        assertTrue(result.contains("Name=rt-"), "线程名应以 'rt-' 开头");
        assertTrue(result.contains("isVirtual=false"), "必须是平台线程，严禁使用虚拟线程！");
        assertTrue(result.contains("isDaemon=true"), "必须是守护线程");
    }

    @Test
    @Order(2)
    @DisplayName("Callable 任务提交")
    void testSubmitCallable() throws ExecutionException, InterruptedException {
        System.out.println("★ 2. 验证 submit(Callable) 返回值");

        Future<Integer> future = threadPool.submit(() -> {
            System.out.println("   [TASK] 正在执行复杂计算...");
            ThreadUtil.sleep(50);
            return 1024;
        });

        Integer result = future.get();
        System.out.println("   [RESULT] 计算结果: " + result);
        assertEquals(1024, result);
    }

    // ==========================================
    // 2. 拒绝策略测试 (CallerRunsPolicy)
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("反压机制测试 (CallerRunsPolicy)")
    void testCallerRunsPolicy() throws InterruptedException {
        System.out.println("★ 3. 验证拒绝策略 (队列满时由调用者执行)");

        // 当前配置：Max=1, Queue=1.
        // 这意味着系统最多同时容纳 2 个任务 (1个正在跑 + 1个在排队)。
        // 第 3 个任务提交时，应该触发 CallerRunsPolicy，在当前主线程(main)执行。

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger mainThreadExecCount = new AtomicInteger(0);
        AtomicInteger poolThreadExecCount = new AtomicInteger(0);
        String mainThreadName = Thread.currentThread().getName();

        // 定义一个耗时任务
        Runnable heavyTask = () -> {
            String currentName = Thread.currentThread().getName();
            System.out.println("   [TASK] 任务执行中... Thread: " + currentName);

            if (currentName.equals(mainThreadName)) {
                mainThreadExecCount.incrementAndGet(); // 记在主线程账上
            } else {
                poolThreadExecCount.incrementAndGet(); // 记在线程池账上
            }

            // 模拟耗时，占住线程
            ThreadUtil.sleep(200);
            latch.countDown();
        };

        // 提交 3 个任务
        // Task 1: 立即被核心线程执行 (占用 Core)
        // Task 2: 进入队列 (占用 Queue)
        // Task 3: 队列满 -> 触发拒绝策略 -> 在 Main 线程执行

        System.out.println("   >> 提交 Task 1 (应在 rt-0 线程执行)");
        threadPool.execute(heavyTask);

        System.out.println("   >> 提交 Task 2 (应进入队列等待)");
        threadPool.execute(heavyTask);

        // 稍微停顿确保 Task 1 占住了 Core，Task 2 占住了 Queue
        ThreadUtil.sleep(20);

        System.out.println("   >> 提交 Task 3 (应被拒绝并在 " + mainThreadName + " 线程执行)");
        threadPool.execute(heavyTask);

        // 等待所有任务完成
        latch.await(2, TimeUnit.SECONDS);

        System.out.println("   [STATS] 主线程执行次数: " + mainThreadExecCount.get());
        System.out.println("   [STATS] 线程池执行次数: " + poolThreadExecCount.get());

        // 验证
        // 至少有 1 个任务是在主线程执行的 (CallerRuns 生效)
        assertTrue(mainThreadExecCount.get() >= 1, "拒绝策略未生效！没有任务在主线程执行");
        // 至少有 1 个任务是在线程池执行的
        assertTrue(poolThreadExecCount.get() >= 1, "线程池罢工了？");
    }

    @Test
    @Order(4)
    @DisplayName("懒加载验证 (Execute触发Init)")
    void testLazyInit() {
        System.out.println("★ 4. 验证懒加载机制");

        // 创建一个新的未初始化的池
        ThreadPool lazyPool = new ThreadPool();
        // 注入配置
        ReflectUtil.setFieldValue(lazyPool, "coreYaml", mockCoreYaml);

        // 此时 executor 应该是 null
        Object executor = ReflectUtil.getFieldValue(lazyPool, "executor");
        assertNull(executor, "初始状态 executor 应为 null");

        // 执行任务，应该自动触发 init()
        lazyPool.execute(() -> {
        });

        // 再次检查
        executor = ReflectUtil.getFieldValue(lazyPool, "executor");
        assertNotNull(executor, "调用 execute 后 executor 应被初始化");

        lazyPool.shutdown();
    }
}
