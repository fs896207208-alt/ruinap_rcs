package com.ruinap.infra.lock;

import com.ruinap.infra.log.RcsLog;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

/**
 * 【核心并发组件】全能型通用锁 (RcsLock)
 * <p>
 * <h3>1. 设计目的</h3>
 * 为了统一系统中的锁机制，屏蔽底层 JDK 锁（ReentrantLock, ReadWriteLock, StampedLock）的 API 差异。
 * 开发者只需通过静态工厂方法选择锁的类型，即可获得一套统一的、安全的、支持智能降级的并发控制接口。
 * </p>
 *
 * <h3>2. 锁类型与适用场景</h3>
 * <table border="1">
 * <tr>
 * <th>锁类型</th>
 * <th>适用场景</th>
 * <th>虚拟线程友好度</th>
 * </tr>
 * <tr>
 * <td><b>REENTRANT</b> (互斥)</td>
 * <td>强一致性、状态流转、交通管制。</td>
 * <td>⭐⭐⭐⭐⭐ (JUC原生支持，自动挂起/恢复)</td>
 * </tr>
 * <tr>
 * <td><b>READ_WRITE</b> (读写)</td>
 * <td>配置热更、读多写少的缓存。</td>
 * <td>⭐⭐⭐⭐⭐</td>
 * </tr>
 * <tr>
 * <td><b>STAMPED</b> (印章)</td>
 * <td><b>超高频</b>的原子性查询（如 A* 算法查节点）。<br>⚠️ <b>不可重入</b>，严禁嵌套！</td>
 * <td>⭐⭐⭐⭐ (操作极其轻量，但 API 较复杂)</td>
 * </tr>
 * </table>
 *
 * <h3>3. 高并发安全声明</h3>
 * <ul>
 * <li><b>虚拟线程友好：</b> 所有阻塞操作均基于 J.U.C 实现，不会导致虚拟线程 Pinning (钉住) 问题。</li>
 * <li><b>防死锁设计：</b> 强制使用 try-finally 范式封装，杜绝因异常导致锁未释放的问题。</li>
 * <li><b>智能降级：</b> 当使用 REENTRANT 模式调用读方法时，自动降级为互斥锁；当使用非 STAMPED 模式调用乐观读时，自动降级为悲观读。</li>
 * </ul>
 *
 * @author qianye
 * @create 2025-12-26 10:43
 */
public abstract class RcsLock {

    // ========================================================================
    //                        静态工厂方法 (Static Factory)
    // ========================================================================

    /**
     * 创建【互斥锁】(默认非公平)
     * <p>
     * 最通用的锁，性能好，适用于绝大多数业务互斥场景。
     * </p>
     */
    public static RcsLock ofReentrant() {
        return new ReentrantImpl(false);
    }

    /**
     * 创建【互斥锁】(指定公平性)
     * <p>
     * 公平锁会严格按照请求顺序排队，防止线程饥饿，但吞吐量会明显下降。
     */
    public static RcsLock ofReentrant(boolean fair) {
        return new ReentrantImpl(fair);
    }

    /**
     * 创建【读写锁】(默认非公平)
     * <p>
     * 适用于读多写少（Read-Heavy）场景。允许多个读线程并行，但写线程独占。
     */
    public static RcsLock ofReadWrite() {
        // 读写锁通常也不建议用公平模式，吞吐量太低
        return new ReadWriteImpl(false);
    }

    /**
     * 创建【印章锁】(高性能，不可重入)
     * <p>
     * ⚠️ <b>致命警告：</b> 不可重入！如果在持有锁期间再次尝试获取锁，会导致死锁（Deadlock）。
     * 仅用于逻辑极度简单、无嵌套调用的高性能计算场景。
     * </p>
     */
    public static RcsLock ofStamped() {
        return new StampedImpl();
    }

    // ========================================================================
    //                        抽象接口定义 (API Contract)
    // ========================================================================

    /**
     * 执行写操作（独占/互斥）- 无返回值
     *
     * @param runnable 业务逻辑
     */
    public abstract void runInWrite(Runnable runnable);

    /**
     * 执行写操作（独占/互斥）- 带返回值
     *
     * @param supplier 业务逻辑
     * @param <T>      返回值类型
     * @return 业务结果
     */
    public abstract <T> T supplyInWrite(Supplier<T> supplier);

    /**
     * 尝试获取写锁并执行（带超时控制）
     * <p>
     * <b>防死锁利器：</b> 强制要求业务指定超时时间，防止因某个线程长期持有锁导致系统瘫痪。
     * </p>
     *
     * @param time     超时数值
     * @param unit     超时单位
     * @param runnable 获取锁成功后执行的逻辑
     * @return true=执行成功; false=获取锁超时，任务被丢弃
     */
    public abstract boolean tryRunInWrite(long time, TimeUnit unit, Runnable runnable);

    /**
     * 执行读操作
     * <ul>
     * <li>对于互斥锁：自动降级为互斥执行（串行）。</li>
     * <li>对于读写/印章锁：并行执行（共享）。</li>
     * </ul>
     */
    public abstract void runInRead(Runnable runnable);

    /**
     * 执行读操作 - 带返回值
     */
    public abstract <T> T supplyInRead(Supplier<T> supplier);

    /**
     * 乐观读 (Optimistic Read) - 极致性能
     * <p>
     * <b>原理：</b> 先假设没有锁冲突直接读数据，读完后校验版本号（Stamp）。
     * 如果校验失败（说明读期间有写入发生），则执行 fallback 逻辑（通常是升级为悲观读锁）。
     * </p>
     *
     * @param attempt  尝试进行的无锁读取逻辑（必须是纯内存操作，无副作用）
     * @param fallback 校验失败后的兜底逻辑（带锁读取）
     * @return 结果
     */
    public abstract <T> T optimisticRead(Supplier<T> attempt, Supplier<T> fallback);

    /**
     * 获取条件变量 (Condition)
     * <p>用于线程间的协调（wait/notify）。</p>
     *
     * @throws UnsupportedOperationException 如果锁实现不支持 Condition (如 StampedLock)
     */
    public abstract Condition newCondition();

    // ========================================================================
    //                    内部策略实现类 (Private Strategy Implementations)
    // ========================================================================

    /**
     * 策略 A: 互斥锁实现 (Based on ReentrantLock)
     */
    private static class ReentrantImpl extends RcsLock {
        // 仅持有一个 ReentrantLock 对象，内存最省
        private final ReentrantLock lock;

        ReentrantImpl(boolean fair) {
            this.lock = new ReentrantLock(fair);
        }

        @Override
        public void runInWrite(Runnable runnable) {
            lock.lock();
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public <T> T supplyInWrite(Supplier<T> supplier) {
            lock.lock();
            try {
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryRunInWrite(long time, TimeUnit unit, Runnable runnable) {
            try {
                // 尝试在指定时间内拿锁
                if (lock.tryLock(time, unit)) {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        lock.unlock();
                    }
                }
                return false;
            } catch (InterruptedException e) {
                // 响应中断是良好并发库的基本素养
                handleInterrupt();
                return false;
            }
        }

        @Override
        public void runInRead(Runnable runnable) {
            // ReentrantLock 不区分读写，读操作也必须互斥
            // 虽然牺牲了并发度，但保证了绝对的正确性
            runInWrite(runnable);
        }

        @Override
        public <T> T supplyInRead(Supplier<T> supplier) {
            return supplyInWrite(supplier);
        }

        @Override
        public <T> T optimisticRead(Supplier<T> attempt, Supplier<T> fallback) {
            // 普通互斥锁不支持乐观读机制，直接降级为悲观读（fallback）
            // 这种设计保证了代码在切换锁类型时的通用性，不会报错
            return supplyInRead(fallback);
        }

        @Override
        public Condition newCondition() {
            return lock.newCondition();
        }
    }

    /**
     * 策略 B: 读写锁实现 (Based on ReentrantReadWriteLock)
     */
    private static class ReadWriteImpl extends RcsLock {
        private final ReentrantReadWriteLock rwLock;

        ReadWriteImpl(boolean fair) {
            this.rwLock = new ReentrantReadWriteLock(fair);
        }

        @Override
        public void runInWrite(Runnable runnable) {
            // 写锁：独占
            rwLock.writeLock().lock();
            try {
                runnable.run();
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public <T> T supplyInWrite(Supplier<T> supplier) {
            rwLock.writeLock().lock();
            try {
                return supplier.get();
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public boolean tryRunInWrite(long time, TimeUnit unit, Runnable runnable) {
            try {
                if (rwLock.writeLock().tryLock(time, unit)) {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        rwLock.writeLock().unlock();
                    }
                }
                return false;
            } catch (InterruptedException e) {
                handleInterrupt();
                return false;
            }
        }

        @Override
        public void runInRead(Runnable runnable) {
            // 读锁：共享 (只要没有写锁，大家都可以进)
            rwLock.readLock().lock();
            try {
                runnable.run();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public <T> T supplyInRead(Supplier<T> supplier) {
            rwLock.readLock().lock();
            try {
                return supplier.get();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public <T> T optimisticRead(Supplier<T> attempt, Supplier<T> fallback) {
            // ReadWriteLock 也没有原生的乐观读 stamp 机制
            // 统一策略：降级为悲观读
            return supplyInRead(fallback);
        }

        @Override
        public Condition newCondition() {
            // 只有写锁支持 Condition
            return rwLock.writeLock().newCondition();
        }
    }

    /**
     * 策略 C: 印章锁实现 (Based on StampedLock) - 高性能
     */
    private static class StampedImpl extends RcsLock {
        // StampedLock 是 JDK 8 引入的，比 RWLock 快，但 API 极其反人类
        // 这里的封装屏蔽了 stamp 管理的复杂性
        private final StampedLock stampedLock;

        StampedImpl() {
            this.stampedLock = new StampedLock();
        }

        @Override
        public void runInWrite(Runnable runnable) {
            long stamp = stampedLock.writeLock();
            try {
                runnable.run();
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        }

        @Override
        public <T> T supplyInWrite(Supplier<T> supplier) {
            long stamp = stampedLock.writeLock();
            try {
                return supplier.get();
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        }

        @Override
        public boolean tryRunInWrite(long time, TimeUnit unit, Runnable runnable) {
            try {
                // StampedLock 的 tryLock 返回 stamp，失败返回 0
                long stamp = stampedLock.tryWriteLock(time, unit);
                if (stamp != 0) {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        stampedLock.unlockWrite(stamp);
                    }
                }
                return false;
            } catch (InterruptedException e) {
                handleInterrupt();
                return false;
            }
        }

        @Override
        public void runInRead(Runnable runnable) {
            // 悲观读锁
            long stamp = stampedLock.readLock();
            try {
                runnable.run();
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }

        @Override
        public <T> T supplyInRead(Supplier<T> supplier) {
            long stamp = stampedLock.readLock();
            try {
                return supplier.get();
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }

        @Override
        public <T> T optimisticRead(Supplier<T> attempt, Supplier<T> fallback) {
            // 1. 尝试乐观读 (无锁，仅获取一个版本号 stamp)
            long stamp = stampedLock.tryOptimisticRead();

            // 2. 执行内存读取 (必须快，且无副作用)
            T result = attempt.get();

            // 3. 校验 stamp (检查在步骤 1 和 2 之间，是否有写线程介入)
            if (stampedLock.validate(stamp)) {
                // 校验通过，数据是干净的，直接返回
                return result;
            }

            // 4. 校验失败 (说明读的过程中数据被改了)，降级为悲观读
            return supplyInRead(fallback);
        }

        @Override
        public Condition newCondition() {
            // StampedLock 的设计缺陷：它不支持 Condition
            throw new UnsupportedOperationException("模式错误：印章锁(StampedLock) 不支持 Condition 机制。请改用 ReentrantLock");
        }
    }

    // ========================================================================
    //                        私有辅助方法
    // ========================================================================

    /**
     * 统一的中断处理逻辑
     * <p>恢复中断状态并记录日志，避免异常被无声吞没。</p>
     */
    private static void handleInterrupt() {
        Thread.currentThread().interrupt();
        RcsLog.consoleLog.warn("RcsLock 获取锁操作被中断 (Interrupted)");
    }
}