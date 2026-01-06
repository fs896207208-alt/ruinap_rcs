package com.ruinap.infra.log;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.log.filter.DeduplicationFilter;
import com.ruinap.infra.thread.VthreadPool;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeduplicationFilter 单元测试
 * 核心策略：
 * 使用反射 (Reflection) 修改 private final 字段，
 * 从而在不破坏代码封装性的前提下，测试"500条阈值"和"60秒过期"的逻辑。
 *
 * @author qianye
 * @create 2025-12-05 18:44
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeduplicationFilterTest {

    private static DeduplicationFilter filter;
    private static VthreadPool mockPool;
    private static ApplicationContext mockContext;

    @BeforeAll
    static void initGlobal() {
        System.out.println("██████████ [START] 启动 DeduplicationFilter 测试 ██████████");

        // 1. Mock SpringContextHolder 和 VthreadPool
        mockContext = mock(ApplicationContext.class);
        mockPool = mock(VthreadPool.class);

        when(mockContext.getBean(VthreadPool.class)).thenReturn(mockPool);

        // 注入上下文
        ReflectUtil.setFieldValue(SpringContextHolder.class, "context", mockContext);

        // 2. 创建过滤器实例
        filter = DeduplicationFilter.createFilter("test-appender");
        filter.start();

        System.out.println("   [INIT] 过滤器初始化完成");
    }

    @AfterAll
    static void destroyGlobal() {
        if (filter != null) {
            filter.stop();
        }
        // 清理静态 Mock
        ReflectUtil.setFieldValue(SpringContextHolder.class, "context", null);
        System.out.println("██████████ [END] 测试结束 ██████████");
    }

    @BeforeEach
    void setUp() {
        // 清空缓存
        ConcurrentHashMap<?, ?> cache = (ConcurrentHashMap<?, ?>) ReflectUtil.getFieldValue(filter, "cache");
        if (cache != null) {
            cache.clear();
        }
    }

    // ==========================================
    // 1. 核心去重逻辑测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("基本去重逻辑 (Pass -> Deny -> Pass)")
    void testDeduplicationLogic() {
        System.out.println("\n--- 测试：基本去重逻辑 ---");

        // 加速测试：修改时间窗口为 100ms
        long testInterval = 100L;
        // [修复] 字段名修正为 DEFAULT_WINDOW_MS
        ReflectUtil.setFieldValue(filter, "DEFAULT_WINDOW_MS", testInterval);

        System.out.println("   [SETUP] 将去重间隔调整为: " + testInterval + "ms");

        LogEvent event = createLogEvent("Error occurred in module A");

        // 1. 第一次：放行
        Filter.Result result1 = filter.filter(event);
        System.out.println("   [STEP 1] 第一次输入: " + result1);
        assertEquals(Filter.Result.NEUTRAL, result1);

        // 2. 立即重复：拦截
        Filter.Result result2 = filter.filter(event);
        System.out.println("   [STEP 2] 立即重复输入: " + result2);
        assertEquals(Filter.Result.DENY, result2);

        // 3. 等待过期
        System.out.println("   [WAIT] 等待过期...");
        ThreadUtil.sleep(testInterval + 50); // 多等一点确保过期

        // 4. 过期后：重新放行
        Filter.Result result3 = filter.filter(event);
        System.out.println("   [STEP 3] 过期后输入: " + result3);
        assertEquals(Filter.Result.NEUTRAL, result3);
    }

    @Test
    @Order(2)
    @DisplayName("不同内容不应互相影响")
    void testDifferentMessages() {
        System.out.println("\n--- 测试：不同日志内容 ---");

        LogEvent eventA = createLogEvent("Message A");
        LogEvent eventB = createLogEvent("Message B");

        assertEquals(Filter.Result.NEUTRAL, filter.filter(eventA));
        assertEquals(Filter.Result.NEUTRAL, filter.filter(eventB));

        assertEquals(Filter.Result.DENY, filter.filter(eventA));
        assertEquals(Filter.Result.DENY, filter.filter(eventB));
    }

    // ==========================================
    // 2. 边界与健壮性测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("Stop 方法清理缓存")
    void testStopClearsCache() {
        System.out.println("\n--- 测试：Stop 清理缓存 ---");

        filter.filter(createLogEvent("Msg1"));

        ConcurrentHashMap<?, ?> cache = (ConcurrentHashMap<?, ?>) ReflectUtil.getFieldValue(filter, "cache");
        assertFalse(cache.isEmpty());

        filter.stop();
        System.out.println("   [ACTION] 执行 stop()");

        assertEquals(0, cache.size());

        filter.start();
    }

    @Test
    @Order(4)
    @DisplayName("缺失 VthreadPool 时的容错性")
    void testMissingThreadPool() {
        System.out.println("\n--- 测试：VthreadPool 缺失容错 ---");

        Object originalContext = ReflectUtil.getFieldValue(SpringContextHolder.class, "context");
        ReflectUtil.setFieldValue(SpringContextHolder.class, "context", null);

        try {
            LogEvent event = createLogEvent("Log without thread pool");

            assertDoesNotThrow(() -> filter.filter(event));

            Filter.Result result = filter.filter(event);
            assertEquals(Filter.Result.DENY, result);

        } finally {
            ReflectUtil.setFieldValue(SpringContextHolder.class, "context", originalContext);
        }
    }

    private LogEvent createLogEvent(String messageText) {
        LogEvent mockEvent = Mockito.mock(LogEvent.class);
        Message message = new SimpleMessage(messageText);
        when(mockEvent.getMessage()).thenReturn(message);
        when(mockEvent.getLevel()).thenReturn(Level.ERROR);
        return mockEvent;
    }
}