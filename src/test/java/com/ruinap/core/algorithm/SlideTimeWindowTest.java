package com.ruinap.core.algorithm;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.framework.core.event.RcsMapConfigRefreshEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.lenient;

/**
 * SlideTimeWindow 核心单元测试
 * <p>
 * 覆盖场景：
 * 1. 配置加载与热更 (Configuration & Hot Reload)
 * 2. 权重增减逻辑 (Weight Logic)
 * 3. 成本计算公式 (Cost Calculation)
 * 4. 边界值防御 (Edge Cases)
 * 5. 高并发原子性 (Concurrency Safety)
 *
 * @author qianye
 * @create 2026-01-08 11:27
 */
@ExtendWith(MockitoExtension.class) // JUnit 5 的扩展方式
class SlideTimeWindowTest {

    @InjectMocks
    private SlideTimeWindow slideTimeWindow;

    @Mock
    private CoreYaml coreYaml;

    private Map<String, Integer> configMap;

    @BeforeEach
        // 替代 @Before
    void setUp() {
        configMap = new HashMap<>();
        configMap.put("weight_step", 2);
        configMap.put("weight_max_step", 20);
        configMap.put("move_time_window_max_occupancy", 10);

        lenient().when(coreYaml.getAlgorithmCommon()).thenReturn(configMap);
        slideTimeWindow.init();
    }

    @Test
    @DisplayName("配置加载：验证初始化参数读取")
    void testInit_LoadConfig() {
        int step = (int) ReflectUtil.getFieldValue(slideTimeWindow, "weightStep");
        Assertions.assertEquals(2, step);
    }

    @Test
    @DisplayName("热更新：验证事件触发后配置重载")
    void testOnApplicationEvent() {
        // 1. 制造脏数据
        List<RcsPoint> points = Collections.singletonList(createPoint(1));
        slideTimeWindow.addWeight(points);
        Assertions.assertEquals(3.0, slideTimeWindow.getWeight(0, 1));

        // 2. 修改模拟配置
        configMap.put("weight_step", 5);

        // 3. 触发刷新 (传入 null source 即可)
        slideTimeWindow.onApplicationEvent(new RcsMapConfigRefreshEvent(new Object()));

        // 4. 验证清理和更新
        Assertions.assertEquals(1.0, slideTimeWindow.getWeight(0, 1), "权重表应清空");
        int newStep = (int) ReflectUtil.getFieldValue(slideTimeWindow, "weightStep");
        Assertions.assertEquals(5, newStep, "配置应更新");
    }

    @Test
    @DisplayName("成本计算：验证不同拥堵等级的计算公式")
    void testCostCalculation() {
        // 正常情况
        Assertions.assertEquals(100, slideTimeWindow.costCalculation(100.0, 1.0));
        // 轻微拥堵 (Weight 3.0 < 10): 100*1.1 + (3-1)*20 = 150
        Assertions.assertEquals(150, slideTimeWindow.costCalculation(100.0, 3.0));
        // 严重拥堵 (Weight 12.0 >= 10): 100 + (12-1)*100 = 1200
        Assertions.assertEquals(1200, slideTimeWindow.costCalculation(100.0, 12.0));
    }

    @Test
    @DisplayName("防御测试：验证非法参数不崩溃")
    void testCostCalculation_InvalidInput() {
        Assertions.assertEquals(1, slideTimeWindow.costCalculation(-1.0, 5.0));
        Assertions.assertEquals(100, slideTimeWindow.costCalculation(100.0, -5.0));
    }

    /**
     * 新增：两阶段可视化压测
     * 目的：让用户亲眼看到权重“涨上去”又“降下来”的过程
     */
    @Test
    @DisplayName("可视化压测：验证权重积累与释放的全过程")
    void testCongestionAndRelease() throws InterruptedException {
        // 1. 临时调整配置，为了演示效果，把上限调大
        configMap.put("weight_step", 1);        // 每辆车加 1.0
        configMap.put("weight_max_step", 1000); // 上限调到 1000，防止被截断
        ReflectUtil.invoke(slideTimeWindow, "refreshConfig"); // 刷新配置生效

        int carCount = 100; // 模拟 100 辆车
        final RcsPoint point = createPoint(888);
        final List<RcsPoint> points = Collections.singletonList(point);

        System.out.println("\n🚗🚗🚗 === 开始两阶段压测 (模拟 100 辆车拥堵) === 🚗🚗🚗");
        System.out.println("初始权重: " + slideTimeWindow.getWeight(0, 888));

        // --- 阶段一：100 辆车涌入 (只加不减) ---
        ExecutorService executor1 = Executors.newFixedThreadPool(carCount);
        CountDownLatch latch1 = new CountDownLatch(carCount);

        for (int i = 0; i < carCount; i++) {
            executor1.submit(() -> {
                try {
                    slideTimeWindow.addWeight(points); // 车辆进入
                } finally {
                    latch1.countDown();
                }
            });
        }
        latch1.await();
        executor1.shutdown();

        // 🔍 验证阶段一：此时权重应该很高！
        double peakWeight = slideTimeWindow.getWeight(0, 888);
        System.out.println("🔥 [阶段一完成] 100辆车进入后，当前权重: " + peakWeight);

        // 预期权重 = 基础 1.0 + (100车 * 1.0步长) = 101.0
        Assertions.assertEquals(101.0, peakWeight, "权重应该累加到 101.0");

        // --- 阶段二：100 辆车离开 (只减不加) ---
        System.out.println("⬇️ [阶段二开始] 车辆开始疏散...");
        ExecutorService executor2 = Executors.newFixedThreadPool(carCount);
        CountDownLatch latch2 = new CountDownLatch(carCount);

        for (int i = 0; i < carCount; i++) {
            executor2.submit(() -> {
                try {
                    slideTimeWindow.subWeight(points); // 车辆离开
                } finally {
                    latch2.countDown();
                }
            });
        }
        latch2.await();
        executor2.shutdown();

        // 🔍 验证阶段二：此时权重应该归零！
        double finalWeight = slideTimeWindow.getWeight(0, 888);
        System.out.println("✅ [阶段二完成] 100辆车离开后，最终权重: " + finalWeight);

        @SuppressWarnings("unchecked")
        Map<Integer, Double> map = (Map<Integer, Double>) ReflectUtil.getFieldValue(slideTimeWindow, "weightMap");

        if (!map.containsKey(888)) {
            System.out.println("✨ 完美！Map 中 Key 已被清理。");
        } else {
            System.err.println("❌ 失败！Map 中 Key 残留。");
        }

        Assertions.assertEquals(1.0, finalWeight, "最终权重应回归 1.0");
        Assertions.assertFalse(map.containsKey(888), "内存应被释放");
    }

    private RcsPoint createPoint(int index) {
        RcsPoint point = new RcsPoint();
        point.setGraphIndex(index);
        return point;
    }
}