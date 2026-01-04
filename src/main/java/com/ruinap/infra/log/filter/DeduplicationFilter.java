package com.ruinap.infra.log.filter;

import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.thread.VthreadPool;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;


/**
 * 自定义 Log4j2 日志去重过滤器。
 * <p>
 * 该过滤器通过检查当前日志消息是否与上一条日志消息相同（使用 hashCode 哈希编码进行比较），
 * 从而过滤掉重复的日志，避免过多的重复日志输出。
 * </p>
 * <p>
 * 通过 {@code @Plugin} 注解注册为 Log4j2 插件，集成到日志系统中。
 * </p>
 *
 * @author qianye
 * @create 2025-01-11 18:56
 */
@Plugin(name = "DeduplicationFilter", category = "Core", elementType = Filter.ELEMENT_TYPE)
public class DeduplicationFilter extends AbstractFilter {

    /**
     * Log4j 插件不能使用 @Autowired，因为它们不归 Spring 管理
     */
    private volatile VthreadPool vthreadPool;

    /**
     * 核心存储：Key=日志Hash, Value=过期时间戳
     * 使用 CHM 实现高并发下的无锁读
     */
    private final ConcurrentHashMap<Integer, Long> cache = new ConcurrentHashMap<>();

    /**
     * 缓存的 AppenderId
     */
    private final String appenderId;

    /**
     * 使用 LongAdder 替代 AtomicInteger，在高并发下性能更好
     */
    private final LongAdder counter = new LongAdder();

    /**
     * 标记位：使用 AtomicBoolean 实现 CAS 锁，防止任务队列堆积
     */
    private final AtomicBoolean isCleaning = new AtomicBoolean(false);

    /**
     * 默认窗口60秒
     */
    private final long DEFAULT_WINDOW_MS;

    /**
     * 清理阈值：每积累 500 条日志触发一次异步清理检查
     * 适当调大以减少任务提交频率
     */
    private final int CLEANUP_THRESHOLD;

    private DeduplicationFilter(Result onMatch, Result onMismatch, String appenderId) {
        super(onMatch, onMismatch);
        this.appenderId = appenderId;
        DEFAULT_WINDOW_MS = 60000L;
        CLEANUP_THRESHOLD = 500;
    }

    /**
     * VthreadPool懒加载 Getter 方法
     */
    private VthreadPool getVthreadPool() {
        if (this.vthreadPool == null) {
            this.vthreadPool = SpringContextHolder.getBean(VthreadPool.class);
        }
        return this.vthreadPool;
    }

    @Override
    public Result filter(LogEvent event) {
        // 1. 【性能优化】自定义哈希计算，避免 Objects.hash() 的 varargs 数组分配
        // 逻辑：(appenderId * 31 + loggerName) * 31 + msg
        int h = 1;
        h = 31 * h + (appenderId != null ? appenderId.hashCode() : 0);
        String loggerName = event.getLoggerName();
        h = 31 * h + (loggerName != null ? loggerName.hashCode() : 0);
        String msg = event.getMessage().getFormattedMessage();
        h = 31 * h + (msg != null ? msg.hashCode() : 0);

        long now = System.currentTimeMillis();
        Long expiryTime = cache.get(h);

        // 触发清理检查 (异步)
        checkAndCleanAsync(now);

        // 【优化 1】业务逻辑修正：固定窗口模式 (Fixed Window)
        // 只有当 key 不存在，或者 key 已过期时，才更新时间并放行。
        // 如果在有效期内被拦截，绝不延长过期时间。
        if (expiryTime == null || now >= expiryTime) {
            // 放行，并设定下一次过期时间
            cache.put(h, now + DEFAULT_WINDOW_MS);
            return Result.NEUTRAL;
        } else {
            // 拦截，且不更新时间（保证持续报错时，每60秒能漏出来一条）
            return Result.DENY;
        }
    }

    /**
     * 异步清理逻辑
     * 将 O(N) 的遍历清除操作转移到虚拟线程中，保证 filter 方法始终是 O(1)
     */
    private void checkAndCleanAsync(long now) {
        counter.increment();

        // 使用 sumThenReset 原子操作，防止计数丢失
        if (counter.sum() >= CLEANUP_THRESHOLD) {

            // CAS 锁：只有抢到锁的线程才有资格提交任务
            // 如果 isCleaning 已经是 true，compareAndSet 返回 false，直接跳过，连任务都不生成
            if (isCleaning.compareAndSet(false, true)) {

                // 既然拿到了锁，就重置计数器 (放在这里更合理，避免频繁重置)
                counter.sumThenReset();

                VthreadPool pool = getVthreadPool();
                if (pool == null) {
                    // 必须释放锁
                    isCleaning.set(false);
                    return;
                }

                try {
                    pool.execute(() -> {
                        try {
                            cache.entrySet().removeIf(entry -> entry.getValue() < now);
                        } catch (Exception e) {
                            // ignore
                        } finally {
                            // 任务结束，释放锁
                            isCleaning.set(false);
                        }
                    });
                } catch (Exception e) {
                    // 提交失败，释放锁
                    isCleaning.set(false);
                }
            }
        }
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        cache.clear();
        return super.stop(timeout, timeUnit);
    }

    @PluginFactory
    public static DeduplicationFilter createFilter(@PluginAttribute("appenderId") String appenderId) {
        if (appenderId == null) {
            appenderId = "default";
        }
        return new DeduplicationFilter(Result.NEUTRAL, Result.DENY, appenderId);
    }
}