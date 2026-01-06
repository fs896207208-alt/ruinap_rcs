package com.ruinap.persistence.repository;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.PageResult;
import cn.hutool.db.sql.Condition;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommonDB 全面测试用例
 * 针对表: rcs_task
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BaseDaoTest extends BaseDao {

    private static DataSource dataSource;
    private static Db db;

    @BeforeAll
    static void initGlobal() throws SQLException {
        System.out.println("██████████ [START] 启动 BaseDao 数据库测试环境 (H2) ██████████");

        // 1. 初始化 H2 内存数据库
        JdbcDataSource h2Ds = new JdbcDataSource();
        h2Ds.setURL("jdbc:h2:mem:test_rcs_db;MODE=MySQL;DB_CLOSE_DELAY=-1");
        h2Ds.setUser("sa");
        h2Ds.setPassword("");
        dataSource = h2Ds;

        // 2. 初始化 Hutool Db 对象
        db = Db.use(dataSource);

        // 3. 初始化表结构
        initTable();
    }

    @AfterAll
    static void destroyGlobal() {
        System.out.println("██████████ [END] 销毁 BaseDao 测试环境 ██████████");
    }

    @BeforeEach
    void cleanData() throws SQLException {
        db.execute("TRUNCATE TABLE t_user");
    }

    // ==========================================
    // 1. 基础元数据与表结构测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("检查表是否存在")
    void testCheckTableExists() throws SQLException {
        printTitle("1. 元数据检测");

        // 直接调用父类 protected 方法
        boolean exists = checkTableExists(db, "PUBLIC", "T_USER");
        System.out.println("   [CHECK] 表 T_USER 是否存在: " + exists);

        boolean notExists = checkTableExists(db, "PUBLIC", "T_NOT_EXIST");
        System.out.println("   [CHECK] 表 T_NOT_EXIST 是否存在: " + notExists);
        assertFalse(notExists);
    }

    // ==========================================
    // 2. 插入 (Insert) 测试
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("插入数据 (普通/批量/主键返回)")
    void testInsert() throws SQLException {
        printTitle("2. 插入测试 (Insert)");

        // 1. 普通插入
        Entity user1 = Entity.create("t_user").set("name", "Alice").set("age", 25);
        int rows = insert(db, "t_user", user1); // 直接调用父类方法
        System.out.println("   [INSERT] 插入 Alice, 影响行数: " + rows);
        assertEquals(1, rows);

        // 2. 插入并返回主键
        Entity user2 = Entity.create("t_user").set("name", "Bob").set("age", 30);
        Integer id = insertForPrimaryKey(db, "t_user", user2);
        System.out.println("   [INSERT] 插入 Bob, 返回主键 ID: " + id);
        assertNotNull(id);
        assertTrue(id > 0);

        // 3. 批量插入
        List<Entity> batchList = ListUtil.of(
                Entity.create("t_user").set("name", "Charlie").set("age", 20),
                Entity.create("t_user").set("name", "David").set("age", 22)
        );
        int[] batchRows = insertBatch(db, "t_user", batchList);
        System.out.println("   [INSERT] 批量插入 2 条数据, 结果数组长度: " + batchRows.length);
        assertEquals(2, batchRows.length);

        // 验证总数
        long total = count(db, "t_user", Entity.create());
        assertEquals(4, total);
    }

    // ==========================================
    // 3. 查询 (Select) 测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("查询测试 (Bean映射/条件查询)")
    void testSelect() throws SQLException {
        printTitle("3. 查询测试 (Select)");
        insert(db, "t_user", Entity.create("t_user").set("name", "Eva").set("age", 18));
        insert(db, "t_user", Entity.create("t_user").set("name", "Frank").set("age", 35));

        // 1. select (Entity)
        Entity entity = select(db, "t_user", Entity.create().set("name", "Eva"));
        System.out.println("   [SELECT] Entity 查询结果: " + entity);
        assertNotNull(entity);
        assertEquals(18, entity.getInt("age"));

        // 2. selectBean (POJO)
        String sql = "SELECT * FROM t_user WHERE name = ?";
        TestUser user = selectBean(db, sql, TestUser.class, "Frank");
        System.out.println("   [SELECT] Bean 查询结果: " + user);
        assertNotNull(user);
        assertEquals("Frank", user.getName());
        assertEquals(35, user.getAge());

        // 3. selectList (Condition)
        List<Entity> list = selectList(db, "t_user", new Condition("age", ">", 20));
        System.out.println("   [SELECT] Condition (>20) 结果数量: " + list.size());
        assertEquals(1, list.size());
    }

    // ==========================================
    // 4. 更新与删除 (Update/Delete) 测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("更新与删除测试")
    void testUpdateDelete() throws SQLException {
        printTitle("4. 更新与删除 (Update/Delete)");
        insert(db, "t_user", Entity.create("t_user").set("name", "George").set("age", 50));

        Entity where = Entity.create().set("name", "George");

        // 1. Update
        int updateRows = update(db, "t_user", Entity.create().set("age", 51), where);
        System.out.println("   [UPDATE] 更新 George 年龄, 影响行数: " + updateRows);
        assertEquals(1, updateRows);

        Entity afterUpdate = select(db, "t_user", where);
        assertEquals(51, afterUpdate.getInt("age"));

        // 2. Delete
        int delRows = delete(db, "t_user", where);
        System.out.println("   [DELETE] 删除 George, 影响行数: " + delRows);
        assertEquals(1, delRows);

        long count = count(db, "t_user", where);
        assertEquals(0, count);
    }

    // ==========================================
    // 5. 分页 (Page) 测试
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("分页查询测试")
    void testPage() throws SQLException {
        printTitle("5. 分页测试 (Page)");
        for (int i = 1; i <= 10; i++) {
            insert(db, "t_user", Entity.create("t_user").set("name", "User" + i).set("age", 20 + i));
        }

        PageResult<TestUser> pageResult = selectPageBean(db, "t_user", 2, 3, Entity.create(), TestUser.class);

        System.out.println("   [PAGE] 总页数: " + pageResult.getTotalPage());
        System.out.println("   [PAGE] 当前页数据: " + pageResult);

        assertEquals(10, pageResult.getTotal());
        assertEquals(3, pageResult.size());
    }

    // ==========================================
    // 6. 事务 (Transaction) 测试
    // ==========================================

    @Test
    @Order(6)
    @DisplayName("事务回滚测试")
    void testTransaction() throws SQLException {
        printTitle("6. 事务回滚测试 (Transaction)");

        // 1. 记录初始数据量
        long countBefore = count(db, "t_user", Entity.create());

        try {
            // 使用父类 tx 方法开启事务
            tx(db, (transactionalDb) -> {
                try {
                    // 2. 事务内插入数据
                    insert(transactionalDb, "t_user", Entity.create("t_user").set("name", "TxUser").set("age", 100));
                    System.out.println("   [TX] 事务内已插入 TxUser");

                    // 3. 模拟抛出异常，触发回滚
                    System.out.println("   [TX] 模拟抛出异常...");
                    throw new RuntimeException("Rollback Exception");
                } catch (SQLException e) {
                    // 将 checked 异常转为 runtime 抛出，确保触发回滚
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) { // <--- [关键修改] 这里改为捕获 Exception 或 SQLException
            // Hutool 会把事务内的 RuntimeException 包装成 SQLException 抛出
            System.out.println("   [MAIN] 捕获到预期异常: " + e.getClass().getSimpleName() + " -> " + e.getMessage());
        }

        // 4. 验证数据是否回滚 (TxUser 应该不存在)
        long countAfter = count(db, "t_user", Entity.create());
        System.out.println("   [CHECK] 事务后记录总数: " + countAfter);

        assertEquals(countBefore, countAfter, "事务回滚失败，数据被提交了！");
    }

    // ==========================================
    // 辅助工具
    // ==========================================

    private static void initTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS t_user (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50),
                    age INT
                )
                """;
        db.execute(sql);
        System.out.println("   [INIT] 初始化 H2 表结构 t_user 完成");
    }

    private void printTitle(String title) {
        System.out.println("\n--------------------------------------------------");
        System.out.println("★ " + title);
        System.out.println("--------------------------------------------------");
    }

    @Data
    @NoArgsConstructor
    public static class TestUser {
        private Integer id;
        private String name;
        private Integer age;

        @Override
        public String toString() {
            return "TestUser(id=" + id + ", name=" + name + ", age=" + age + ")";
        }
    }
}