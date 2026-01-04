package com.ruinap.infra.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.ruinap.infra.config.pojo.TaskConfig;
import com.ruinap.infra.config.pojo.task.TaskCommonEntity;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsTaskConfigRefreshEvent;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:24
 */
@RunWith(MockitoJUnitRunner.class)
public class TaskYamlTest {

    @Mock
    private Environment environment;
    @Mock
    private ApplicationContext ctx;
    @InjectMocks
    private TaskYaml taskYaml;

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
    public void testTaskConfigData() {
        TaskConfig mockConfig = new TaskConfig();
        TaskCommonEntity common = new TaskCommonEntity();
        common.setTaskDistributeMode(99);
        mockConfig.setTaskCommon(common);

        when(environment.bind(eq("rcs_task"), eq(TaskConfig.class))).thenReturn(mockConfig);

        // 简单的 Mock 文件操作，防止 init 报错
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);

        taskYaml.initialize();

        Assert.assertEquals(Integer.valueOf(99), taskYaml.getTaskDistributeMode());
        // 验证空集合兜底
        Assert.assertNotNull(taskYaml.getTaskPiontAlias());
        Assert.assertTrue(taskYaml.getTaskPiontAlias().isEmpty());
    }

    @Test
    public void testCheckAndReload() {
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        when(environment.bind(anyString(), any())).thenReturn(new TaskConfig());

        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_1");
        taskYaml.initialize();

        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_2");
//        boolean changed = taskYaml.checkAndReload();

//        Assert.assertTrue(changed);
        verify(ctx, times(1)).publishEvent(any(RcsTaskConfigRefreshEvent.class));
    }
}