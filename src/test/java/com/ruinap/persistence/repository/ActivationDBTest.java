package com.ruinap.persistence.repository;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import com.ruinap.persistence.factory.RcsDSFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ActivationDB 集成测试 (H2环境)
 * <p>
 * 特性：
 * 1. 真实 H2 环境：ActivationDB 设计为使用 H2，因此测试也使用 H2。
 * 2. 全流程覆盖：建表 -> 检查 -> 插入 -> 查询。
 * </p>
 *
 * @author qianye
 * @create 2026-01-06 14:01
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ActivationDBTest {

    private static ActivationDB activationDB;
    private static Db h2Db;

    @BeforeAll
    static void initGlobal() {
        System.out.println("██████████ [START] 启动 ActivationDB 集成测试 (H2) ██████████");

        // 1. 初始化 H2 内存数据库
        // 使用 MySQL 模式兼容，因为 BaseDao 可能有一些通用 SQL 习惯，但 createTable 里主要是标准 SQL
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test_activation_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        h2Db = Db.use(ds);

        // 2. 初始化待测对象
        activationDB = new ActivationDB();

        // 3. Mock RcsDSFactory
        // ActivationDB 使用的是 factory.h2Db
        RcsDSFactory mockFactory = Mockito.mock(RcsDSFactory.class);
        mockFactory.h2Db = h2Db; // 注入我们的测试 DB

        // 4. 注入依赖
        ReflectUtil.setFieldValue(activationDB, "factory", mockFactory);
    }

    @AfterAll
    static void destroyGlobal() {
        System.out.println("██████████ [END] ActivationDB 集成测试结束 ██████████");
    }

    @BeforeEach
    void cleanEnv() throws SQLException {
        // 每次测试前，为了纯净，我们可以选择删表
        // 但由于 createTable 方法本身包含 DROP IF EXISTS，我们在测试 createTable 时会用到
        // 这里暂时留空，由各个测试方法自己控制节奏
    }

    // ==========================================
    // 1. 建表与存在性检查测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("建表与检查 (Create & Check)")
    void testCreateTable() throws SQLException {
        // 1. 初始状态：表应该不存在 (内存库刚启动)
        // 注意：checkTableExists 内部是 checkTableExists(h2Db, "PUBLIC", TABLE_NAME)
        // H2 默认 Schema 是 PUBLIC
        boolean existsBefore = activationDB.checkTableExists();
        System.out.println("   [CHECK] 建表前是否存在: " + existsBefore);
        // 如果是第一次跑，肯定不存在；如果之前的测试没清理，可能存在。
        // 为了严谨，我们先手动删一次确保它是 false
        h2Db.execute("DROP TABLE IF EXISTS RCS_ACTIVATION");
        assertFalse(activationDB.checkTableExists(), "初始化时表不应存在");

        // 2. 执行建表
        int result = activationDB.createTable();
        System.out.println("   [CREATE] 建表结果: " + result);

        // 3. 再次检查
        boolean existsAfter = activationDB.checkTableExists();
        System.out.println("   [CHECK] 建表后是否存在: " + existsAfter);
        assertTrue(existsAfter, "执行 createTable 后表必须存在");
    }

    // ==========================================
    // 2. 数据读写测试
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("激活码写入与查询 (Insert & Query)")
    void testInsertAndQuery() throws SQLException {
        // 确保表存在
        if (!activationDB.checkTableExists()) {
            activationDB.createTable();
        }

        // 1. 准备数据
        String machineCode = "M-112233";
        String activationCode = "ACT-888888";
        String secureCode = "SEC-999";

        Entity entity = Entity.create("RCS_ACTIVATION")
                .set("machine_code", machineCode)
                .set("activation_code", activationCode)
                .set("secure_code", secureCode)
                .set("expired_date", DateUtil.parse("2099-12-31"));

        // 2. 插入
        Integer rows = activationDB.insert(entity);
        System.out.println("   [INSERT] 插入影响行数: " + rows);
        assertEquals(1, rows);

        // 3. 查询
        Entity where = Entity.create().set("machine_code", machineCode);
        List<Entity> list = activationDB.query(where);

        System.out.println("   [QUERY] 查询结果条数: " + list.size());
        assertEquals(1, list.size());

        Entity result = list.get(0);
        assertEquals(activationCode, result.getStr("activation_code"));
        assertEquals(secureCode, result.getStr("secure_code"));
        System.out.println("   [VERIFY] 数据验证通过: " + result);
    }
}