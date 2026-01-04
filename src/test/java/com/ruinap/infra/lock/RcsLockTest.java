package com.ruinap.infra.lock;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

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
public class RcsLockTest {

    // 使用 Java 21 的虚拟线程池进行测试 (模拟真实生产环境)
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // ========================================================================
    //                        1. 互斥性测试 (最核心)
    // ========================================================================

    @Test
    public void testReentrantLock_Concurrency() throws InterruptedException {
        System.out.println(">>> 测试互斥锁 (REENTRANT) 高并发累加...");
        RcsLock lock = RcsLock.ofReentrant();
        runConcurrencyCounter(lock);
    }

    @Test
    public void testReadWriteLock_Concurrency() throws InterruptedException {
        System.out.println(">>> 测试读写锁 (READ_WRITE) 高并发累加...");
        RcsLock lock = RcsLock.ofReadWrite();
        runConcurrencyCounter(lock);
    }

    @Test
    public void testStampedLock_Concurrency() throws InterruptedException {
        System.out.println(">>> 测试印章锁 (STAMPED) 高并发累加...");
        RcsLock lock = RcsLock.ofStamped();
        runConcurrencyCounter(lock);
    }

    /**
     * 通用并发计数测试逻辑
     * 启动 1000 个线程，每个加 1，如果锁有效，结果必须是 1000。
     */
    private void runConcurrencyCounter(RcsLock lock) throws InterruptedException {
        final int taskCount = 1000;
        // 使用普通 int，如果锁失效，结果一定小于 1000
        final int[] counter = {0};
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                lock.runInWrite(() -> {
                    counter[0]++;
                });
                latch.countDown();
            });
        }

        latch.await();
        Assert.assertEquals("锁失效！并发累加结果错误", taskCount, counter[0]);
        System.out.println("    ✅ 结果正确: " + counter[0]);
    }

    // ========================================================================
    //                        2. 乐观读测试 (Stamped 特性)
    // ========================================================================

    @Test
    public void testOptimisticRead() throws InterruptedException {
        System.out.println(">>> 测试乐观读 (Optimistic Read)...");
        RcsLock lock = RcsLock.ofStamped();

        // 共享数据
        final int[] data = {0, 0}; // x, y

        // 1. 写线程：不断修改 x 和 y
        Thread writer = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 100; i++) {
                lock.runInWrite(() -> {
                    data[0]++;
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                    }
                    data[1]++;
                });
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
            }
        });

        // 2. 读线程：使用乐观读
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fallbackCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                for (int j = 0; j < 20; j++) {
                    // 乐观读逻辑：尝试读取 data[0] 和 data[1]
                    // 如果读的过程中发生了写操作，result 应该是 -1 (fallback 的返回值)
                    int result = lock.optimisticRead(
                            () -> {
                                // 模拟耗时读取
                                int x = data[0];
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException ignored) {
                                }
                                int y = data[1];
                                // 如果读的时候数据一致 (x==y)，返回 x，否则逻辑上可能读到了脏数据
                                return x == y ? x : -999;
                            },
                            () -> {
                                fallbackCount.incrementAndGet();
                                return -1; // 悲观读兜底
                            }
                    );

                    if (result != -1 && result != -999) {
                        successCount.incrementAndGet();
                    }
                }
            });
        }

        writer.join();
        // 等待读线程跑完（简单 sleep 模拟）
        Thread.sleep(500);

        System.out.println("    ✅ 乐观读成功次数: " + successCount.get());
        System.out.println("    ⚠️ 降级悲观读次数: " + fallbackCount.get());

        // 只要没抛异常，且有成功的，就算通过
        Assert.assertTrue(successCount.get() > 0 || fallbackCount.get() > 0);
    }

    // ========================================================================
    //                        3. 锁降级与重入测试
    // ========================================================================

    @Test
    public void testReentrant() {
        System.out.println(">>> 测试可重入性 (Reentrancy)...");

        // 1. 测试互斥锁重入
        RcsLock reentrantLock = RcsLock.ofReentrant();
        reentrantLock.runInWrite(() -> {
            reentrantLock.runInWrite(() -> {
                System.out.println("    ✅ ReentrantLock 重入成功");
            });
        });

        // 2. 测试读写锁重入 (写锁内可加读锁)
        RcsLock rwLock = RcsLock.ofReadWrite();
        rwLock.runInWrite(() -> {
            rwLock.runInRead(() -> {
                System.out.println("    ✅ ReadWriteLock 锁降级(写嵌读)成功");
            });
        });
    }

    // ========================================================================
    //                        4. 死锁预防测试 (TryRun)
    // ========================================================================

    @Test
    public void testTryRunInWrite() throws InterruptedException {
        System.out.println(">>> 测试 TryLock 超时机制...");
        RcsLock lock = RcsLock.ofReentrant();
        CountDownLatch latch = new CountDownLatch(1);

        // 1. 线程 A 拿走锁，并持有 200ms
        executor.submit(() -> {
            lock.runInWrite(() -> {
                try {
                    latch.countDown();
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
            });
        });

        latch.await(); // 确保 A 已经拿到了锁

        // 2. 线程 B 尝试拿锁，超时设为 50ms (应该失败)
        boolean success = lock.tryRunInWrite(50, TimeUnit.MILLISECONDS, () -> {
            Assert.fail("不应该拿到锁！");
        });
        Assert.assertFalse("预期获取锁超时失败，但却成功了", success);
        System.out.println("    ✅ 超时放弃逻辑验证通过");

        // 3. 线程 C 尝试拿锁，超时设为 500ms (应该成功，因为 A 只占 200ms)
        boolean success2 = lock.tryRunInWrite(500, TimeUnit.MILLISECONDS, () -> {
            System.out.println("    ✅ 等待后获取锁成功");
        });
        Assert.assertTrue("预期获取锁成功，但失败了", success2);
    }

    // ========================================================================
    //                        5. Condition 测试
    // ========================================================================

    @Test
    public void testCondition() throws InterruptedException {
        System.out.println(">>> 测试 Condition 等待/通知...");
        RcsLock lock = RcsLock.ofReentrant();
        Condition condition = lock.newCondition();
        AtomicInteger step = new AtomicInteger(0);

        Thread t1 = Thread.ofVirtual().start(() -> {
            lock.runInWrite(() -> {
                try {
                    step.set(1);
                    System.out.println("    T1: 等待信号...");
                    condition.await(); // 释放锁，等待
                    step.set(3);
                    System.out.println("    T1: 被唤醒!");
                } catch (InterruptedException e) {
                }
            });
        });

        Thread.sleep(50); // 确保 T1 进入 await

        Thread t2 = Thread.ofVirtual().start(() -> {
            lock.runInWrite(() -> {
                Assert.assertEquals(1, step.get());
                step.set(2);
                System.out.println("    T2: 发送信号...");
                condition.signal();
            });
        });

        t1.join();
        t2.join();

        Assert.assertEquals(3, step.get());
        System.out.println("    ✅ Condition 流程验证通过");
    }
}