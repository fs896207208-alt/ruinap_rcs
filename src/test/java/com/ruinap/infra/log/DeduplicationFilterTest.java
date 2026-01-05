package com.ruinap.infra.log;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.log.filter.DeduplicationFilter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
@SpringBootTest // 组合注解，已包含 JUnit 6 扩展
@DisplayName("日志去重过滤器测试")
class DeduplicationFilterTest {

    @Autowired
    private DeduplicationFilter deduplicationFilter; // 如果容器中没有此 Bean，可能需要手动 new，视配置而定

    /**
     * 辅助方法：反射修改 Filter 内部的配置参数，以便于测试
     */
    private void mockFilterConfig(DeduplicationFilter filter, int maxSize, long expireTime) throws Exception {
        // 修改 MAX_CACHE_SIZE
        Field sizeField = DeduplicationFilter.class.getDeclaredField("MAX_CACHE_SIZE");
        sizeField.setAccessible(true);
        sizeField.set(filter, maxSize);

        // 修改 EXPIRE_TIME_MS
        Field expireField = DeduplicationFilter.class.getDeclaredField("EXPIRE_TIME_MS");
        expireField.setAccessible(true);
        expireField.set(filter, expireTime);
    }

    /**
     * 辅助方法：反射获取缓存 Map
     */
    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Integer, Long> getCacheMap(DeduplicationFilter filter) throws Exception {
        Field cacheField = DeduplicationFilter.class.getDeclaredField("deduplicationCache");
        cacheField.setAccessible(true);
        return (ConcurrentHashMap<Integer, Long>) cacheField.get(filter);
    }

    private LogEvent createEvent(String msg) {
        return Log4jLogEvent.newBuilder()
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage(msg))
                .build();
    }

    @Test
    @DisplayName("测试：常规去重逻辑 (重复消息被 DENY)")
    void testNormalDeduplication() {
        System.out.println("=== 测试用例 1: 常规去重 ===");
        DeduplicationFilter filter = DeduplicationFilter.createFilter("console");

        LogEvent event1 = createEvent("Error: Connection timeout");
        LogEvent event2 = createEvent("Error: Connection timeout"); // 重复
        LogEvent event3 = createEvent("Error: Database down");      // 不同

        // 第一次：放行
        Assertions.assertEquals(Filter.Result.NEUTRAL, filter.filter(event1));

        // 第二次（重复）：拒绝
        Assertions.assertEquals(Filter.Result.DENY, filter.filter(event2), "重复日志应该被拒绝");

        // 第三次（新内容）：放行
        Assertions.assertEquals(Filter.Result.NEUTRAL, filter.filter(event3));

        System.out.println("=== 测试用例 1 通过 ===");
    }

    @Test
    @DisplayName("测试：缓存过期清理 (Expire Eviction)")
    void testExpiration() throws Exception {
        System.out.println("=== 测试用例 2: 过期清理 ===");
        DeduplicationFilter filter = DeduplicationFilter.createFilter("console");

        // 将过期时间改为 100ms
        mockFilterConfig(filter, 1000, 100L);

        LogEvent event = createEvent("Short lived error");

        // 第一次：放行
        filter.filter(event);

        // 立即再次：拒绝
        Assertions.assertEquals(Filter.Result.DENY, filter.filter(event));

        // 等待 200ms 让缓存过期
        Thread.sleep(200);

        // 过期后：应该再次放行
        Assertions.assertEquals(Filter.Result.NEUTRAL, filter.filter(event), "过期后日志应该重新被放行");

        System.out.println("=== 测试用例 2 通过 ===");
    }

    @Test
    @DisplayName("测试：缓存容量溢出清理 (Capacity Eviction)")
    void testCapacityEviction() throws Exception {
        System.out.println("=== 测试用例 4: 容量溢出清理 ===");
        DeduplicationFilter filter = DeduplicationFilter.createFilter("console");

        // 设置极小的容量：5个
        mockFilterConfig(filter, 5, 60000L);
        ConcurrentHashMap<Integer, Long> cache = getCacheMap(filter);

        // 1. 填满缓存 (5个)
        // 模拟手动放入过期数据和新鲜数据
        long now = System.currentTimeMillis();
        cache.put(11111, now);
        cache.put(22222, now);
        cache.put(88888, now - 100000); // 这是一个很久以前的 Key (过期)
        cache.put(99999, now);          // 这是一个刚刚放入的 Key (新鲜)
        cache.put(33333, now);

        System.out.println("  -> 当前缓存大小: " + cache.size()); // 应该是 5

        // 2. 触发清理
        // 再来一条日志，导致 size > 5，触发 cleanup
        LogEvent triggerEvent = createEvent("Trigger Overflow Log");
        filter.filter(triggerEvent);

        // 3. 验证
        boolean hasOldKey = cache.containsKey(88888);
        boolean hasNewKey = cache.containsKey(99999);
        int currentSize = cache.size();

        System.out.println("  -> [验证] 过期 Key (88888) 存在? " + hasOldKey);
        System.out.println("  -> [验证] 新鲜 Key (99999) 存在? " + hasNewKey);
        System.out.println("  -> [验证] 清理后剩余缓存数: " + currentSize);

        // JUnit 6: Assertions.assertFalse(condition, message) 注意参数顺序！
        Assertions.assertFalse(hasOldKey, "过期数据 (88888) 应该被删除");
        Assertions.assertTrue(hasNewKey, "新鲜数据 (99999) 应该保留");

        // 剩余数量应该是 2 个：99999 和 刚刚那条 Trigger Log (实际可能还有其他的，只要小于阈值即可)
        Assertions.assertTrue(currentSize < 5, "缓存应该被清理到阈值以下");

        System.out.println("=== 测试用例 4 通过 ===");
    }

    @Test
    @DisplayName("测试：Appender 隔离性")
    void testAppenderIsolation() {
        System.out.println("=== 测试用例 5: Appender 隔离性 ===");
        DeduplicationFilter consoleFilter = DeduplicationFilter.createFilter("console");
        DeduplicationFilter fileFilter = DeduplicationFilter.createFilter("file");

        LogEvent event = createEvent("System Boot");

        // 两个过滤器应该分别处理，互不干扰
        Assertions.assertEquals(Filter.Result.NEUTRAL, consoleFilter.filter(event));
        Assertions.assertEquals(Filter.Result.NEUTRAL, fileFilter.filter(event));

        // console 再次过滤 -> DENY
        Assertions.assertEquals(Filter.Result.DENY, consoleFilter.filter(event));

        // file 依然应该受自己控制 (如果它之前没过滤过第二次，这里假设逻辑是隔离的)
        // 注意：LogEvent 的 hashCode 是一样的，但是不同的 Filter 实例拥有不同的 Map。
        // 上面 fileFilter.filter(event) 已经记录了一次，所以这里再次调用应该是 DENY。
        Assertions.assertEquals(Filter.Result.DENY, fileFilter.filter(event), "File Filter 应该独立记录状态");

        System.out.println("=== 测试用例 5 通过 ===");
    }
}