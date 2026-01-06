package com.ruinap.infra.config;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.config.pojo.MapConfig;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsMapConfigRefreshEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MapYaml 地图配置类测试
 * <p>
 * 测试目标：
 * 1. 验证复杂嵌套 Map 结构的读取 (LinkedHashMap)。
 * 2. 验证多层级 Mock (config.getRcsMap().getSourceFile())。
 * 3. 验证热重载事件发布与日志记录。
 * 4. 验证空指针防御机制 (Null Safety)。
 * </p>
 *
 * @author qianye
 * @create 2025-12-24 17:18
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MapYamlTest {

    private MapYaml mapYaml;
    private Environment mockEnv;
    private ApplicationContext mockCtx;
    private MapConfig mockConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 1. 初始化待测对象
        mapYaml = new MapYaml();

        // 2. Mock 依赖组件
        mockEnv = mock(Environment.class);
        mockCtx = mock(ApplicationContext.class);
        mockConfig = mock(MapConfig.class); // Mock POJO

        // 3. 注入依赖
        ReflectUtil.setFieldValue(mapYaml, "environment", mockEnv);
        ReflectUtil.setFieldValue(mapYaml, "ctx", mockCtx);

    }

    // ==========================================
    // 1. 生命周期测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("初始化流程")
    void testInitialize() {
        System.out.println("★ 1. 测试初始化");

        when(mockEnv.bind(eq("rcs_map"), eq(MapConfig.class))).thenReturn(mockConfig);

        mapYaml.initialize();

        Object actualConfig = ReflectUtil.getFieldValue(mapYaml, "config");
        assertEquals(mockConfig, actualConfig);
        System.out.println("   [PASS] 初始化绑定成功");
    }

    @Test
    @Order(2)
    @DisplayName("热重载 (Rebind & Event)")
    void testRebind() {
        System.out.println("★ 2. 测试热重载");

        // 模拟 bind 返回新对象
        MapConfig newConfig = mock(MapConfig.class);
        when(mockEnv.bind(eq("rcs_map"), eq(MapConfig.class))).thenReturn(newConfig);

        // 执行重载
        mapYaml.rebind();

        // 验证：
        // 1. Config 更新
        Object actualConfig = ReflectUtil.getFieldValue(mapYaml, "config");
        assertEquals(newConfig, actualConfig);

        // 2. 事件发布 (RcsMapConfigRefreshEvent)
        ArgumentCaptor<RcsMapConfigRefreshEvent> eventCaptor = ArgumentCaptor.forClass(RcsMapConfigRefreshEvent.class);
        verify(mockCtx, times(1)).publishEvent(eventCaptor.capture());
        assertEquals(mapYaml, eventCaptor.getValue().getSource());

        // 3. 日志打印
        System.out.println("   [CHECK] 请检查上方是否出现 [RcsLog-Mock] 开头的日志");

        System.out.println("   [PASS] 热重载及事件发布成功");
    }

    // ==========================================
    // 2. 复杂数据结构读取测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("地图文件列表读取 (Deep Stubs)")
    void testGetRcsMaps() {
        System.out.println("★ 3. 测试地图文件列表读取");

        // 场景 A: 正常读取 (Happy Path)
        // 使用 RETURNS_DEEP_STUBS 自动 Mock 链式调用：config.getRcsMap().getSourceFile()
        // 这样我们就不需要手动 Mock 中间的 RcsMap 对象了
        MapConfig deepMockConfig = mock(MapConfig.class, RETURNS_DEEP_STUBS);

        LinkedHashMap<Integer, String> files = new LinkedHashMap<>();
        files.put(1, "map_v1.xml");

        // 直接打桩链式调用的结果
        when(deepMockConfig.getRcsMap().getSourceFile()).thenReturn(files);

        ReflectUtil.setFieldValue(mapYaml, "config", deepMockConfig);
        assertEquals(files, mapYaml.getRcsMaps());
        System.out.println("   [PASS] 链式调用 Mock 成功");

        // 场景 B: 空指针防御
        // 切换回普通的 mockConfig 来测试 null 场景
        when(mockConfig.getRcsMap()).thenReturn(null);
        ReflectUtil.setFieldValue(mapYaml, "config", mockConfig);

        assertTrue(mapYaml.getRcsMaps().isEmpty());
        System.out.println("   [PASS] 空指针防御成功");
    }

    @Test
    @Order(4)
    @DisplayName("待机点读取 (LinkedHashMap)")
    void testGetStandbyPoint() {
        System.out.println("★ 4. 测试待机点读取");

        LinkedHashMap<Integer, ArrayList<Integer>> standbyMap = new LinkedHashMap<>();
        ArrayList<Integer> points = new ArrayList<>();
        points.add(101);
        standbyMap.put(1, points);

        when(mockConfig.getStandbyPoint()).thenReturn(standbyMap);
        ReflectUtil.setFieldValue(mapYaml, "config", mockConfig);

        assertEquals(1, mapYaml.getStandbyPoint().size());
        assertEquals(101, mapYaml.getStandbyPoint().get(1).get(0));
    }

    @Test
    @Order(5)
    @DisplayName("复杂嵌套：管制区 (ControlArea)")
    void testGetControlArea() {
        System.out.println("★ 5. 测试复杂嵌套 (管制区)");

        // 构造 LinkedHashMap<Integer, LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>>>
        LinkedHashMap<Integer, LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>>> areaMap = new LinkedHashMap<>();

        // 填充数据
        LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>> level2 = new LinkedHashMap<>();
        LinkedHashMap<Integer, ArrayList<Integer>> level3 = new LinkedHashMap<>();
        ArrayList<Integer> coords = new ArrayList<>();
        coords.add(999);

        level3.put(1, coords);
        level2.put("mutex_area", level3);
        areaMap.put(10, level2);

        when(mockConfig.getControlArea()).thenReturn(areaMap);
        ReflectUtil.setFieldValue(mapYaml, "config", mockConfig);

        // 验证读取
        LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>> result = mapYaml.getControlArea().get(10);
        assertNotNull(result);
        assertEquals(999, result.get("mutex_area").get(1).getFirst());
    }

    // ==========================================
    // 3. 健壮性测试 (Null Safety)
    // ==========================================

    @Test
    @Order(6)
    @DisplayName("全局空指针防御")
    void testGlobalNullSafety() {
        System.out.println("★ 6. 测试全局空指针防御");

        // 场景 1: config 为 null
        ReflectUtil.setFieldValue(mapYaml, "config", null);

        assertNotNull(mapYaml.getRcsMaps());
        assertTrue(mapYaml.getRcsMaps().isEmpty());

        assertNotNull(mapYaml.getBridgePoint());
        assertTrue(mapYaml.getBridgePoint().isEmpty());

        assertNotNull(mapYaml.getControlArea());
        assertTrue(mapYaml.getControlArea().isEmpty());

        // 场景 2: config 存在，但内部 Map 为 null
        ReflectUtil.setFieldValue(mapYaml, "config", mockConfig);
        when(mockConfig.getBridgePoint()).thenReturn(null);
        when(mockConfig.getControlArea()).thenReturn(null);

        assertTrue(mapYaml.getBridgePoint().isEmpty());
        assertTrue(mapYaml.getControlArea().isEmpty());

        System.out.println("   [PASS] 健壮性测试通过");
    }
}