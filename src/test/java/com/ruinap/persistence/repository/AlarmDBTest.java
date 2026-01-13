package com.ruinap.persistence.repository;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.sql.Condition;
import com.ruinap.infra.config.DbSetting;
import com.ruinap.persistence.factory.RcsDSFactory;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmDB 真实环境集成测试 (MySQL版)
 * <p>
 * 覆盖：
 * 1. 告警信息的写入 (createAlarm)
 * 2. 多维度查询 (Entity条件, Condition复杂条件, 时间范围)
 * 3. 表存在性检查
 * </p>
 *
 * @author qianye
 * @create 2026-01-06 13:50
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlarmDBTest {

    // ======================================================================
    // ▼▼▼ 请在此处配置你的真实 MySQL 连接信息 ▼▼▼
    // ======================================================================
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/ruinap_rcs?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123456"; // 请修改为你的密码
    // ======================================================================

    private static AlarmDB alarmDB;
    private static Db realDb;

    @BeforeAll
    static void initGlobal() {
        System.out.println("██████████ [START] 启动 AlarmDB 集成测试 (Real MySQL) ██████████");

        try {
            // 1. 创建真实的数据库连接
            SimpleDataSource ds = new SimpleDataSource(DB_URL, DB_USER, DB_PASS);
            realDb = Db.use(ds);

            // 简单验证
            realDb.queryNumber("SELECT 1");
            System.out.println("   [INIT] MySQL 连接成功！");

        } catch (Exception e) {
            System.err.println("   [ERROR] 连接 MySQL 失败，请检查配置！");
            throw new RuntimeException(e);
        }

        // 2. 初始化待测对象
        alarmDB = new AlarmDB();

        // 3. 构造 Mock 的 RcsDSFactory
        RcsDSFactory mockFactory = Mockito.mock(RcsDSFactory.class);
        mockFactory.db = realDb;

        // Mock DbSetting
        DbSetting mockSetting = Mockito.mock(DbSetting.class);
        try {
            String currentDb = realDb.queryOne("SELECT DATABASE()").getStr("DATABASE()");
            Mockito.when(mockSetting.getDatabaseName()).thenReturn(currentDb);
        } catch (SQLException e) {
            Mockito.when(mockSetting.getDatabaseName()).thenReturn("ruinap_rcs");
        }
        mockFactory.dbSetting = mockSetting;

        // 4. 注入依赖
        ReflectUtil.setFieldValue(alarmDB, "factory", mockFactory);
    }

    @AfterAll
    static void destroyGlobal() {
        System.out.println("██████████ [END] AlarmDB 集成测试结束 ██████████");
    }

    @BeforeEach
    void cleanData() throws SQLException {
        // [修复] 使用正确的字段 'source' 进行清理
        // 假设我们在测试中使用 'TEST-AGV-01' 作为 source (告警来源/设备编码)
        realDb.execute("DELETE FROM rcs_alarm WHERE source = 'TEST-AGV-01'");
    }

    // ==========================================
    // 1. 基础检查
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("表存在性检查")
    void testCheckTableExists() throws SQLException {
        boolean exists = alarmDB.checkTableExists();
        System.out.println("   [CHECK] 表 rcs_alarm 是否存在: " + exists);
        assertTrue(exists);
    }

    // ==========================================
    // 2. 插入与查询测试
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("告警创建与基础查询")
    void testCreateAndQuery() throws SQLException {
        // 1. 创建告警 (严格对应 V3 SQL 结构)
        Entity alarm = Entity.create("rcs_alarm")
                .set("source", "TEST-AGV-01")   // 对应 device_code
                .set("code", 1001)              // 对应 alarm_code (改为 int)
                .set("msg", "测试电池低电量")     // 对应 alarm_msg
                .set("level", 1)                // 对应 alarm_level
                .set("type", 0)                 // 0: AGV
                .set("state", 0)                // 0: 新告警
                .set("create_time", DateTime.now());

        Integer rows = alarmDB.createAlarm(alarm);
        System.out.println("   [CREATE] 创建告警影响行数: " + rows);
        assertEquals(1, rows);

        // 2. 使用 Entity 条件查询
        // [修复] 查询条件字段名修正
        Entity where = Entity.create().set("source", "TEST-AGV-01").set("code", 1001);
        List<Entity> list = alarmDB.queryAlarmList(where);

        System.out.println("   [QUERY] 查询到的条数: " + list.size());
        assertEquals(1, list.size());
        assertEquals("测试电池低电量", list.getFirst().getStr("msg")); // 修正字段名
    }

    @Test
    @Order(3)
    @DisplayName("复杂条件查询 (Condition)")
    void testQueryWithCondition() throws SQLException {
        // 准备数据
        createTestAlarm("TEST-AGV-01", 2001, 2); // Level 2 (异常)
        createTestAlarm("TEST-AGV-01", 1002, 1); // Level 1 (警告)

        // 查询 Level >= 2 的告警
        // [修复] 字段名修正
        List<Entity> list = alarmDB.queryAlarmList(
                new Condition("source", "=", "TEST-AGV-01"),
                new Condition("level", ">=", 2)
        );

        System.out.println("   [QUERY-COND] Level >= 2 的告警数: " + list.size());
        assertEquals(1, list.size());
        assertEquals(2001, list.getFirst().getInt("code"));
    }

    // ==========================================
    // 3. 时间范围查询测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("时间范围查询")
    void testQueryByTimeRange() throws SQLException {
        // 1. 准备不同时间段的数据
        DateTime yesterday = DateUtil.yesterday();
        createTestAlarm("TEST-AGV-01", 9001, 1, yesterday);

        DateTime now = DateTime.now();
        createTestAlarm("TEST-AGV-01", 9002, 1, now);

        // 2. 查询今天的告警
        DateTime start = DateUtil.beginOfDay(now);
        DateTime end = DateUtil.endOfDay(now);

        List<Entity> list = alarmDB.queryAlarmList(start, end);

        System.out.println("   [QUERY-TIME] 查询范围: " + start + " ~ " + end);
        System.out.println("   [QUERY-TIME] 命中数量: " + list.size());

        // 验证结果
        boolean hasNew = list.stream().anyMatch(e -> e.getInt("code") == 9002);
        boolean hasOld = list.stream().anyMatch(e -> e.getInt("code") == 9001);

        assertTrue(hasNew, "应该包含今天的告警 (code=9002)");
        assertFalse(hasOld, "不应包含昨天的告警 (code=9001)");
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private void createTestAlarm(String source, int code, int level) throws SQLException {
        createTestAlarm(source, code, level, DateTime.now());
    }

    private void createTestAlarm(String source, int code, int level, DateTime createTime) throws SQLException {
        // [修复] 辅助方法也需要对应新字段结构
        Entity alarm = Entity.create("rcs_alarm")
                .set("source", source)
                .set("code", code)
                .set("msg", "AutoTest")
                .set("level", level)
                .set("create_time", createTime);
        realDb.insert(alarm);
    }
}