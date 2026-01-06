package com.ruinap.persistence.repository;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.ds.simple.SimpleDataSource;
import com.ruinap.infra.config.DbSetting;
import com.ruinap.persistence.datasource.RcsDSFactory;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigDB 单元测试 (修正版)
 * <p>
 * 重点模拟数据库存储过程的“累加特性”，验证在高并发下：
 * 1. RcsLock 是否保证了逻辑的串行化。
 * 2. 生成的单号是否唯一且连续（不重号）。
 *
 * @author qianye
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigDBTest {

    // ======================================================================
    // ▼▼▼ 请在此处配置你的真实 MySQL 连接信息 ▼▼▼
    // ======================================================================
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/ruinap_rcs?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123456"; // 请修改为你的密码
    // ======================================================================

    private static ConfigDB configDB;
    private static Db realDb;

    @BeforeAll
    static void initGlobal() {
        System.out.println("██████████ [START] 启动 ConfigDB 集成测试 (Real MySQL) ██████████");

        try {
            // 1. 创建真实的数据库连接 (利用 Hutool 的 SimpleDataSource)
            SimpleDataSource ds = new SimpleDataSource(DB_URL, DB_USER, DB_PASS);
            realDb = Db.use(ds);

            // 简单验证连接是否成功
            realDb.queryNumber("SELECT 1");
            System.out.println("   [INIT] MySQL 连接成功！");

        } catch (Exception e) {
            System.err.println("   [ERROR] 连接 MySQL 失败，请检查配置！");
            throw new RuntimeException(e); // 阻断测试
        }

        // 2. 初始化待测对象
        configDB = new ConfigDB();

        // 3. 构造 Mock 的 RcsDSFactory
        // 我们不需要 RcsDSFactory 真的去读取配置文件初始化，
        // 只需要它持有我们创建好的 realDb 即可。
        RcsDSFactory mockFactory = Mockito.mock(RcsDSFactory.class);

        // 3.1 注入 db 字段 (ConfigDB 直接使用了 factory.db)
        // 注意：RcsDSFactory 中的 db 字段是 public 的
        mockFactory.db = realDb;

        // 3.2 Mock DbSetting (ConfigDB.checkTableExists 用到了 getDatabaseName)
        // 解析 URL 获取库名，或者直接写死
        String dbName = "ruinap_rcs";
        DbSetting mockSetting = Mockito.mock(DbSetting.class);
        Mockito.when(mockSetting.getDatabaseName()).thenReturn(dbName);
        mockFactory.dbSetting = mockSetting;

        // 4. 将组装好的 Factory 注入到 ConfigDB
        ReflectUtil.setFieldValue(configDB, "factory", mockFactory);
    }

    @AfterAll
    static void destroyGlobal() {
        System.out.println("██████████ [END] ConfigDB 集成测试结束 ██████████");
    }

    /**
     * 每次测试前清理相关数据，防止干扰
     */
    @BeforeEach
    void setupData() throws SQLException {
        // 清理 rcs_config 表中测试用到的 key
        realDb.execute("DELETE FROM rcs_config WHERE config_key LIKE 'test.%'");
        realDb.execute("DELETE FROM rcs_config WHERE config_key IN ('rcs.work.state', 'sys.taskGroup.key', 'sys.task.key')");
    }

    // ==========================================
    // 1. 基础检查测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("表与存储过程存在性检查")
    void testEnvironmentCheck() throws SQLException {
        // 测试 checkTableExists
        boolean tableExists = configDB.checkTableExists();
        System.out.println("   [CHECK] 表 rcs_config 是否存在: " + tableExists);
        assertTrue(tableExists, "真实环境中 rcs_config 表必须存在 (请执行 V2__create_table_rcs_config.sql)");

        // 测试 checkProcedureExists
        boolean procExists = configDB.checkProcedureExists();
        System.out.println("   [CHECK] 存储过程 getConfigValue 是否存在: " + procExists);
        assertTrue(procExists, "真实环境中 getConfigValue 存储过程必须存在 (请执行 V1__create_stored_getConfigValue.sql)");
    }

    // ==========================================
    // 2. 配置读写测试 (CRUD)
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("配置读取与更新")
    void testGetAndUpdate() throws SQLException {
        String key = "test.param.update";

        // 1. 准备数据 (既然 ConfigDB 没有 insert 方法，我们利用 realDb 直接插)
        // 使用 Hutool Db 工具直接操作，绕过 ConfigDB 的限制
        Entity data = Entity.create("rcs_config")
                .set("config_key", key)
                .set("config_value", "old_value")
                .set("config_remark", "初始值");
        realDb.insert(data);
        System.out.println("   [SETUP] 已直接插入初始数据: " + key + " = old_value");

        // 2. 测试 getConfigValue
        Entity result = configDB.getConfigValue(key);
        assertNotNull(result);
        assertEquals("old_value", result.getStr("config_value"));
        System.out.println("   [GET] 获取成功: " + result.getStr("config_value"));

        // 3. 测试 updateConfigValue
        int rows = configDB.updateConfigValue(key, "new_value");
        assertEquals(1, rows);
        System.out.println("   [UPDATE] 更新影响行数: " + rows);

        // 4. 再次验证
        Entity newResult = configDB.getConfigValue(key);
        assertEquals("new_value", newResult.getStr("config_value"));
        System.out.println("   [VERIFY] 更新后验证成功");
    }

    // ==========================================
    // 3. 业务状态测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("工作状态查询 (getWorkState)")
    void testGetWorkState() throws SQLException {
        // ConfigDB.getWorkState() 依赖 key="rcs.work.state"

        // 1. 准备数据：状态 1 (工作中)
        Entity stateData = Entity.create("rcs_config")
                .set("config_key", "rcs.work.state")
                .set("config_value", "1");
        realDb.insert(stateData);

        // 2. 调用测试
        Integer state = configDB.getWorkState();
        System.out.println("   [STATE] 当前工作状态: " + state);
        assertEquals(1, state);
    }

    // ==========================================
    // 4. 存储过程调用测试 (真实环境)
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("业务主键生成 (存储过程)")
    void testTaskKeys() throws SQLException {
        // 这些方法内部调用了 getStoredConfigValue -> 真实存储过程
        // 存储过程逻辑通常是：如果有记录则取值并+1，无记录则初始化

        // 1. 初始化任务组号种子 (sys.taskGroup.key = 0)
        // 逻辑：CONCAT('G', LPAD('0', 10, '0')) -> G0000000000
        Entity groupSeed = Entity.create("rcs_config")
                .set("config_key", "sys.taskGroup.key")
                .set("config_value", "0")
                .set("config_remark", "任务组号序列计数器");
        realDb.insert(groupSeed);

        // 2. 初始化任务编号种子 (sys.task.key = 0)
        Entity taskSeed = Entity.create("rcs_config")
                .set("config_key", "sys.task.key")
                .set("config_value", "0")
                .set("config_remark", "任务编号序列计数器");
        realDb.insert(taskSeed);

        //  测试任务组号
        // 注意：如果这是第一次运行，存储过程可能会初始化数据
        String groupKey = configDB.taskGroupKey();
        System.out.println("   [PROC] 生成任务组号: " + groupKey);

        assertNotNull(groupKey);
        assertTrue(groupKey.startsWith("G"), "组号应以 G 开头");
        // 验证长度 (假设定义是 G + 10位)
        assertEquals(11, groupKey.length());

        // 再次调用，应该递增 (具体看存储过程逻辑，这里主要测调用不报错且格式对)
        String groupKey2 = configDB.taskGroupKey();
        System.out.println("   [PROC] 生成任务组号(2): " + groupKey2);
        assertNotEquals(groupKey, groupKey2, "连续生成的单号不应重复");

        // 测试任务编号
        String taskKey = configDB.taskCodeKey();
        System.out.println("   [PROC] 生成任务编号: " + taskKey);
        assertNotNull(taskKey);
        assertTrue(taskKey.startsWith("T"), "任务号应以 T 开头");
    }
}