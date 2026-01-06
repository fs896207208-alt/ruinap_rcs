package com.ruinap.infra.config;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.infra.config.pojo.TaskConfig;
import com.ruinap.infra.config.pojo.task.ChargeCommonEntity;
import com.ruinap.infra.config.pojo.task.StandbyCommonEntity;
import com.ruinap.infra.config.pojo.task.TaskCommonEntity;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import org.junit.jupiter.api.*;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author qianye
 * @create 2025-12-24 17:24
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskYamlTest {

    private TaskYaml taskYaml;
    private Environment mockEnv;
    private ApplicationContext mockCtx;
    private TaskConfig mockConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 1. 初始化待测对象
        taskYaml = new TaskYaml();

        // 2. Mock 依赖
        mockEnv = mock(Environment.class);
        mockCtx = mock(ApplicationContext.class);

        // 3. Mock POJO (使用 DEEP_STUBS 处理链式调用)
        mockConfig = mock(TaskConfig.class, RETURNS_DEEP_STUBS);

        // 4. 注入依赖
        ReflectUtil.setFieldValue(taskYaml, "environment", mockEnv);
        ReflectUtil.setFieldValue(taskYaml, "ctx", mockCtx);
    }

    // ==========================================
    // 1. 生命周期测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("初始化流程")
    void testInitialize() {
        System.out.println("★ 1. 测试初始化");

        when(mockEnv.bind(eq("rcs_task"), eq(TaskConfig.class))).thenReturn(mockConfig);

        taskYaml.initialize();

        Object actualConfig = ReflectUtil.getFieldValue(taskYaml, "config");
        assertEquals(mockConfig, actualConfig);
        System.out.println("   [PASS] 初始化绑定成功");
    }

    @Test
    @Order(2)
    @DisplayName("热重载 (Rebind)")
    void testRebind() {
        System.out.println("★ 2. 测试热重载");

        TaskConfig newConfig = mock(TaskConfig.class);
        when(mockEnv.bind(eq("rcs_task"), eq(TaskConfig.class))).thenReturn(newConfig);

        taskYaml.rebind();

        Object actualConfig = ReflectUtil.getFieldValue(taskYaml, "config");
        assertEquals(newConfig, actualConfig);
        System.out.println("   [PASS] 热重载更新成功");
    }

    // ==========================================
    // 2. 核心 Getter 测试 (Happy Path)
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("普通参数读取")
    void testGetters_HappyPath() {
        System.out.println("★ 3. 测试普通参数读取");

        // 注入 Config
        ReflectUtil.setFieldValue(taskYaml, "config", mockConfig);

        // 1. 任务分配模式 (TaskDistributeMode)
        when(mockConfig.getTaskCommon().getTaskDistributeMode()).thenReturn(1);
        assertEquals(1, taskYaml.getTaskDistributeMode());

        // 2. 点位别名 (TaskPiontAlias)
        // [修复] 使用 LinkedHashMap 以匹配 POJO 定义
        LinkedHashMap<String, String> aliasMap = new LinkedHashMap<>();
        aliasMap.put("P1", "Home");
        when(mockConfig.getRegexCommon().getTaskPointAlias()).thenReturn(aliasMap);
        assertEquals("Home", taskYaml.getTaskPiontAlias().get("P1"));

        // 3. 动作参数 (Origin/Destin ActionParameter)
        // [修复] 使用 LinkedHashMap
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("lift", "up");
        when(mockConfig.getRegexCommon().getOriginActionParameter()).thenReturn(params);
        assertEquals("up", taskYaml.getOriginActionParameter().get("lift"));
    }

    @Test
    @Order(4)
    @DisplayName("实体对象读取")
    void testGetEntities() {
        System.out.println("★ 4. 测试实体对象读取");
        ReflectUtil.setFieldValue(taskYaml, "config", mockConfig);

        // 模拟实体对象
        ChargeCommonEntity mockCharge = mock(ChargeCommonEntity.class);
        StandbyCommonEntity mockStandby = mock(StandbyCommonEntity.class);
        TaskCommonEntity mockTask = mock(TaskCommonEntity.class);

        when(mockConfig.getChargeCommon()).thenReturn(mockCharge);
        when(mockConfig.getStandbyCommon()).thenReturn(mockStandby);
        when(mockConfig.getTaskCommon()).thenReturn(mockTask);

        assertSame(mockCharge, taskYaml.getChargeCommon());
        assertSame(mockStandby, taskYaml.getStandbyCommon());
        assertSame(mockTask, taskYaml.getTaskCommon());
    }

    // ==========================================
    // 3. 健壮性测试 (Null Safety)
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("空指针防御 (针对带保护的方法)")
    void testNullSafety_ProtectedMethods() {
        System.out.println("★ 5. 测试空指针防御");

        // 场景 A: 全局 config 为 null
        ReflectUtil.setFieldValue(taskYaml, "config", null);

        assertEquals(0, taskYaml.getTaskDistributeMode());
        assertTrue(taskYaml.getTaskPiontAlias().isEmpty());
        assertTrue(taskYaml.getOriginActionParameter().isEmpty());
        assertTrue(taskYaml.getDestinActionParameter().isEmpty());

        // 场景 B: config 存在，但子对象 (RegexCommon/TaskCommon) 为 null
        ReflectUtil.setFieldValue(taskYaml, "config", mockConfig);
        when(mockConfig.getRegexCommon()).thenReturn(null);
        when(mockConfig.getTaskCommon()).thenReturn(null);

        assertEquals(0, taskYaml.getTaskDistributeMode());
        assertTrue(taskYaml.getTaskPiontAlias().isEmpty());

        // 场景 C: 子对象存在，但 Map 本身为 null
        // 重新 mock 一个 deep stub 对象来模拟这种情况
        TaskConfig stubConfig = mock(TaskConfig.class, RETURNS_DEEP_STUBS);
        ReflectUtil.setFieldValue(taskYaml, "config", stubConfig);

        when(stubConfig.getRegexCommon().getOriginActionParameter()).thenReturn(null);
        assertNotNull(taskYaml.getOriginActionParameter());
        assertTrue(taskYaml.getOriginActionParameter().isEmpty());

        System.out.println("   [PASS] 健壮性测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("验证未保护方法的空指针风险")
    void testUnprotectedMethods_NPE() {
        System.out.println("★ 6. 验证未保护方法的行为");

        ReflectUtil.setFieldValue(taskYaml, "config", null);

        assertThrows(NullPointerException.class, () -> taskYaml.getChargeCommon());
        assertThrows(NullPointerException.class, () -> taskYaml.getStandbyCommon());

        System.out.println("   [INFO] 确认 getChargeCommon 等方法在配置未加载时会抛出 NPE (符合预期)");
    }
}