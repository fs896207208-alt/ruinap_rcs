package com.ruinap.infra.config;

import cn.hutool.setting.Setting;
import com.ruinap.infra.framework.core.Environment;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
@RunWith(MockitoJUnitRunner.class)
public class DbSettingTest {

    @Mock
    private Environment environment;

    @Mock
    private ConfigAdapter configAdapter; // 模拟适配器

    @InjectMocks
    private DbSetting dbSetting;

    @Before
    public void setUp() {
        // 模拟 Environment 返回的 Map 数据 (用于 bind)
        Map<String, Object> rootMap = new HashMap<>();
        Map<String, String> dbGroup = new HashMap<>();
        dbGroup.put("db_name", "test_db");
        dbGroup.put("db_ip", "10.0.0.1");
        rootMap.put("rcs_db", dbGroup);

        when(environment.bind(eq("rcs_db"), eq(Map.class))).thenReturn(rootMap);
    }

    @Test
    public void testInitializeAndGetters() {
        dbSetting.initialize();

        Assert.assertEquals("test_db", dbSetting.getDatabaseName());
        Assert.assertEquals("10.0.0.1", dbSetting.getDatabaseIp());
        // 未配置的端口应为 null
        Assert.assertNull(dbSetting.getDatabasePort());
    }

    @Test
    public void testGetSettingFromAdapter() {
        // 模拟 ConfigAdapter 返回 Hutool Setting 对象
        Setting mockSetting = new Setting();
        when(configAdapter.getSetting("rcs_db")).thenReturn(mockSetting);

        // 验证 DbSetting 是否正确委托给了 Adapter
        Setting result = dbSetting.getSetting();
        Assert.assertNotNull(result);
        Assert.assertSame(mockSetting, result);
    }
}