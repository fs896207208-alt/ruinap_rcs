package com.ruinap.infra.config;

import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
@RunWith(MockitoJUnitRunner.class)
public class GlobalConfigManagerTest {

    @Mock
    private Environment environment;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private GlobalConfigManager globalConfigManager;

    @Test
    public void testReloadAll_Success() {
        // --- 1. 准备数据 ---

        // 模拟容器中存在的两个可重载配置 Bean
        ReloadableConfig mockCore = mock(ReloadableConfig.class);
        ReloadableConfig mockMap = mock(ReloadableConfig.class);

        Map<String, ReloadableConfig> beans = new HashMap<>();
        beans.put("coreYaml", mockCore);
        beans.put("mapYaml", mockMap);

        // 模拟 ApplicationContext 返回这些 Bean
        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(beans);

        // --- 2. 执行 ---
        globalConfigManager.reloadAll();

        // --- 3. 验证核心流程 ---

        // 验证 Step 1: 必须先强制扫描磁盘 (IO 操作)
        // 这一步是所有热更新的基础，必须且只能执行一次
        verify(environment, times(1)).scanAndLoad(anyString());

        // 验证 Step 2: 必须获取所有可重载的 Bean
        verify(applicationContext, times(1)).getBeansOfType(ReloadableConfig.class);

        // 验证 Step 3: 所有 Bean 的 rebind 方法都被调用了
        // 顺序不重要，重要的是都要被通知到
        verify(mockCore, times(1)).rebind();
        verify(mockMap, times(1)).rebind();
    }

    @Test
    public void testReloadAll_EmptyBeans() {
        // --- 场景：容器里没有任何实现 ReloadableConfig 的 Bean ---

        // 模拟返回空 Map
        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(new HashMap<>());

        // 执行
        globalConfigManager.reloadAll();

        // 验证：Environment 依然会被刷新 (虽然没人用，但动作必须做)
        verify(environment, times(1)).scanAndLoad(anyString());

        // 验证：没有报错，流程正常结束
    }

    @Test
    public void testReloadAll_ExceptionSafety() {
        // --- 场景：某个 Bean 重载失败，不应影响其他 Bean ---

        ReloadableConfig goodBean = mock(ReloadableConfig.class);
        ReloadableConfig badBean = mock(ReloadableConfig.class);

        // 模拟 badBean 抛出异常
        doThrow(new RuntimeException("配置格式错误")).when(badBean).rebind();

        Map<String, ReloadableConfig> beans = new HashMap<>();
        beans.put("goodBean", goodBean);
        beans.put("badBean", badBean);

        when(applicationContext.getBeansOfType(ReloadableConfig.class)).thenReturn(beans);

        // 执行
        globalConfigManager.reloadAll();

        // 验证
        // 1. badBean 确实被调用了 (然后报错了)
        verify(badBean, times(1)).rebind();

        // 2. goodBean 依然被调用了 (异常被 try-catch 吞掉了，这是预期的健壮性)
        verify(goodBean, times(1)).rebind();

        // 3. Environment 依然正常扫描
        verify(environment, times(1)).scanAndLoad(anyString());
    }
}