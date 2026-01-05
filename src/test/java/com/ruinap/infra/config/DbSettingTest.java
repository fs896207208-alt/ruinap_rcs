package com.ruinap.infra.config;

import com.ruinap.infra.framework.core.Environment;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * DbSetting 单元测试
 * 覆盖：单例模式、配置读取、热更新机制
 *
 * @author qianye
 * @create 2025-12-08 10:58
 */
@DisplayName("数据库配置(DbSetting)测试")
class DbSettingTest {

    @Mock
    private Environment environment;

    @Mock
    private ConfigAdapter configAdapter; // 模拟适配器

    @InjectMocks
    private DbSetting dbSetting;

    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        // JUnit 6: 手动初始化 Mock 注入
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        // 模拟 Environment 返回的 Map 数据 (用于 bind)
        // 保持原测试逻辑不变
        Map<String, Object> rootMap = new HashMap<>();
        Map<String, String> dbGroup = new HashMap<>();
        dbGroup.put("db_name", "test_db");
        dbGroup.put("db_ip", "10.0.0.1");
        rootMap.put("rcs_db", dbGroup);

        when(environment.bind(eq("rcs_db"), eq(Map.class))).thenReturn(rootMap);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 及时关闭 Mock 资源
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("测试：初始化与参数获取")
    void testInitializeAndGetters() {
        // 执行初始化
        dbSetting.initialize();

        // 验证属性是否正确注入
        // JUnit 6: Assertions.assertEquals(expected, actual)
        Assertions.assertEquals("test_db", dbSetting.getDatabaseName());
        Assertions.assertEquals("10.0.0.1", dbSetting.getDatabaseIp());

        // 未配置的端口应为 null
        Assertions.assertNull(dbSetting.getDatabasePort());
    }
}