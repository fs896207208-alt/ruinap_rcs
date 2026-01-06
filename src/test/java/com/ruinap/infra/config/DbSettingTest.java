package com.ruinap.infra.config;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.setting.Setting;
import com.ruinap.infra.framework.core.Environment;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * DbSetting 单元测试
 * 覆盖：单例模式、配置读取、热更新机制
 *
 * @author qianye
 * @create 2025-12-08 10:58
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DbSettingTest {

    private DbSetting dbSetting;

    @Mock
    private Environment mockEnv;

    @Mock
    private ConfigAdapter mockConfigAdapter;

    @Mock
    private Setting mockHutoolSetting;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 1. 初始化待测对象
        dbSetting = new DbSetting();

        // 2. 注入 Mock 依赖
        ReflectUtil.setFieldValue(dbSetting, "environment", mockEnv);
        ReflectUtil.setFieldValue(dbSetting, "configAdapter", mockConfigAdapter);
    }

    // ==========================================
    // 1. 基础读取测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("配置加载与读取 - 正常场景")
    void testLoadAndRead() {
        System.out.println("★ 1. 测试配置加载与 Getter");

        // 构造模拟数据:
        // bind 返回的是一个大 Map，结构为: { "rcs_db": { "db_name": "test_db", ... } }
        Map<String, String> dbGroupProps = new HashMap<>();
        dbGroupProps.put("db_name", "rcs_core_test");
        dbGroupProps.put("db_ip", "127.0.0.1");
        dbGroupProps.put("db_port", "5432");

        Map<String, Object> fullConfigMap = new HashMap<>();
        fullConfigMap.put("rcs_db", dbGroupProps);

        // Mock 行为
        when(mockConfigAdapter.bind(eq("rcs_db"), eq(Map.class))).thenReturn(fullConfigMap);

        // 执行初始化
        dbSetting.initialize();

        // 验证 Getter
        assertEquals("rcs_core_test", dbSetting.getDatabaseName());
        assertEquals("127.0.0.1", dbSetting.getDatabaseIp());
        assertEquals("5432", dbSetting.getDatabasePort());

        // 验证通用 Getter
        assertEquals("rcs_core_test", dbSetting.getKeyByGroupAndKey("rcs_db", "db_name"));

        System.out.println("   [PASS] 数据库配置读取正确");
    }

    @Test
    @Order(2)
    @DisplayName("配置获取 Setting 对象")
    void testGetSetting() {
        System.out.println("★ 2. 测试 getSetting 委托");

        when(mockConfigAdapter.getSetting("rcs_db")).thenReturn(mockHutoolSetting);

        Setting result = dbSetting.getSetting();
        assertNotNull(result);
        assertEquals(mockHutoolSetting, result);
    }

    // ==========================================
    // 2. 热加载测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("热加载 (Rebind)")
    void testRebind() {
        System.out.println("★ 3. 测试热加载 (Rebind)");

        // 阶段 1: 初始配置
        Map<String, String> initialProps = new HashMap<>();
        initialProps.put("db_name", "db_v1");
        Map<String, Object> initialMap = new HashMap<>();
        initialMap.put("rcs_db", initialProps);

        when(mockConfigAdapter.bind(eq("rcs_db"), eq(Map.class))).thenReturn(initialMap);
        dbSetting.initialize();

        assertEquals("db_v1", dbSetting.getDatabaseName());

        // 阶段 2: 修改配置并重载
        Map<String, String> newProps = new HashMap<>();
        newProps.put("db_name", "db_v2_reloaded");
        Map<String, Object> newMap = new HashMap<>();
        newMap.put("rcs_db", newProps);

        // 更新 Mock 行为
        when(mockConfigAdapter.bind(eq("rcs_db"), eq(Map.class))).thenReturn(newMap);

        // 执行 rebind
        dbSetting.rebind();

        // 验证是否更新
        assertEquals("db_v2_reloaded", dbSetting.getDatabaseName());
        System.out.println("   [PASS] 热加载更新配置成功");
    }

    // ==========================================
    // 3. 健壮性测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("配置缺失/为空时的健壮性")
    void testNullSafety() {
        System.out.println("★ 4. 测试 Null 安全性");

        // 场景 A: Adapter 返回 null (例如配置文件不存在)
        when(mockConfigAdapter.bind(anyString(), any())).thenReturn(null);

        dbSetting.initialize(); // 不应报错

        assertNull(dbSetting.getDatabaseName());
        assertNull(dbSetting.getKeyByGroupAndKey("rcs_db", "any_key"));

        // 场景 B: Adapter 返回空 Map
        when(mockConfigAdapter.bind(anyString(), any())).thenReturn(Collections.emptyMap());
        dbSetting.rebind();

        // 虽然 settingMap 不为 null，但 get("rcs_db") 返回 null
        assertNull(dbSetting.getDatabaseName());

        // 场景 C: 获取不存在的分组
        assertNull(dbSetting.getKeyByGroupAndKey("non_exist_group", "key"));

        System.out.println("   [PASS] 空值处理安全");
    }
}