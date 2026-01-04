package com.ruinap.infra.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.ruinap.infra.config.pojo.CoreConfig;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsCoreConfigRefreshEvent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.LinkedHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CoreYaml 单元测试 (JUnit 4 + Mockito Inline)
 *
 * @author qianye
 * @create 2025-12-24 17:06
 */
@RunWith(MockitoJUnitRunner.class)
public class CoreYamlTest {

    @Mock
    private Environment environment; // 模拟 IOC 环境

    @Mock
    private ApplicationContext ctx;  // 模拟容器上下文（用于验证事件发布）

    @InjectMocks
    private CoreYaml coreYaml;       // 被测对象

    private CoreConfig mockConfig;   // 模拟的配置数据

    // 静态方法的 Mock 控制器
    private MockedStatic<FileUtil> fileUtilMock;
    private MockedStatic<SecureUtil> secureUtilMock;

    @Before
    public void setUp() {
        // 1. 准备测试数据
        mockConfig = new CoreConfig();
        LinkedHashMap<String, String> rcsSys = new LinkedHashMap<>();
        rcsSys.put("develop", "true");
        mockConfig.setRcsSys(rcsSys);
        // 防止 NPE
        mockConfig.setRcsPort(new LinkedHashMap<>());
        mockConfig.setAlgorithmCommon(new LinkedHashMap<>());

        // 2. 开启静态方法 Mock (接管 Hutool 的行为)
        fileUtilMock = mockStatic(FileUtil.class);
        secureUtilMock = mockStatic(SecureUtil.class);
    }

    @After
    public void tearDown() {
        // 3. 必须关闭静态 Mock，避免污染其他测试
        if (fileUtilMock != null) fileUtilMock.close();
        if (secureUtilMock != null) secureUtilMock.close();
    }

    @Test
    public void testInitialize() {
        // --- 准备 ---
        // 模拟 environment.bind 返回我们准备好的 mockConfig
        when(environment.bind(eq("rcs_core"), eq(CoreConfig.class))).thenReturn(mockConfig);

        // 模拟文件存在检查 (尽管 initialize 不强求文件存在，但逻辑里有 newFile)
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);

        // --- 执行 ---
        coreYaml.initialize();

        // --- 验证 ---
        Assert.assertEquals("配置读取失败", "true", coreYaml.getDevelop());
        // 验证 bind 方法被调用了 1 次
        verify(environment, times(1)).bind(eq("rcs_core"), eq(CoreConfig.class));
    }

    @Test
    public void testRequestReloadFlow() {
        // --- 场景模拟：文件发生了变化，触发热更新 ---

        // 1. 模拟物理文件对象
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true); // 文件始终存在

        // 2. 模拟 Environment 行为
        when(environment.bind(eq("rcs_core"), eq(CoreConfig.class))).thenReturn(mockConfig);

        // --- 步骤 A: 初始化 (记录旧指纹) ---
        // 模拟第一次计算 MD5 为 "hash_v1"
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("hash_v1");

        coreYaml.initialize();

        // --- 步骤 B: 触发热更新 (发现新指纹) ---
        // 模拟第二次计算 MD5 为 "hash_v2" (指纹变了！)
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("hash_v2");

//        coreYaml.requestReload();

        // --- 验证核心逻辑 ---

        // 1. 验证 Environment 确实被要求重新扫描磁盘了 (scanAndLoad)
        verify(environment, times(1)).scanAndLoad(anyString());

        // 2. 验证 bind 被调用了 2 次 (初始化 1 次 + 重载 1 次)
        verify(environment, times(2)).bind(eq("rcs_core"), eq(CoreConfig.class));

        // 3. 验证是否发布了 RcsCoreConfigRefreshEvent 事件
        verify(ctx, times(1)).publishEvent(any(RcsCoreConfigRefreshEvent.class));
    }
}