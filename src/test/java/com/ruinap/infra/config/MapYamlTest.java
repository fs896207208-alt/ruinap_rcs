package com.ruinap.infra.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.ruinap.infra.config.pojo.MapConfig;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsMapConfigRefreshEvent;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:18
 */
@RunWith(MockitoJUnitRunner.class)
public class MapYamlTest {

    @Mock
    private Environment environment;
    @Mock
    private ApplicationContext ctx;
    @InjectMocks
    private MapYaml mapYaml;

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
    public void testMapConfigBinding() {
        // --- 准备数据 ---
        MapConfig mockConfig = new MapConfig();
        LinkedHashMap<Integer, ArrayList<Integer>> chargePoints = new LinkedHashMap<>();
        chargePoints.put(1, new ArrayList<>());
        mockConfig.setChargePoint(chargePoints);

        // --- Mock ---
        when(environment.bind(eq("rcs_map"), eq(MapConfig.class))).thenReturn(mockConfig);
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);

        // --- 执行 ---
        mapYaml.initialize();

        // --- 验证 ---
        Assert.assertNotNull(mapYaml.getChargePoint());
        Assert.assertTrue(mapYaml.getChargePoint().containsKey(1));
        // 验证未设置的字段返回空集合（代码中的兜底逻辑）
        Assert.assertEquals(0, mapYaml.getStandbyPoint().size());
    }

    @Test
    public void testCheckAndReload() {
        // --- 模拟热更新流程 ---
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        when(environment.bind(eq("rcs_map"), eq(MapConfig.class))).thenReturn(new MapConfig());

        // 1. 初始化
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_A");
        mapYaml.initialize();

        // 2. 检查更新 (指纹变更为 md5_B)
        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_B");
//        boolean changed = mapYaml.checkAndReload();

        // 3. 验证
//        Assert.assertTrue(changed);
        verify(environment, times(1)).scanAndLoad(anyString());
        verify(ctx, times(1)).publishEvent(any(RcsMapConfigRefreshEvent.class));
    }
}