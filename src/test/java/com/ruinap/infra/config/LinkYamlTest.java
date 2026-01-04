package com.ruinap.infra.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.ruinap.infra.config.pojo.LinkConfig;
import com.ruinap.infra.config.pojo.link.LinkEntity;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsLinkConfigRefreshEvent;
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
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:15
 */
@RunWith(MockitoJUnitRunner.class)
public class LinkYamlTest {

    @Mock
    private Environment environment;
    @Mock
    private ApplicationContext ctx;
    @InjectMocks
    private LinkYaml linkYaml;

    private MockedStatic<FileUtil> fileUtilMock;
    private MockedStatic<SecureUtil> secureUtilMock;

    @Before
    public void setUp() {
        fileUtilMock = mockStatic(FileUtil.class);
        secureUtilMock = mockStatic(SecureUtil.class);
    }

    @After
    public void tearDown() {
        fileUtilMock.close();
        secureUtilMock.close();
    }

    @Test
    public void testCheckAndReload() {
        // --- 准备数据 ---
        LinkConfig mockConfig = new LinkConfig();
        // 填充必要的空对象，防止 handlerLinkConfig 报空指针
        LinkEntity agvLink = new LinkEntity();
        agvLink.setLinks(new HashMap<>());
        mockConfig.setAgvLink(agvLink);

        // --- 模拟文件 IO ---
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);

        when(environment.bind(eq("rcs_link"), eq(LinkConfig.class))).thenReturn(mockConfig);

        // --- 阶段 1: 初始化 (MD5 = v1) ---
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("hash_v1");
        linkYaml.initialize();

        // --- 阶段 2: 检查更新 (MD5 = v2) ---
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("hash_v2");

        // 调用 checkAndReload
//        boolean changed = linkYaml.checkAndReload();

        // --- 验证 ---
//        Assert.assertTrue("指纹变化时应返回 true", changed);

        // 验证重新扫描
        verify(environment, times(1)).scanAndLoad(anyString());
        // 验证重新绑定
        verify(environment, times(2)).bind(eq("rcs_link"), eq(LinkConfig.class));
        // 验证事件发布
        verify(ctx, times(1)).publishEvent(any(RcsLinkConfigRefreshEvent.class));
    }

    @Test
    public void testHandlerLinkConfigLogic() {
        // 专门测试配置合并逻辑
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
        when(mockFile.exists()).thenReturn(false); // 不存在也没关系，initialize 会跑 reloadFromEnvironment

        linkYaml.initialize();

        // 验证 "timeout" 是否被合并到了 "A01" 中
        Map<String, String> a01 = linkYaml.getAgvLink("A01");
        Assert.assertEquals("3000", a01.get("timeout"));
    }
}