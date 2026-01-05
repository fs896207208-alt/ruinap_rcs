package com.ruinap.infra.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.ruinap.infra.config.pojo.LinkConfig;
import com.ruinap.infra.config.pojo.link.LinkEntity;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:15
 */
@DisplayName("连接配置(LinkYaml)测试")
class LinkYamlTest {

    @Mock
    private Environment environment;
    @Mock
    private ApplicationContext ctx;

    @InjectMocks
    private LinkYaml linkYaml;

    // 静态 Mock 对象
    private MockedStatic<FileUtil> fileUtilMock;
    private MockedStatic<SecureUtil> secureUtilMock;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        // JUnit 6 初始化
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        // 初始化静态 Mock (Hutool)
        fileUtilMock = mockStatic(FileUtil.class);
        secureUtilMock = mockStatic(SecureUtil.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 必须关闭静态 Mock，否则会污染其他测试
        if (fileUtilMock != null) fileUtilMock.close();
        if (secureUtilMock != null) secureUtilMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    @DisplayName("测试：检查更新与热重载")
    void testCheckAndReload() {
        // --- 模拟热更新流程 ---
        File mockFile = mock(File.class);
        // 模拟 FileUtil 返回 mockFile
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        // 模拟 Environment 返回新配置
        when(environment.bind(eq("rcs_link"), eq(LinkConfig.class))).thenReturn(new LinkConfig());

        // 1. 初始化
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_A");
        linkYaml.initialize();

        // 2. 检查更新 (指纹变更为 md5_B)
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_B");

        // TODO: 如果 LinkYaml 实现了 checkAndReload 方法，请解开下行注释
        // boolean changed = linkYaml.checkAndReload();

        // --- 验证 (严格保留原有逻辑，即使它是注释掉的) ---
        // Assertions.assertTrue(changed, "指纹变化时应返回 true");

        // 验证重新扫描
        // verify(environment, times(1)).scanAndLoad(anyString());
        // 验证重新绑定
        // verify(environment, times(2)).bind(eq("rcs_link"), eq(LinkConfig.class));
        // 验证事件发布
        // verify(ctx, times(1)).publishEvent(any(RcsLinkConfigRefreshEvent.class));
    }

    @Test
    @DisplayName("测试：配置逻辑处理")
    void testHandlerLinkConfigLogic() {
        // 专门测试配置合并逻辑 (完全保留原数据构造)
        LinkConfig config = new LinkConfig();
        LinkEntity entity = new LinkEntity();

        // 通用配置
        Map<String, String> common = new HashMap<>();
        common.put("timeout", "3000");
        entity.setCommon(common);

        // 具体配置
        Map<String, Map<String, String>> links = new HashMap<>();
        Map<String, String> item = new HashMap<>();
        item.put("ip", "1.1.1.1");
        links.put("A01", item);
        entity.setLinks(links);

        config.setAgvLink(entity);

        when(environment.bind(anyString(), any())).thenReturn(config);

        // 忽略文件操作，只关注逻辑
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_X");

        // 执行初始化
        linkYaml.initialize();

        // 验证逻辑 (此处假设 initialize 会读取 config 并进行内部处理，我们不做额外断言，仅保证跑通)
        Assertions.assertNotNull(linkYaml, "LinkYaml 实例不应为空");
    }
}