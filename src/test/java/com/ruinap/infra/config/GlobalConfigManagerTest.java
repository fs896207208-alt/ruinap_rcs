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

import java.util.HashMap;
import java.util.Map;

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
        // JUnit 6: 手动初始化 Mock 注入
        mockitoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 及时关闭 Mock 资源
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("测试：全量重载成功 (Happy Path)")
    void testReloadAll_Success() {
        // --- 1. 准备数据 ---
        // 模拟容器中存在的两个可重载配置 Bean
        ReloadableConfig mockCore = mock(ReloadableConfig.class);
        ReloadableConfig mockMap = mock(ReloadableConfig.class);

        Map<String, ReloadableConfig> beans = new HashMap<>();
        beans.put("rcs_core", mockCore);
        beans.put("rcs_map", mockMap);

        // Mock 容器返回这些 Bean
        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(beans);

        // --- 2. 执行 ---
        globalConfigManager.reloadAll();

        // --- 3. 验证 ---
        // 验证 Environment 是否执行了重新扫描加载
        verify(environment, times(1)).scanAndLoad(anyString());

        // 验证所有 Bean 是否都收到了 rebind 通知
        verify(mockCore, times(1)).rebind();
        verify(mockMap, times(1)).rebind();
    }

    @Test
    @DisplayName("测试：空容器重载 (Empty Beans)")
    void testReloadAll_EmptyBeans() {
        // --- 场景：容器里没有任何实现 ReloadableConfig 的 Bean ---

        // 模拟返回空 Map
        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(new HashMap<>());

        // 执行
        globalConfigManager.reloadAll();

        // 验证：Environment 依然会被刷新 (虽然没人用，但基础配置加载动作必须做)
        verify(environment, times(1)).scanAndLoad(anyString());

        // 验证：没有报错，流程正常结束
    }

    @Test
    @DisplayName("测试：异常隔离性 (Exception Safety)")
    void testReloadAll_ExceptionSafety() {
        // --- 场景：某个 Bean 重载失败，不应中断循环，必须保证其他 Bean 能正常更新 ---

        ReloadableConfig goodBean = mock(ReloadableConfig.class);
        ReloadableConfig badBean = mock(ReloadableConfig.class);

        // 模拟 badBean 抛出运行时异常
        doThrow(new RuntimeException("配置格式错误")).when(badBean).rebind();

        Map<String, ReloadableConfig> beans = new HashMap<>();
        beans.put("goodBean", goodBean);
        beans.put("badBean", badBean);

        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(beans);

        // 执行 (预期内部会 catch 异常并打印日志，而不是抛出到外部)
        globalConfigManager.reloadAll();

        // 验证：
        // 1. 坏的 Bean 被调用过 (然后挂了)
        verify(badBean, times(1)).rebind();

        // 2. 好的 Bean 依然被正常调用 (未受牵连)
        verify(goodBean, times(1)).rebind();

        // 3. Environment 依然被刷新
        verify(environment, times(1)).scanAndLoad(anyString());
    }
}