package com.ruinap.infra.thread;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.exception.TaskFinishedException;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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

    // ==========================================
    // 4. dispatchTasksConcurrently (带超时的弱网隔离并发下发)
    // ==========================================

    @Test
    @Order(6)
    @DisplayName("模式四：带超时的独立批量下发 (部分成功/部分失败/部分超时)")
    void testDispatchTasksConcurrently() {
        System.out.println("\n--- 测试：模式四 弱网隔离并发下发 ---");
        long start = System.currentTimeMillis();

        List<Callable<String>> tasks = Arrays.asList(
                () -> {
                    // AGV 1: 正常成功
                    ThreadUtil.sleep(50);
                    return "AGV_1_SUCCESS";
                },
                () -> {
                    // AGV 2: 模拟抛出异常（网络拒绝连接）
                    ThreadUtil.sleep(20);
                    throw new RuntimeException("Connection Refused");
                },
                () -> {
                    // AGV 3: 模拟网络死锁，死等ACK
                    ThreadUtil.sleep(2000);
                    return "AGV_3_SUCCESS";
                }
        );

        // 【严苛测试】：设定绝对超时时间为 200 毫秒
        List<String> results = taskExecutor.dispatchTasksConcurrently(tasks, 200, TimeUnit.MILLISECONDS);
        long cost = System.currentTimeMillis() - start;

        System.out.println("   [RESULT] 耗时: " + cost + "ms, 结果: " + results);

        // 验证隔离性
        assertEquals(3, results.size(), "应该返回3个结果（即使有失败和超时，绝不能少）");
        assertEquals("AGV_1_SUCCESS", results.get(0), "AGV_1 应该成功");
        assertNull(results.get(1), "AGV_2 异常，异常兜底机制应该返回 null");
        assertNull(results.get(2), "AGV_3 超时，异常兜底机制应该返回 null");

        // 验证防死锁：由于 orTimeout 机制，哪怕 AGV_3 内部 sleep 2000ms，整个方法也应该在 200ms 出头立刻返回
        assertTrue(cost < 400, "强力超时控制失效，虚拟线程可能发生了死锁，耗时: " + cost);
    }

    // ==========================================
    // 5. dispatchTasksWithRetryConcurrently (自旋重试并发下发)
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("模式五：独立隔离的虚拟线程自旋重试下发")
    void testDispatchTasksWithRetryConcurrently() {
        System.out.println("\n--- 测试：模式五 自旋重试下发 ---");

        AtomicInteger agv1Attempts = new AtomicInteger(0);
        AtomicInteger agv2Attempts = new AtomicInteger(0);
        AtomicBoolean agv1Success = new AtomicBoolean(false);

        List<Runnable> tasks = Arrays.asList(
                () -> {
                    // 模拟 AGV 1: 前2次握手失败，第3次尝试才成功
                    int attempt = agv1Attempts.incrementAndGet();
                    if (attempt < 3) {
                        throw new RuntimeException("AGV_1 Not Ready");
                    }
                    agv1Success.set(true);
                },
                () -> {
                    // 模拟 AGV 2: 彻底断网掉线，永远失败
                    agv2Attempts.incrementAndGet();
                    throw new RuntimeException("AGV_2 Offline");
                }
        );

        // 最大重试 4 次，每次间隔 50 毫秒
        taskExecutor.dispatchTasksWithRetryConcurrently(tasks, 50, 4);

        // 注意：因为模式五是完全异步的 (runAsync 发后即忘)，主线程直接往下走了。
        // 我们在测试环境里强制等一会儿，让底层的虚拟线程把重试循环充分跑完。
        // AGV2 会失败 4 次，每次休眠 50ms。我们等 500ms 确保所有分支尘埃落定。
        ThreadUtil.sleep(500);

        System.out.println("   [RESULT] AGV_1 尝试次数: " + agv1Attempts.get() + ", 最终是否成功: " + agv1Success.get());
        System.out.println("   [RESULT] AGV_2 尝试次数: " + agv2Attempts.get());

        // 验证 AGV 1: 重试机制使其起死回生
        assertTrue(agv1Success.get(), "AGV_1 应该在重试后最终成功");
        assertEquals(3, agv1Attempts.get(), "AGV_1 应该正好尝试了 3 次");

        // 验证 AGV 2: 防治无限死循环 (僵尸线程)
        assertEquals(4, agv2Attempts.get(), "AGV_2 必须被强制拦截，达到最大重试次数限制");
    }

    // ==========================================
    // 6. submitTaskLifecycle (单任务专属点火引擎)
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("模式七：单任务专属点火器 (防重、容错、注册表清理)")
    void testSubmitTaskLifecycle() {
        System.out.println("\n--- 测试：模式七 单任务专属点火器 ---");

        String testAgvId = "AGV_TEST_001";
        AtomicInteger runCount = new AtomicInteger(0);
        AtomicBoolean isSecondaryTaskRun = new AtomicBoolean(false);

        // 1. 构建主任务 (模拟 A段 任务的生命周期)
        Runnable primaryTask = () -> {
            int current = runCount.incrementAndGet();
            System.out.println("   [主虚拟线程] 正在推演第 " + current + " 次周期...");

            if (current == 1) {
                System.out.println("   [主虚拟线程] 💥 模拟底层网络崩溃，抛出未捕获的 RuntimeException！");
                throw new RuntimeException("Mock Netty Connection Reset");
            }
            if (current >= 3) {
                System.out.println("   [主虚拟线程] ✅ A段 任务彻底完成，抛出终态异常，准备触发 finally 清理！");
                throw new TaskFinishedException();
            }
        };

        // 🚀 第一次点火！周期为 100 毫秒
        taskExecutor.submitTaskLifecycle(testAgvId, primaryTask, 100, TimeUnit.MILLISECONDS);

        // 2. 【极限施压】：在主任务还在跑的时候，假设定时器又扫到了这辆车，尝试二次点火！
        Runnable secondaryTask = () -> {
            isSecondaryTaskRun.set(true);
            System.out.println("   [幽灵线程] ❌ 如果这行打印了，说明防重机制彻底失效，发生了线程踩踏！");
        };
        System.out.println("   [主线程] 尝试对 " + testAgvId + " 进行二次恶意重复点火...");
        taskExecutor.submitTaskLifecycle(testAgvId, secondaryTask, 100, TimeUnit.MILLISECONDS);

        // 3. 验证【运行状态】：此时 testAgvId 应该被死死锁在 activeTaskRegistry 中
        assertTrue(taskExecutor.isTaskRunning(testAgvId), "点火后，任务应该进入防重注册表");

        // 4. 让子弹飞一会儿：等待足够的时间让主任务历经磨难并跑完 3 次循环
        // 100ms * 3 = 300ms，加上异常时的重试休眠 100ms，我们等 800ms 确保一切尘埃落定
        ThreadUtil.sleep(800);

        System.out.println("\n   --- 最终验收结果 ---");

        // 5. 验收防重机制
        assertFalse(isSecondaryTaskRun.get(), "第二次恶意点火必须被拦截，SecondaryTask 绝对不应该被执行！");

        // 6. 验收自愈容错能力
        assertEquals(3, runCount.get(), "主线程必须坚强地度过异常，精准执行 3 次后退出");

        // 7. 验收打扫战场能力 (核心红线)
        assertFalse(taskExecutor.isTaskRunning(testAgvId), "任务执行完毕后，finally 必须将其从 activeTaskRegistry 中安全移除！");

        System.out.println("   [测试通过] 防重点火、异常自愈、内存清理全部验证成功！");
    }
}