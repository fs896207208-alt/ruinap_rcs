package com.ruinap.infra.config;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.config.pojo.CoreConfig;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.config.RcsCoreConfigRefreshEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CoreYaml 单元测试 (JUnit 4 + Mockito Inline)
 *
 * @author qianye
 * @create 2025-12-24 17:06
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoreYamlTest {

    private static CoreYaml coreYaml;
    private static ApplicationContext mockCtx;
    private static Environment mockEnv;
    private static CoreConfig mockConfig;

    @BeforeEach
    void setUp() {
        // 1. 初始化待测对象
        coreYaml = new CoreYaml();

        // 2. Mock 依赖
        mockCtx = mock(ApplicationContext.class);
        mockEnv = mock(Environment.class);
        mockConfig = mock(CoreConfig.class);

        // 3. 注入依赖
        ReflectUtil.setFieldValue(coreYaml, "ctx", mockCtx);
        ReflectUtil.setFieldValue(coreYaml, "environment", mockEnv);
    }

    // ==========================================
    // 1. 初始化与绑定测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("初始化成功测试")
    void testInitialize_Success() {
        System.out.println("★ 1. 测试初始化 (Initialize)");

        // 模拟 Environment 绑定成功
        when(mockEnv.bind(eq("rcs_core"), eq(CoreConfig.class))).thenReturn(mockConfig);

        // 执行初始化
        coreYaml.initialize();

        // 验证 config 字段是否被正确赋值
        Object actualConfig = ReflectUtil.getFieldValue(coreYaml, "config");
        assertNotNull(actualConfig);
        assertEquals(mockConfig, actualConfig);
        System.out.println("   [PASS] 配置绑定成功");
    }

    @Test
    @Order(2)
    @DisplayName("初始化失败测试 (配置不存在)")
    void testInitialize_Failure() {
        System.out.println("★ 2. 测试初始化失败 (配置缺失)");

        // 模拟 Environment 返回 null
        when(mockEnv.bind(any(), any())).thenReturn(null);

        // 验证是否抛出运行时异常
        RuntimeException ex = assertThrows(RuntimeException.class, () -> coreYaml.initialize());
        assertTrue(ex.getMessage().contains("CoreYaml 初始化失败"));
        System.out.println("   [PASS] 成功捕获初始化异常");
    }

    @Test
    @Order(3)
    @DisplayName("热加载测试 (Rebind)")
    void testRebind() {
        System.out.println("★ 3. 测试热加载 (Rebind)");

        when(mockEnv.bind(eq("rcs_core"), eq(CoreConfig.class))).thenReturn(mockConfig);

        // 执行热加载
        coreYaml.rebind();

        // 验证：
        // 1. 重新调用了 bind 方法
        verify(mockEnv, times(1)).bind("rcs_core", CoreConfig.class);

        // 2. 发布了 RcsCoreConfigRefreshEvent 事件
        ArgumentCaptor<RcsCoreConfigRefreshEvent> eventCaptor = ArgumentCaptor.forClass(RcsCoreConfigRefreshEvent.class);
        verify(mockCtx, times(1)).publishEvent(eventCaptor.capture());

        assertNotNull(eventCaptor.getValue());
        assertEquals(coreYaml, eventCaptor.getValue().getSource());
        System.out.println("   [PASS] 热加载并发布事件成功");
    }

    // ==========================================
    // 2. Getter 与默认值测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("参数读取 - 正常值")
    void testGetters_WithValues() {
        System.out.println("★ 4. 测试 Getter (正常值)");

        // [重点修复] 严格使用 LinkedHashMap 构造模拟数据
        LinkedHashMap<String, Integer> threadPoolMap = new LinkedHashMap<>();
        threadPoolMap.put("core_pool_size", 10);
        threadPoolMap.put("max_pool_size", 20);
        threadPoolMap.put("queue_capacity", 500);

        LinkedHashMap<String, Integer> portMap = new LinkedHashMap<>();
        portMap.put("web_port", 8080);
        portMap.put("netty_websocket_port", 8081);

        // 配置 Mock 行为
        when(mockConfig.getRcsThreadPool()).thenReturn(threadPoolMap);
        when(mockConfig.getRcsPort()).thenReturn(portMap);

        // 必须让 initialize 成功才能测 getter
        when(mockEnv.bind(any(), any())).thenReturn(mockConfig);
        coreYaml.initialize();

        // 验证读取结果
        assertEquals(10, coreYaml.getAlgoCorePoolSize());
        assertEquals(20, coreYaml.getAlgoMaxPoolSize());
        assertEquals(500, coreYaml.getAlgoQueueCapacity());
        assertEquals(8080, coreYaml.getWebPort());
        assertEquals(8081, coreYaml.getNettyWebsocketPort());

        System.out.println("   [PASS] 读取配置值正确");
    }

    @Test
    @Order(5)
    @DisplayName("参数读取 - 默认值兜底")
    void testGetters_Defaults() {
        System.out.println("★ 5. 测试 Getter (默认值兜底)");

        // 场景：配置对象存在，但具体 Map 为 null 或空
        when(mockConfig.getRcsThreadPool()).thenReturn(null);
        // [重点修复] 返回空的 LinkedHashMap
        when(mockConfig.getRcsPort()).thenReturn(new LinkedHashMap<>());

        when(mockEnv.bind(any(), any())).thenReturn(mockConfig);
        coreYaml.initialize();

        // 验证默认值逻辑
        // 线程池核心数默认 = CPU核数 + 1
        int expectedCore = Runtime.getRuntime().availableProcessors() + 1;

        assertEquals(expectedCore, coreYaml.getAlgoCorePoolSize(), "核心线程数应有默认值");
        assertEquals(expectedCore, coreYaml.getAlgoMaxPoolSize(), "最大线程数应有默认值");
        assertEquals(2048, coreYaml.getAlgoQueueCapacity(), "队列容量应有默认值");
        assertEquals(60, coreYaml.getAlgoKeepAliveSeconds(), "存活时间应有默认值");

        assertEquals(9090, coreYaml.getWebPort(), "Web端口应有默认值");
        assertEquals(9091, coreYaml.getNettyWebsocketPort(), "WS端口应有默认值");

        // 场景：Config 对象本身为 null (模拟极端情况)
        ReflectUtil.setFieldValue(coreYaml, "config", null);
        assertEquals(expectedCore, coreYaml.getAlgoCorePoolSize());
        assertEquals(9092, coreYaml.getNettyMqttPort());

        System.out.println("   [PASS] 默认值机制生效");
    }

    @Test
    @Order(6)
    @DisplayName("基础信息读取")
    void testBasicInfo() {
        System.out.println("★ 6. 测试基础信息 (Sys Config)");

        // [重点修复] 使用 LinkedHashMap
        LinkedHashMap<String, String> sysMap = new LinkedHashMap<>();
        sysMap.put("develop", "true");
        sysMap.put("enable_arthas", "false");

        when(mockConfig.getRcsSys()).thenReturn(sysMap);
        when(mockEnv.bind(any(), any())).thenReturn(mockConfig);
        coreYaml.initialize();

        assertEquals("true", coreYaml.getDevelop());
        assertEquals("false", coreYaml.getEnableArthas());
        assertEquals("rcs_core", coreYaml.getSourceName());

        // 验证 Timer Map 读取
        LinkedHashMap<String, LinkedHashMap<String, String>> timerMap = new LinkedHashMap<>();
        when(mockConfig.getRcsTimer()).thenReturn(timerMap);
        assertNotNull(coreYaml.getTimerCommon());

        System.out.println("   [PASS] 基础信息读取正确");
    }
}