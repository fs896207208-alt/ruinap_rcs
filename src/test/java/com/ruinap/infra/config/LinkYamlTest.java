package com.ruinap.infra.config;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.config.pojo.LinkConfig;
import com.ruinap.infra.config.pojo.link.LinkEntity;
import com.ruinap.infra.config.pojo.link.TransferLinkEntity;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import org.junit.jupiter.api.*;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:15
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LinkYamlTest {

    private LinkYaml linkYaml;
    private Environment mockEnv;
    private ApplicationContext mockCtx;
    private LinkConfig mockConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 1. 初始化待测对象
        linkYaml = new LinkYaml();

        // 2. Mock 依赖
        mockEnv = mock(Environment.class);
        mockCtx = mock(ApplicationContext.class);

        // Mock POJO 对象 (因为没有源码，只能 Mock)
        mockConfig = mock(LinkConfig.class);

        // 3. 注入依赖
        ReflectUtil.setFieldValue(linkYaml, "environment", mockEnv);
        ReflectUtil.setFieldValue(linkYaml, "ctx", mockCtx);
    }

    // ==========================================
    // 1. 生命周期测试 (Init & Rebind)
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("初始化流程验证")
    void testInitialize() {
        System.out.println("★ 1. 测试初始化 (Initialize)");

        // 模拟 Environment 返回 Mock 的 Config 对象
        when(mockEnv.bind(eq("rcs_link"), eq(LinkConfig.class))).thenReturn(mockConfig);

        // 执行初始化
        // 注意：handlerLinkConfig 会尝试反射 mockConfig，
        // 由于 Mock 对象没有真实字段，反射循环通常会直接跳过，不会报错，这符合预期。
        assertDoesNotThrow(() -> linkYaml.initialize());

        // 验证 config 字段被赋值
        Object actualConfig = ReflectUtil.getFieldValue(linkYaml, "config");
        assertEquals(mockConfig, actualConfig);

        System.out.println("   [PASS] 初始化成功");
    }

    @Test
    @Order(2)
    @DisplayName("热重载流程验证")
    void testRebind() {
        System.out.println("★ 2. 测试热重载 (Rebind)");

        when(mockEnv.bind(eq("rcs_link"), eq(LinkConfig.class))).thenReturn(mockConfig);

        // 执行重载
        linkYaml.rebind();

        // 验证重新绑定
        verify(mockEnv, times(1)).bind("rcs_link", LinkConfig.class);
        // LinkYaml 的 rebind 没有发布事件，所以不需要验证 publishEvent

        System.out.println("   [PASS] 热重载调用成功");
    }

    // ==========================================
    // 2. AGV 配置读取测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("AGV 配置读取 - 正常与空值")
    void testGetAgvLink() {
        System.out.println("★ 3. 测试 AGV 配置读取");

        // 场景 1: 正常数据
        LinkEntity mockAgvEntity = mock(LinkEntity.class);
        Map<String, Map<String, String>> agvLinks = new HashMap<>();
        Map<String, String> agv1 = new HashMap<>();
        agv1.put("ip", "192.168.1.100");
        agvLinks.put("AGV_001", agv1);

        when(mockConfig.getAgvLink()).thenReturn(mockAgvEntity);
        when(mockAgvEntity.getLinks()).thenReturn(agvLinks);

        // 注入 Config
        ReflectUtil.setFieldValue(linkYaml, "config", mockConfig);

        // 验证
        Map<String, Map<String, String>> resultAll = linkYaml.getAgvLink();
        assertEquals(1, resultAll.size());
        assertEquals("192.168.1.100", resultAll.get("AGV_001").get("ip"));

        // 验证单个获取
        Map<String, String> resultSingle = linkYaml.getAgvLink("AGV_001");
        assertNotNull(resultSingle);
        assertEquals("192.168.1.100", resultSingle.get("ip"));

        // 场景 2: 数据为空 (Entity 存在但 Map 为空)
        when(mockAgvEntity.getLinks()).thenReturn(Collections.emptyMap());
        assertTrue(linkYaml.getAgvLink().isEmpty());

        // 场景 3: Entity 为 null
        when(mockConfig.getAgvLink()).thenReturn(null);
        assertTrue(linkYaml.getAgvLink().isEmpty());
    }

    // ==========================================
    // 3. 充电桩配置读取测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("充电桩配置读取")
    void testGetChargeLink() {
        System.out.println("★ 4. 测试充电桩配置读取");

        LinkEntity mockChargeEntity = mock(LinkEntity.class);
        Map<String, Map<String, String>> chargeLinks = new HashMap<>();
        Map<String, String> charger1 = new HashMap<>();
        charger1.put("type", "V5");
        chargeLinks.put("C_01", charger1);

        when(mockConfig.getChargeLink()).thenReturn(mockChargeEntity);
        when(mockChargeEntity.getLinks()).thenReturn(chargeLinks);

        ReflectUtil.setFieldValue(linkYaml, "config", mockConfig);

        assertEquals("V5", linkYaml.getChargeLink().get("C_01").get("type"));
        assertEquals("V5", linkYaml.getChargeLink("C_01").get("type"));
    }

    // ==========================================
    // 4. 中转站配置读取测试
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("中转站配置读取")
    void testGetTransferLink() {
        System.out.println("★ 5. 测试中转站配置读取");

        TransferLinkEntity mockTransferEntity = mock(TransferLinkEntity.class);
        Map<String, String> transferMap = new HashMap<>();
        transferMap.put("url", "http://transfer.local");

        when(mockConfig.getTransferLink()).thenReturn(mockTransferEntity);
        when(mockTransferEntity.getLink()).thenReturn(transferMap);

        ReflectUtil.setFieldValue(linkYaml, "config", mockConfig);

        Map<String, String> result = linkYaml.getTransferLink();
        assertEquals("http://transfer.local", result.get("url"));

        // 验证空值防御
        when(mockConfig.getTransferLink()).thenReturn(null);
        assertTrue(linkYaml.getTransferLink().isEmpty());
    }

    // ==========================================
    // 5. 健壮性测试 (Null Safety)
    // ==========================================

    @Test
    @Order(6)
    @DisplayName("配置整体为 Null 时的安全性")
    void testGlobalNullSafety() {
        System.out.println("★ 6. 测试全局 Null 安全性");

        // 将 config 置为 null (模拟未初始化状态)
        ReflectUtil.setFieldValue(linkYaml, "config", null);

        // 验证所有 Getter 是否能安全返回空集合，而不是抛出 NPE
        assertNotNull(linkYaml.getAgvLink());
        assertTrue(linkYaml.getAgvLink().isEmpty());

        assertNotNull(linkYaml.getChargeLink());
        assertTrue(linkYaml.getChargeLink().isEmpty());

        assertNotNull(linkYaml.getTransferLink());
        assertTrue(linkYaml.getTransferLink().isEmpty());

        System.out.println("   [PASS] 健壮性测试通过");
    }
}