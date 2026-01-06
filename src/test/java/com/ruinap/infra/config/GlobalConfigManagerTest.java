package com.ruinap.infra.config;

import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 全局配置管理器测试
 * 验证核心逻辑：是否正确触发了 Environment 扫描 + 所有 Config Bean 的重绑定
 *
 * @author qianye
 * @create 2025-12-25 10:12
 */
@DisplayName("全局配置管理器(GlobalConfig)测试")
class GlobalConfigManagerTest {

    @Mock
    private Environment environment;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private GlobalConfigManager globalConfigManager;

    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("场景1：精准更新 - 仅更新发生变化的文件")
    void testReload_SelectiveUpdate() {
        Set<String> changedFiles = new HashSet<>();
        changedFiles.add("rcs_core");
        when(environment.scanAndLoad(anyString())).thenReturn(changedFiles);

        ReloadableConfig coreBean = mock(ReloadableConfig.class);
        when(coreBean.getSourceName()).thenReturn("rcs_core");

        ReloadableConfig mapBean = mock(ReloadableConfig.class);
        when(mapBean.getSourceName()).thenReturn("rcs_map");

        Map<String, ReloadableConfig> beans = new HashMap<>();
        beans.put("coreBean", coreBean);
        beans.put("mapBean", mapBean);

        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(beans);

        globalConfigManager.reloadAll();

        verify(coreBean, times(1)).rebind();
        verify(mapBean, never()).rebind();
        System.out.println("   [PASS] 精准更新逻辑验证通过");
    }

    @Test
    @DisplayName("场景2：性能优化 - 无文件变更时直接返回")
    void testReload_NoChanges_EarlyReturn() {
        when(environment.scanAndLoad(anyString())).thenReturn(Collections.emptySet());

        globalConfigManager.reloadAll();

        verify(environment, times(1)).scanAndLoad(anyString());
        verify(applicationContext, never()).getBeansOfType(ReloadableConfig.class);
        System.out.println("   [PASS] 性能优化(提前返回)验证通过");
    }

    @Test
    @DisplayName("场景3：异常隔离 - 单个组件失败不影响整体")
    void testReload_ExceptionSafety() {
        Set<String> changedFiles = new HashSet<>(Arrays.asList("rcs_bad", "rcs_good"));
        when(environment.scanAndLoad(anyString())).thenReturn(changedFiles);

        ReloadableConfig badBean = mock(ReloadableConfig.class);
        when(badBean.getSourceName()).thenReturn("rcs_bad");
        doThrow(new RuntimeException("解析错误")).when(badBean).rebind();

        ReloadableConfig goodBean = mock(ReloadableConfig.class);
        when(goodBean.getSourceName()).thenReturn("rcs_good");

        Map<String, ReloadableConfig> beans = new HashMap<>();
        beans.put("bad", badBean);
        beans.put("good", goodBean);
        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(beans);

        globalConfigManager.reloadAll();

        verify(badBean, times(1)).rebind();
        verify(goodBean, times(1)).rebind();

        System.out.println("   [PASS] 异常隔离性验证通过");
    }
}