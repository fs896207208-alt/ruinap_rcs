package com.ruinap.log;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.framework.test.SpringRunner;
import com.ruinap.infra.log.filter.DeduplicationFilter;
import com.ruinap.infra.thread.VthreadPool;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeduplicationFilter 单元测试
 * 核心策略：
 * 使用反射 (Reflection) 修改 private final 字段，
 * 从而在不破坏代码封装性的前提下，测试"500条阈值"和"60秒过期"的逻辑。
 *
 * @author qianye
 * @create 2025-12-05 18:44
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class DeduplicationFilterTest {

    @Autowired
    private VthreadPool vthreadPool;

    private DeduplicationFilter filter;

    public DeduplicationFilter getFilter() {
        if (filter == null) {
            filter = DeduplicationFilter.createFilter("test-appender");
            filter.start();
        }
        // 依然需要判空（因为 Log4j 可能比容器启动得早）
        if (this.filter == null) {
            throw new RuntimeException("DeduplicationFilter 未初始化成功");
        }
        return filter;
    }

    @Test
    public void testBasicDeduplication() {
        System.out.println("=== 测试用例 1: 基础去重功能 ===");
        LogEvent event = createEvent("AGV-001 Connection Lost");

        // 1. 发送第一条
        Filter.Result r1 = getFilter().filter(event);
        System.out.println("  -> 第1次发送结果: " + r1 + " (预期: NEUTRAL)");
        Assert.assertEquals(Filter.Result.NEUTRAL, r1);

        // 2. 发送第二条
        Filter.Result r2 = getFilter().filter(event);
        System.out.println("  -> 第2次发送结果: " + r2 + " (预期: DENY)");
        Assert.assertEquals(Filter.Result.DENY, r2);

        System.out.println("=== 测试用例 1 通过 ===");
    }

    @Test
    public void testDistinctMessages() {
        System.out.println("=== 测试用例 2: 不同内容互不影响 ===");

        Filter.Result r1 = getFilter().filter(createEvent("Msg A"));
        System.out.println("  -> Msg A 结果: " + r1);
        Assert.assertEquals(Filter.Result.NEUTRAL, r1);

        Filter.Result r2 = getFilter().filter(createEvent("Msg B"));
        System.out.println("  -> Msg B 结果: " + r2);
        Assert.assertEquals(Filter.Result.NEUTRAL, r2);

        System.out.println("=== 测试用例 2 通过 ===");
    }

    @Test
    public void testExpirationLogic() throws Exception {
        System.out.println("=== 测试用例 3: 过期机制验证 ===");
        LogEvent event = createEvent("Timeout Warning");

        getFilter().filter(event);
        System.out.println("  -> 首次发送完毕，已记录缓存");

        // 反射修改时间
        Field cacheField = DeduplicationFilter.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ConcurrentHashMap<Integer, Long> cache = (ConcurrentHashMap<Integer, Long>) cacheField.get(getFilter());

        System.out.println("  -> [黑客操作] 使用反射将缓存时间戳篡改为 1秒前...");
        for (Integer key : cache.keySet()) {
            cache.put(key, System.currentTimeMillis() - 1000L);
        }

        Filter.Result result = getFilter().filter(event);
        System.out.println("  -> 再次发送结果: " + result + " (预期: NEUTRAL, 因为已过期)");
        Assert.assertEquals(Filter.Result.NEUTRAL, result);

        System.out.println("=== 测试用例 3 通过 ===");
    }

    @Test
    public void testAsyncCleanup() throws Exception {
        System.out.println("=== 测试用例 4: 异步清理逻辑 (高并发核心) ===");

        DeduplicationFilter filter = getFilter();
        Field cacheField = DeduplicationFilter.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ConcurrentHashMap<Integer, Long> cache = (ConcurrentHashMap<Integer, Long>) cacheField.get(filter);

        Field counterField = DeduplicationFilter.class.getDeclaredField("counter");
        counterField.setAccessible(true);
        java.util.concurrent.atomic.LongAdder counter = (java.util.concurrent.atomic.LongAdder) counterField.get(filter);
        counter.add(500);

        // 2. 准备大量脏数据
        int limit = 510;
        // 过期数据：设置为 100秒前过期
        long expiredTime = System.currentTimeMillis() - 100_000L;

        System.out.println("  -> [准备数据] 正在插入 " + limit + " 条过期数据...");
        for (int i = 0; i < limit; i++) {
            cache.put(i, expiredTime);
        }

        // 标记两个特殊的 Key 用于验证
        cache.put(88888, expiredTime); // 肯定会被删的

        // 【核心修复】新鲜数据：过期时间应该是 "未来"！
        // 比如：当前时间 + 60秒 (模拟正常逻辑)
        long freshTime = System.currentTimeMillis() + 60_000L;
        cache.put(99999, freshTime);

        System.out.println("  -> 当前 Cache 大小: " + cache.size());

        // 3. 触发清理
        System.out.println("  -> 发送触发日志 (Trigger Cleanup)...");
        filter.filter(createEvent("Trigger Log Cleanup"));

        // 4. 等待异步执行
        System.out.println("  -> [等待] 睡眠 1秒 等待虚拟线程执行...");
        Thread.sleep(1000);

        // 5. 验证
        boolean hasOldKey = cache.containsKey(88888);
        boolean hasNewKey = cache.containsKey(99999);
        int currentSize = cache.size();

        System.out.println("  -> [验证] 过期 Key (88888) 存在? " + hasOldKey);
        System.out.println("  -> [验证] 新鲜 Key (99999) 存在? " + hasNewKey);
        System.out.println("  -> [验证] 清理后剩余缓存数: " + currentSize);

        Assert.assertFalse("过期数据 (88888) 应该被删除", hasOldKey);
        Assert.assertTrue("新鲜数据 (99999) 应该保留", hasNewKey);
        // 剩余数量应该是 2 个：99999 和 刚刚那条 Trigger Log
        Assert.assertTrue("缓存应该被大量清理", currentSize < 10);

        System.out.println("=== 测试用例 4 通过 ===");
    }

    @Test
    public void testAppenderIsolation() {
        System.out.println("=== 测试用例 5: Appender 隔离性 ===");
        DeduplicationFilter consoleFilter = DeduplicationFilter.createFilter("console");
        DeduplicationFilter fileFilter = DeduplicationFilter.createFilter("file");

        LogEvent event = createEvent("System Boot");

        // 两个过滤器应该分别处理，互不干扰
        Assert.assertEquals(Filter.Result.NEUTRAL, consoleFilter.filter(event));
        Assert.assertEquals(Filter.Result.NEUTRAL, fileFilter.filter(event));
        System.out.println("=== 测试用例 5 通过 ===");
    }

    private LogEvent createEvent(String msg) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("com.ruinap.test")
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage(msg))
                .setTimeMillis(System.currentTimeMillis())
                .build();
    }
}