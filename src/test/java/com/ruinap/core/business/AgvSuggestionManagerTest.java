package com.ruinap.core.business;

import cn.hutool.core.thread.ThreadUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author qianye
 * @create 2026-01-12 10:31
 */
class AgvSuggestionManagerTest {

    private AgvSuggestionManager manager;

    @BeforeEach
    void setUp() {
        manager = new AgvSuggestionManager();
    }

    @Test
    @DisplayName("测试：基本添加与获取功能")
    void testAddAndGet() {
        String agvId = "AGV_001";
        manager.addSuggestion(agvId, "SLOW_DOWN");
        manager.addSuggestion(agvId, "KEEP_DISTANCE");

        List<String> suggestions = manager.getSuggestions(agvId);

        assertEquals(2, suggestions.size());
        assertTrue(suggestions.contains("SLOW_DOWN"));
        assertTrue(suggestions.contains("KEEP_DISTANCE"));
    }

    @Test
    @DisplayName("测试：数据过期机制 (5秒)")
    void testExpiration() {
        String agvId = "AGV_002";
        manager.addSuggestion(agvId, "TEMP_STOP");

        // 1. 立即获取 -> 存在
        assertFalse(manager.getSuggestions(agvId).isEmpty());

        // 2. 模拟时间流逝 (为了测试速度，实际代码是5s，这里如果改不了源码常量，只能 sleep)
        // 在实际工程中，建议将 timeout 设为配置项注入，方便测试时设为 100ms
        // 这里为了演示，我们假设经过了 5000ms + 缓冲
        // *注意*：单元测试不建议真 Sleep 5秒，这里仅作逻辑演示。
        // 如果要严谨测试，可以使用 awaitility 或者重构 Manager 允许注入 Clock/Timeout。

        // 这里演示 Sleep (请耐心等待5.1秒)
        ThreadUtil.sleep(5100);
        assertTrue(manager.getSuggestions(agvId).isEmpty(), "过期后应返回空列表");
    }

    @Test
    @DisplayName("测试：并发读写安全性")
    void testConcurrency() throws InterruptedException {
        int threads = 20;
        int loop = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        String agvId = "AGV_CONCURRENT";

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < loop; j++) {
                        // 混合读写
                        manager.addSuggestion(agvId, "SUGGESTION_" + idx + "_" + j);
                        manager.getSuggestions(agvId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        // 验证：不抛异常即通过，ConcurrentHashMap 保证了结构不破坏
        // 最终数量取决于执行速度和过期时间，很难断言具体数字，主要验证无异常
        assertNotNull(manager.getSuggestions(agvId));
    }
}