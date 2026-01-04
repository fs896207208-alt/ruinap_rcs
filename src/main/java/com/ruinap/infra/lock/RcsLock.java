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
 * 开发者只需通过构造参数选择锁的类型，即可获得一套统一的、安全的、支持智能降级的并发控制接口。
 * </p>
 *
 * <h3>2. 锁类型详解 (Type)</h3>
 * <table border="1">
 * <tr>
 * <th>类型</th>
 * <th>底层实现</th>
 * <th>可重入性</th>
 * <th>读写分离</th>
 * <th>适用场景</th>
 * </tr>
 * <tr>
 * <td><b>REENTRANT</b></td>
 * <td>ReentrantLock</td>
 * <td>✅ 是</td>
 * <td>❌ 否 (读也是互斥)</td>
 * <td><b>强一致性/互斥场景</b><br>如：交通管制、任务状态流转、防止 AGV 撞车。</td>
 * </tr>
 * <tr>
 * <td><b>READ_WRITE</b></td>
 * <td>ReentrantReadWriteLock</td>
 * <td>✅ 是</td>
 * <td>✅ 是</td>
 * <td><b>读多写少且逻辑复杂</b><br>如：全局配置管理、非热点数据缓存。</td>
 * </tr>
 * <tr>
 * <td><b>STAMPED</b></td>
 * <td>StampedLock</td>
 * <td>❌ <b>否 (死锁风险)</b></td>
 * <td>✅ 是 (支持乐观读)</td>
 * <td><b>超高频热点计算</b><br>如：A* 算法查地图、实时坐标查询。(极致性能，严禁嵌套调用)</td>
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
public class RcsLock {

    /**
     * 锁类型枚举
     */
    public enum Type {
        /**
         * 互斥锁 (基于 ReentrantLock)
         * 特点：可重入、支持 Condition、无读写分离（读也是互斥的）。
         * 适用：交通管制、任务状态变更。
         */
        REENTRANT,

        /**
         * 读写锁 (基于 ReentrantReadWriteLock)
         * 特点：可重入、读读并行、写独占。
         * 适用：配置读取、缓存读取。
         */
        READ_WRITE,

        /**
         * 印章锁 (基于 StampedLock)
         * 特点：<b>不可重入</b>、性能极致、支持乐观读。
         * 适用：超高频的地图/节点查询。
         */
        STAMPED
    }

    private final Type type;

    // 三选一：运行时只有其中一个被初始化，其余为 null (节省内存)
    private final ReentrantLock reentrantLock;
    private final ReentrantReadWriteLock rwLock;
    private final StampedLock stampedLock;

    // ================= 初始化与工厂方法 =================

    /**
     * 创建【互斥锁】(默认非公平)
     * <p>适合绝大多数互斥场景，性能优于公平锁。</p>
     */
    public static RcsLock ofReentrant() {
        return new RcsLock(Type.REENTRANT, false);
    }

    /**
     * 创建【互斥锁】(指定公平性)
     * <p>公平锁能保证线程按请求顺序获取锁，防止饥饿，但吞吐量较低。</p>
     *
     * @param fair true=公平锁, false=非公平锁
     */
    public static RcsLock ofReentrant(boolean fair) {
        return new RcsLock(Type.REENTRANT, fair);
    }

    /**
     * 创建【读写锁】(默认非公平)
     * <p>适合读多写少，且业务逻辑中可能存在嵌套调用（重入）的场景。</p>
     */
    public static RcsLock ofReadWrite() {
        return new RcsLock(Type.READ_WRITE, false);
    }

    /**
     * 创建【印章锁】(高性能，不可重入)
     * <p>
     * ⚠️ <b>警告：</b> 该模式不可重入！
     * 如果在持有锁的代码块中再次调用该锁的方法，将直接导致<b>死锁</b>。
     * 仅用于逻辑简单、平铺直叙的高频计算场景。
     * </p>
     */
    public static RcsLock ofStamped() {
        return new RcsLock(Type.STAMPED, false);
    }

    /**
     * 私有构造函数
     */
    private RcsLock(Type type, boolean fair) {
        this.type = type;
        switch (type) {
            case REENTRANT:
                this.reentrantLock = new ReentrantLock(fair);
                this.rwLock = null;
                this.stampedLock = null;
                break;
            case READ_WRITE:
                this.rwLock = new ReentrantReadWriteLock(fair);
                this.reentrantLock = null;
                this.stampedLock = null;
                break;
            case STAMPED:
                this.stampedLock = new StampedLock();
                this.reentrantLock = null;
                this.rwLock = null;
                break;
            default:
                throw new IllegalArgumentException("未知的锁类型");
        }
    }

    // ========================================================================
    //                            写操作 (Write / Exclusive)
    // ========================================================================

    /**
     * 执行写操作（独占/互斥）
     * <p>
     * <b>特性说明：</b>
     * <ul>
     * <li>REENTRANT: 普通互斥锁。</li>
     * <li>READ_WRITE: 写锁（独占），会阻塞所有读锁。</li>
     * <li>STAMPED: 写锁（独占），<b>不可重入</b>。</li>
     * </ul>
     * </p>
     *
     * @param runnable 需要执行的业务逻辑（无返回值）
     */
    public void runInWrite(Runnable runnable) {
        if (type == Type.STAMPED) {
            long stamp = stampedLock.writeLock();
            try {
                runnable.run();
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        } else if (type == Type.READ_WRITE) {
            rwLock.writeLock().lock();
            try {
                runnable.run();
            } finally {
                rwLock.writeLock().unlock();
            }
        } else {
            reentrantLock.lock();
            try {
                runnable.run();
            } finally {
                reentrantLock.unlock();
            }
        }
    }

    /**
     * 执行写操作并返回结果
     *
     * @param supplier 需要执行的业务逻辑（有返回值）
     * @param <T>      返回值类型
     * @return 业务执行结果
     */
    public <T> T supplyInWrite(Supplier<T> supplier) {
        if (type == Type.STAMPED) {
            long stamp = stampedLock.writeLock();
            try {
                return supplier.get();
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        } else if (type == Type.READ_WRITE) {
            rwLock.writeLock().lock();
            try {
                return supplier.get();
            } finally {
                rwLock.writeLock().unlock();
            }
        } else {
            reentrantLock.lock();
            try {
                return supplier.get();
            } finally {
                reentrantLock.unlock();
            }
        }
    }

    /**
     * 尝试获取写锁并执行（带超时）
     * <p>
     * <b>高并发核心方法：</b>
     * 用于防止死锁。如果指定时间内无法获取锁，则直接放弃执行，返回 false。
     * 此方法兼容了 StampedLock 的 tryWriteLock 特性。
     * </p>
     *
     * @param time     超时时间数值
     * @param unit     超时时间单位
     * @param runnable 获取锁成功后执行的任务
     * @return true=获取锁成功并执行完毕; false=获取锁超时，任务未执行
     */
    public boolean tryRunInWrite(long time, TimeUnit unit, Runnable runnable) {
        try {
            if (type == Type.STAMPED) {
                // StampedLock 的 tryWriteLock 返回 stamp，失败返回 0
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
            } else if (type == Type.READ_WRITE) {
                if (rwLock.writeLock().tryLock(time, unit)) {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        rwLock.writeLock().unlock();
                    }
                }
                return false;
            } else {
                if (reentrantLock.tryLock(time, unit)) {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        reentrantLock.unlock();
                    }
                }
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RcsLog.consoleLog.warn("RcsLock tryRunInWrite 被中断");
            return false;
        }
    }

    // ========================================================================
    //                            读操作 (Read / Shared)
    // ========================================================================

    /**
     * 执行读操作
     * <p>
     * <b>特性说明：</b>
     * <ul>
     * <li>REENTRANT: <b>自动降级</b>为互斥锁。注意：此时读操作也是串行的！</li>
     * <li>READ_WRITE: 共享读锁，多线程并行。</li>
     * <li>STAMPED: 悲观读锁，共享，不可重入。</li>
     * </ul>
     * </p>
     *
     * @param runnable 读操作逻辑
     */
    public void runInRead(Runnable runnable) {
        if (type == Type.STAMPED) {
            long stamp = stampedLock.readLock();
            try {
                runnable.run();
            } finally {
                stampedLock.unlockRead(stamp);
            }
        } else if (type == Type.READ_WRITE) {
            rwLock.readLock().lock();
            try {
                runnable.run();
            } finally {
                rwLock.readLock().unlock();
            }
        } else {
            // ReentrantLock 没有读写分离，只能加互斥锁
            reentrantLock.lock();
            try {
                runnable.run();
            } finally {
                reentrantLock.unlock();
            }
        }
    }

    /**
     * 执行读操作并返回值
     */
    public <T> T supplyInRead(Supplier<T> supplier) {
        if (type == Type.STAMPED) {
            long stamp = stampedLock.readLock();
            try {
                return supplier.get();
            } finally {
                stampedLock.unlockRead(stamp);
            }
        } else if (type == Type.READ_WRITE) {
            rwLock.readLock().lock();
            try {
                return supplier.get();
            } finally {
                rwLock.readLock().unlock();
            }
        } else {
            reentrantLock.lock();
            try {
                return supplier.get();
            } finally {
                reentrantLock.unlock();
            }
        }
    }

    // ========================================================================
    //                            乐观读 (Optimistic Read)
    // ========================================================================

    /**
     * 尝试乐观读 (极速无锁查询)
     * <p>
     * <b>高性能核心：</b>
     * 这是 A* 算法等高频查询场景的“核武器”。它不加任何锁，只在读完后校验数据版本。
     * </p>
     *
     * <h3>降级策略：</h3>
     * <ul>
     * <li>如果类型是 <b>STAMPED</b>: 执行真正的乐观读。如果发现数据被修改（校验失败），自动调用 fallback 降级为悲观读。</li>
     * <li>如果类型是 <b>其他</b>: 直接忽略 attempt 逻辑，强制执行 fallback（即悲观读锁）。这保证了代码的通用性。</li>
     * </ul>
     *
     * @param attempt  尝试进行的无锁读取逻辑（必须是纯内存操作，无副作用）
     * @param fallback 如果乐观读失败，使用的兜底逻辑（带锁读取）
     * @return 查询结果
     */
    public <T> T optimisticRead(Supplier<T> attempt, Supplier<T> fallback) {
        // 只有 StampedLock 真正支持乐观读
        if (type == Type.STAMPED) {
            long stamp = stampedLock.tryOptimisticRead();
            T result = attempt.get();
            // 关键：校验 stamp 是否有效 (在此期间有没有写操作发生)
            if (stampedLock.validate(stamp)) {
                return result;
            }
        }
        // 验证失败，或者锁类型不支持，统一降级为“普通读锁”
        return supplyInRead(fallback);
    }

    // ========================================================================
    //                            高级特性 (Condition / TryLock)
    // ========================================================================

    /**
     * 获取条件变量 (用于 wait/notify)
     * <p>
     * <b>适用场景：</b> 任务队列等待、交通路口释放通知。
     * </p>
     * <p>
     * ⚠️ <b>注意：</b> STAMPED 模式不支持 Condition！调用此方法会抛出异常。
     * 如果需要 Condition 功能，请务必使用 REENTRANT 模式。
     * </p>
     *
     * @return Condition 对象
     * @throws UnsupportedOperationException 如果当前锁类型不支持 Condition
     */
    public Condition newCondition() {
        if (type == Type.REENTRANT) {
            return reentrantLock.newCondition();
        } else if (type == Type.READ_WRITE) {
            return rwLock.writeLock().newCondition();
        } else {
            throw new UnsupportedOperationException("模式错误：印章锁(StampedLock) 不支持 Condition。");
        }
    }
}
