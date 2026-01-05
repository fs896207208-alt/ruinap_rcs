package com.ruinap.infra.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.ruinap.infra.config.pojo.TaskConfig;
import com.ruinap.infra.config.pojo.task.TaskCommonEntity;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsTaskConfigRefreshEvent;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:24
 */
@DisplayName("任务配置(TaskYaml)测试")
class TaskYamlTest {

    @Mock
    private Environment environment;
    @Mock
    private ApplicationContext ctx;
    @InjectMocks
    private TaskYaml taskYaml;

    private MockedStatic<FileUtil> fileUtilMock;
    private MockedStatic<SecureUtil> secureUtilMock;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    public void setUp() {
        // JUnit 6 初始化 Mock
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        // 初始化静态 Mock (完全保留原逻辑)
        fileUtilMock = mockStatic(FileUtil.class);
        secureUtilMock = mockStatic(SecureUtil.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // 关闭静态 Mock
        if (fileUtilMock != null) fileUtilMock.close();
        if (secureUtilMock != null) secureUtilMock.close();
        // 关闭 Mockito 资源
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    @DisplayName("测试：任务配置数据加载")
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

        // JUnit 6: assertEquals(expected, actual)
        Assertions.assertEquals(Integer.valueOf(99), taskYaml.getTaskDistributeMode());
        // 验证空集合兜底
        Assertions.assertNotNull(taskYaml.getTaskPiontAlias());
        Assertions.assertTrue(taskYaml.getTaskPiontAlias().isEmpty());
    }

    @Test
    @DisplayName("测试：检查更新与重载")
    public void testCheckAndReload() {
        File mockFile = mock(File.class);
        fileUtilMock.when(() -> FileUtil.newFile(anyString())).thenReturn(mockFile);
        when(mockFile.exists()).thenReturn(true);
        when(environment.bind(anyString(), any())).thenReturn(new TaskConfig());

        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_1");
        taskYaml.initialize();

        secureUtilMock.when(() -> SecureUtil.md5(mockFile)).thenReturn("md5_2");

        // 严格保留注释代码
//        boolean changed = taskYaml.checkAndReload();

//        Assertions.assertTrue(changed);
        verify(ctx, times(1)).publishEvent(any(RcsTaskConfigRefreshEvent.class));
    }
}