package com.ruinap.infra.lock;

import cn.hutool.core.thread.ThreadUtil;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RcsLock 核心并发测试
 * <p>
 * 目标：
 * 1. 验证三种锁模式 (Reentrant, ReadWrite, Stamped) 的基本功能。
 * 2. 验证高并发下的原子性（数据安全）。
 * 3. 验证高级特性（乐观读、超时锁、Condition）。
 * <p>
 * 注意：本测试不依赖 Spring 容器，直接运行，速度极快。
 *
 * @author qianye
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RcsLockTest {

    @BeforeEach
    void printSeparator() {
        System.out.println("\n══════════════════════════════════════════════════════");
    }

    // ==========================================
    // 1. REENTRANT (互斥锁) 测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("互斥锁 - 并发累加安全性")
    void testReentrantMutex() throws InterruptedException {
        System.out.println("★ 1. 测试互斥锁 (Mutual Exclusion)");
        RcsLock lock = RcsLock.ofReentrant();
        int taskCount = 100;
        int loops = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        // 使用普通 int 模拟非线程安全变量，验证锁的保护作用
        final int[] unsafeValue = {0};

        CountDownLatch latch = new CountDownLatch(taskCount);

        // 启动 100 个线程并发累加
        for (int i = 0; i < taskCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < loops; j++) {
                        lock.runInWrite(() -> {
                            unsafeValue[0]++; // 临界区
                        });
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        boolean finished = latch.await(2, TimeUnit.SECONDS);
        assertTrue(finished, "测试超时，可能发生了死锁");

        System.out.println("   [RESULT] 预期值: " + (taskCount * loops));
        System.out.println("   [RESULT] 实际值: " + unsafeValue[0]);

        assertEquals(taskCount * loops, unsafeValue[0], "数据不一致，互斥锁未生效");
    }

    @Test
    @Order(2)
    @DisplayName("互斥锁 - 可重入性 (Reentrancy)")
    void testReentrantRecursion() {
        System.out.println("★ 2. 测试互斥锁重入性");
        RcsLock lock = RcsLock.ofReentrant();

        assertDoesNotThrow(() -> {
            lock.runInWrite(() -> {
                System.out.println("   [Level 1] 进入第一层锁");
                lock.runInWrite(() -> {
                    System.out.println("   [Level 2] 进入第二层锁 (重入成功)");
                    lock.runInWrite(() -> {
                        System.out.println("   [Level 3] 进入第三层锁 (重入成功)");
                    });
                });
            });
        });
        System.out.println("   [RESULT] 重入测试通过，未发生死锁");
    }

    @Test
    @Order(3)
    @DisplayName("互斥锁 - Condition 机制")
    void testCondition() throws InterruptedException {
        System.out.println("★ 3. 测试 Condition 等待/唤醒");
        RcsLock lock = RcsLock.ofReentrant();
        Condition condition = lock.newCondition();
        AtomicInteger step = new AtomicInteger(0);

        // 线程 T1: 等待信号
        Thread t1 = new Thread(() -> {
            lock.runInWrite(() -> {
                try {
                    System.out.println("   [T1] 拿到锁，开始 await...");
                    step.set(1);
                    condition.await(); // 释放锁并等待
                    System.out.println("   [T1] 被唤醒，继续执行");
                    step.set(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });

        t1.start();

        // 确保 T1 已经进入 await 状态
        while (step.get() != 1) {
            ThreadUtil.sleep(10);
        }

        // 线程 T2: 发送信号
        ThreadUtil.sleep(100);
        lock.runInWrite(() -> {
            System.out.println("   [T2] 拿到锁，发送 signal");
            step.set(2);
            condition.signal();
        });

        t1.join();
        assertEquals(3, step.get(), "Condition 流程执行顺序错误");
    }

    // ==========================================
    // 2. READ_WRITE (读写锁) 测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("读写锁 - 读读共享 & 写锁排他")
    void testReadWriteLock() throws InterruptedException {
        System.out.println("★ 4. 测试读写锁 (Read-Write)");
        RcsLock lock = RcsLock.ofReadWrite();
        AtomicInteger readCount = new AtomicInteger(0);
        CountDownLatch readLatch = new CountDownLatch(3);

        // 1. 模拟一个写锁正在持有 (Block Reads)
        Thread writer = new Thread(() -> {
            lock.runInWrite(() -> {
                System.out.println("   [Writer] 写锁持有中 (100ms)...");
                ThreadUtil.sleep(100);
            });
        });
        writer.start();
        ThreadUtil.sleep(10); // 确保写锁先拿到

        long start = System.currentTimeMillis();

        // 2. 启动 3 个读线程
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                lock.runInRead(() -> {
                    System.out.println("   [Reader] 读取数据...");
                    readCount.incrementAndGet();
                    ThreadUtil.sleep(50); // 模拟耗时读取
                });
                readLatch.countDown();
            }).start();
        }

        readLatch.await();
        long cost = System.currentTimeMillis() - start;

        System.out.println("   [STATS] 总耗时: " + cost + "ms");

        // 验证：
        // 写锁耗时 100ms，读锁并发耗时 50ms。
        // 如果是互斥的，总耗时 = 100 + 50*3 = 250ms
        // 如果读是共享的，总耗时 ≈ 100 + 50 = 150ms
        assertTrue(cost < 200, "读操作应该是并行的，不应串行阻塞");
        assertEquals(3, readCount.get());
    }

    // ==========================================
    // 3. STAMPED (印章锁) 测试
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("印章锁 - 乐观读成功 (No Write)")
    void testStampedOptimisticSuccess() {
        System.out.println("★ 5. 测试印章锁乐观读 - 成功场景");
        RcsLock lock = RcsLock.ofStamped();

        String result = lock.optimisticRead(
                // 1. 尝试乐观读 (无锁)
                () -> {
                    System.out.println("   [Optimistic] 尝试乐观读取...");
                    return "SUCCESS";
                },
                // 2. 降级逻辑 (不应触发)
                () -> {
                    fail("不应触发降级");
                    return "FAILURE";
                }
        );

        assertEquals("SUCCESS", result);
    }

    @Test
    @Order(6)
    @DisplayName("印章锁 - 乐观读失败自动降级 (Write Interfere)")
    void testStampedOptimisticFallback() throws InterruptedException {
        System.out.println("★ 6. 测试印章锁乐观读 - 失败降级场景");
        RcsLock lock = RcsLock.ofStamped();
        final int[] data = {100};

        // 1. 启动一个线程，在乐观读的间隙修改数据
        CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
            ThreadUtil.sleep(50); // 等待主线程先开始乐观读
            lock.runInWrite(() -> {
                System.out.println("   [Writer] 😈 恶意修改数据 -> 200");
                data[0] = 200;
            });
        });

        // 2. 主线程执行乐观读
        Integer result = lock.optimisticRead(
                // Attempt: 模拟耗时读取，故意让写线程插队
                () -> {
                    System.out.println("   [Optimistic] 开始尝试...");
                    int val = data[0];
                    ThreadUtil.sleep(100); // 睡得比写线程久，保证在此期间发生写操作
                    System.out.println("   [Optimistic] 尝试结束 (读取到旧值/脏值: " + val + ")");
                    return val;
                },
                // Fallback: 悲观读兜底
                () -> {
                    System.out.println("   [Fallback] ⚠️ 校验失败，降级为悲观读锁");
                    return data[0];
                }
        );

        writeFuture.join();

        System.out.println("   [RESULT] 最终获取值: " + result);

        // 验证：乐观读虽然可能读到了 100，但 validate 失败，最终应该通过 fallback 拿到 200
        assertEquals(200, result, "降级机制未生效，拿到了脏数据");
    }

    // ==========================================
    // 4. 异常安全性测试
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("异常安全性 - 异常后锁应自动释放")
    void testExceptionSafety() {
        System.out.println("★ 7. 测试异常安全性");
        RcsLock lock = RcsLock.ofReentrant();

        // 1. 抛出异常
        assertThrows(RuntimeException.class, () -> {
            lock.runInWrite(() -> {
                throw new RuntimeException("业务异常");
            });
        });

        // 2. 验证锁是否被释放 (如果没释放，再次加锁会卡死或无法获取)
        // 尝试立即获取锁
        boolean success = false;
        try {
            lock.runInWrite(() -> System.out.println("   [Check] 锁已释放，可以重新获取"));
            success = true;
        } catch (Exception e) {
            success = false;
        }

        assertTrue(success, "发生异常后锁未释放");
    }
}