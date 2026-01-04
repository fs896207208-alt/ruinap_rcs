package com.ruinap.infra.thread;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.log.RcsLog;

import java.util.concurrent.*;

/**
 * 全局线程池【计算密集型专用线程池】(CPU-Bound Executor)
 * <p>
 * <h3>核心设计理念：</h3>
 * 本线程池使用传统的<b>平台线程 (Platform Threads)</b>，专门用于隔离和执行 CPU 密集型任务。
 * 它的存在是为了弥补虚拟线程 (Virtual Threads) 在处理长时间计算任务时的短板。
 * </p>
 *
 * <h3>为什么需要这个线程池？</h3>
 * <ul>
 * <li><b>防止调度死锁：</b> 虚拟线程是协作式调度的。如果一个虚拟线程执行长时间的 CPU 运算（如 A* 寻路），
 * 它会长期占用底层的 Carrier Thread（载体线程），导致同一载体上的其他 I/O 虚拟线程无法被调度，
 * 严重时会导致系统 I/O 吞吐量暴跌甚至看似假死。</li>
 *
 * <li><b>利用抢占式调度：</b> 平台线程受操作系统内核管理，使用抢占式调度（Time Slicing）。
 * 即使 A* 算法陷入死循环，操作系统也会强制暂停它，分配 CPU 时间片给其他线程（如心跳、急停信号），
 * 从而保证系统的响应性。</li>
 * </ul>
 *
 * <h3>适用场景 (CPU密集型)：</h3>
 * <ul>
 * <li>✅ 路径规划算法 (A*, Dijkstra, TimeWindow)</li>
 * <li>✅ 交通管制计算 (死锁检测, 环路检测)</li>
 * <li>✅ 复杂的几何运算 (贝塞尔曲线, 坐标系转换)</li>
 * <li>✅ 大数据量的序列化/反序列化</li>
 * </ul>
 *
 * <h3>严禁场景 (I/O密集型)：</h3>
 * <ul>
 * <li>❌ 数据库查询 (请用 VthreadPool)</li>
 * <li>❌ HTTP/Netty 请求 (请用 VthreadPool)</li>
 * <li>❌ 文件读写 (请用 VthreadPool)</li>
 * <li>❌ Thread.sleep (请用 VthreadPool)</li>
 * </ul>
 *
 * @author qianye
 * @create 2024-04-07 14:30
 */
@Component
public class ThreadPool {

    /**
     * 核心执行器
     */
    private ExecutorService executor;
    @Autowired
    private CoreYaml coreYaml;

    /**
     * 初始化线程池
     * <p>
     * 参数配置说明：
     * 1. <b>核心线程数 = 最大线程数 = CPU核数 + 1</b>：
     * 这是计算密集型任务的最佳实践。保持 CPU 始终 100% 满载运行，同时最大限度减少线程上下文切换的开销。
     * (注意：千万不要设置几百个线程，那会让 A* 变慢！)
     * <p>
     * 2. <b>有界队列 (LinkedBlockingQueue)</b>：
     * 防止任务无限堆积耗尽内存。如果队列满了，说明 CPU 算不过来了。
     * <p>
     * 3. <b>拒绝策略 (CallerRunsPolicy)</b>：
     * "反压机制"。如果队列满了，让提交任务的 IO 线程自己去算。这会变相减慢 IO 接收速度，保护系统不崩溃。
     * <p>
     * 4. <b>守护线程 (Daemon)</b>：
     * 确保 JVM 关闭时，计算任务能被直接中断，不会导致进程无法退出。
     */
    @PostConstruct
    public void init() {
        // 1. 从配置文件读取参数
        int corePoolSize = coreYaml.getAlgoCorePoolSize();
        int maxPoolSize = coreYaml.getAlgoMaxPoolSize();
        int queueCapacity = coreYaml.getAlgoQueueCapacity();
        int keepAliveSeconds = coreYaml.getAlgoKeepAliveSeconds();

        // 2. 构建线程池 (使用 Hutool)
        this.executor = ExecutorBuilder.create()
                // 设置核心线程数
                .setCorePoolSize(corePoolSize)
                // 设置最大线程数
                .setMaxPoolSize(maxPoolSize)
                // 设置空闲线程的存活时间
                .setKeepAliveTime(keepAliveSeconds, TimeUnit.SECONDS)
                // 设置任务队列
                .setWorkQueue(new LinkedBlockingQueue<>(queueCapacity))
                // 设置拒绝策略 当任务队列满时，将任务退回给调用者线程执行
                .setHandler((r, executor) -> {
                    RcsLog.consoleLog.warn(RcsLog.getTemplate(2), RcsLog.randomInt(), "⚠️ 警告：ThreadPool线程池已满，正在降级为由调用线程执行计算，可能影响IO响应！");
                    if (!executor.isShutdown()) {
                        r.run();
                    }
                })
                //设置自定义线程工厂 自定义线程名称，并设置为守护线程
                .setThreadFactory(new ThreadFactoryBuilder().setNamePrefix("rt-").setDaemon(true).build())
                .build();

        // 3. 打印日志
        RcsLog.consoleLog.info("ThreadPool 初始化完成 | 核心数:{} | 最大数:{} | 队列:{}", corePoolSize, maxPoolSize, queueCapacity);
    }

    /**
     * 提交无返回值的计算任务
     *
     * @param task 计算任务 (Runnable)
     */
    public void execute(Runnable task) {
        if (executor == null) {
            init();
        }
        executor.execute(task);
    }

    /**
     * 提交有返回值的计算任务 (例如获取路径规划结果)
     *
     * @param task 计算任务 (Callable)
     * @param <T>  返回值类型
     * @return Future 对象，用于获取计算结果
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (executor == null) {
            init();
        }
        return executor.submit(task);
    }

    /**
     * 优雅停机
     * <p>
     * 采用双阶段关闭模式：
     * 1. shutdown(): 停止接收新任务，等待队列中的任务算完。
     * 2. awaitTermination(): 给 5 秒缓冲时间。
     * 3. shutdownNow(): 如果 5 秒还没算完，强制中断。
     */
    @PreDestroy
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            // 第一阶段：不再接收新任务，但继续处理队列中已有的任务
            executor.shutdown();
            try {
                // 第二阶段：等待现有任务执行完成（最多等 5 秒）
                // 对于 A* 算法，5秒足够算完剩下的路径了
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    // 第三阶段：等太久了（超过5秒），强制中断正在运行的任务
                    executor.shutdownNow();
                    // 第四阶段：再次等待（给由于中断而退出的线程一点时间善后）
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        RcsLog.consoleLog.error("ThreadPool 线程池未能彻底关闭");
                    }
                }
            } catch (InterruptedException e) {
                // 如果当前线程在等待过程中被中断，也尝试强制关闭线程池
                executor.shutdownNow();
                // 保持中断状态
                Thread.currentThread().interrupt();
            }
            RcsLog.consoleLog.warn("ThreadPool 资源释放完毕");
        }
    }
}
