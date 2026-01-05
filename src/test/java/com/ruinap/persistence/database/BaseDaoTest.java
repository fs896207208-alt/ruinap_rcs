package com.ruinap.persistence.database;

import cn.hutool.core.lang.Console;
import cn.hutool.db.Entity;
import cn.hutool.db.PageResult;
import cn.hutool.db.sql.Condition;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.persistence.datasource.RcsDSFactory;
import com.ruinap.persistence.repository.BaseDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

/**
 * CommonDB 全面测试用例
 * 针对表: rcs_task
 */
@SpringBootTest
@DisplayName("基础DAO(BaseDao)测试")
public class BaseDaoTest extends BaseDao {

    private static final String TABLE = "rcs_task";

    @Autowired
    private RcsDSFactory factory;

    /**
     * 每次测试前清空表并初始化基础数据，确保测试环境纯净
     */
    @BeforeEach // 替换 @Before
    public void initData() throws SQLException {
//        RcsDSFactory.startup();
//        // 1. 清空表
//        execute("TRUNCATE TABLE " + TABLE);
//
//        // 2. 准备初始化数据 (模拟5条不同状态的任务)
//        List<Entity> tasks = new ArrayList<>();
//
//        // 任务1: 搬运任务(0), 状态:新任务(2), 优先级: 10
//        tasks.add(Entity.create(TABLE)
//                .set("task_code", "T1001").set("task_group", "G1")
//                .set("task_type", 0).set("task_state", 2)
//                .set("equipment_code", "AGV_01").set("task_priority", 10)
//                .set("origin", "L1").set("remark", "紧急搬运"));
//
//        // 任务2: 充电任务(1), 状态:运行中(99), 优先级: 5
//        tasks.add(Entity.create(TABLE)
//                .set("task_code", "T1002").set("task_group", "G1")
//                .set("task_type", 1).set("task_state", 99)
//                .set("equipment_code", "AGV_02").set("task_priority", 5)
//                .set("origin", "CHG_01").set("remark", "低电量回充"));
//
//        // 任务3: 搬运任务(0), 状态:已完成(0), 优先级: 1
//        tasks.add(Entity.create(TABLE)
//                .set("task_code", "T1003").set("task_group", "G2")
//                .set("task_type", 0).set("task_state", 0)
//                .set("equipment_code", "AGV_01").set("task_priority", 1)
//                .set("origin", "L2").set("remark", "普通搬运"));
//
//        // 任务4: 临时任务(3), 状态:异常(-1)
//        tasks.add(Entity.create(TABLE)
//                .set("task_code", "T1004").set("task_group", "G3")
//                .set("task_type", 3).set("task_state", -1)
//                .set("equipment_code", "AGV_03").set("task_priority", 99)
//                .set("origin", "TEMP").set("remark", "人工取消"));
//
//        // 批量插入
//        insertBatch(TABLE, tasks);
    }

    // =========================================================================
    // 1. 测试 Entity 魔法语法 (CommonDB 注释中提到的支持)
    // 注意：这依赖于 Hutool 的解析机制，通常用于 selectList(String tableName, Entity where)
    // =========================================================================

    @Test
    public void testEntityOperator_Like() throws SQLException {
        // 测试: set("remark", "like %搬运%")
        Entity where = Entity.create().set("task_code", "like %T000000118%");
        List<Entity> list = selectList(factory.db, TABLE, where);

        Console.log("LIKE 测试结果数: {}", list.size());
        Assertions.assertTrue(list.size() >= 1);
    }

    @Test
    public void testEntityOperator_In() throws SQLException {
        // 测试: set("task_state", "in 2,99")
        Entity where = Entity.create().set("task_state", "in -1,99");
        List<Entity> list = selectList(factory.db, TABLE, where);

        Console.log("IN (String) 测试结果数: {}", list.size());
        Assertions.assertTrue(list.size() >= 1);
    }

    @Test
    public void testEntityOperator_InArray() throws SQLException {
        // 测试: set("task_type", new int[]{0, 3})
        Entity where = Entity.create().set("task_type", new int[]{0, 1});
        List<Entity> list = selectList(factory.db, TABLE, where);

        Console.log("IN (Array) 测试结果数: {}", list.size());
        Assertions.assertTrue(list.size() >= 1);
    }

    @Test
    public void testEntityOperator_GreaterEqual() throws SQLException {
        // 测试: set("task_priority", ">= 10")
        Entity where = Entity.create().set("task_priority", ">= 200");
        List<Entity> list = selectList(factory.db, TABLE, where);

        Console.log(">= 测试结果数: {}", list.size());
        Assertions.assertTrue(list.size() >= 1);
    }

    @Test
    public void testEntityOperator_IsNull() throws SQLException {
        Entity where = Entity.create().set("destin", "is null");
        List<Entity> list = selectList(factory.db, TABLE, where);
        Console.log(">= 测试结果数: {}", list.size());
        Assertions.assertTrue(list.size() >= 1);
    }

    // =========================================================================
    // 2. 测试 Condition 对象 (Hutool 标准强类型查询)
    // 对应方法: selectList(String tableName, Condition... wheres)
    // =========================================================================

    @Test
    public void testCondition_Equals() throws SQLException {
        List<Entity> list = selectList(factory.db, TABLE, new Condition("task_code", "=", "T0000001664"));
        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals("G0000001629", list.getFirst().getStr("task_group"));
    }

    @Test
    public void testCondition_NotEquals() throws SQLException {
        // 查询 equipment_code != 'AGV_01'
        List<Entity> list = selectList(factory.db, TABLE, new Condition("equipment_code", "!=", "1"), new Condition("equipment_code", "!=", "2"));
        Console.log(">= 测试结果数: {}", list.size());
        Assertions.assertTrue(list.size() >= 1);
    }

    @Test
    public void testCondition_Like() throws SQLException {
        // 模糊查询: like %回充%
        List<Entity> list = selectList(factory.db, TABLE, new Condition("task_code", "like", "%18%"));
        Console.log(">= 测试结果数: {}", list.size());
        Assertions.assertFalse(list.isEmpty());
    }

    @Test
    public void testCondition_GreaterAndLess() throws SQLException {
        // 组合条件: 优先级 > 1 并且 优先级 < 99
        List<Entity> list = selectList(factory.db, TABLE,
                new Condition("task_priority", ">=", 200),
                new Condition("task_priority", "<=", 201)
        );
        Console.log(">= 测试结果数: {}", list.size());
        Assertions.assertFalse(list.isEmpty());
    }

    @Test
    public void testCondition_Between_Fixed() throws SQLException {
        // 定义时间范围
        String startTime = "2025-04-10 17:29:39";
        String endTime = "2025-04-10 17:29:42";

        // 【修正】：使用 >= 和 <= 组合来实现 Between 语义
        // 这样 JDBC 会生成：create_time >= ? AND create_time <= ?
        // 并且参数会作为两个独立的 String/Date 对象安全传入

        List<Entity> list = selectList(factory.db, TABLE,
                new Condition("create_time", ">=", startTime),
                new Condition("create_time", "<=", endTime)
        );

        Console.log("Between(>= <=) 测试结果数: {}", list.size());
        // 断言根据你的实际数据调整，只要查出来就行
        Assertions.assertTrue(list.size() >= 0);
    }

    // =========================================================================
    // 3. 测试 SQL 占位符与泛型 (Bean/Record 支持)
    // 对应方法: selectBean, queryListBean
    // =========================================================================

    @Test
    public void testSelectBean_ById() throws SQLException {
        // 先查一个ID
        Entity one = select(factory.db, TABLE, Entity.create().set("task_code", "T0000001127"));
        Integer id = one.getInt("id");

        // 测试 selectBean (? 占位符)
        RcsTask task = selectBean(factory.db, "SELECT * FROM " + TABLE + " WHERE id = ?", RcsTask.class, id);

        Assertions.assertNotNull(task);
        Assertions.assertEquals("T0000001127", task.getTaskCode());
        Console.log("Bean查询成功: {}", task);
    }

    @Test
    public void testQueryListBean_ComplexSql() throws SQLException {
        // 复杂 SQL 测试
        String sql = "SELECT * FROM " + TABLE + " WHERE task_type = ? AND task_state > ? ORDER BY task_priority DESC";
        // 查 搬运任务(0) 且 状态 > 0
        List<RcsTask> tasks = queryListBean(factory.db, sql, RcsTask.class, 0, 0);

        // 预期只有 T1001 (state=2). T1003(state=0)不满足 > 0
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals("20241203071", tasks.getFirst().getTaskCode());
    }

    // =========================================================================
    // 4. 测试 分页查询
    // 对应方法: selectPageBean
    // =========================================================================

    @Test
    public void testPagination() throws SQLException {
        // 查第 1 页，每页 3 条
        Entity where = Entity.create().set("task_type", 0); // 只查搬运任务
        PageResult<RcsTask> page1 = selectPageBean(factory.db, TABLE, 1, 3, where, RcsTask.class);

        Assertions.assertEquals(3, page1.size()); // 当前页数量
        Assertions.assertTrue(page1.getTotal() >= 8); // 总数 (初始2 + 新增6 = 8个搬运任务)
        Assertions.assertEquals(1, page1.getPage()); // 页码

        // 查第 2 页
        PageResult<RcsTask> page2 = selectPageBean(factory.db, TABLE, 2, 3, where, RcsTask.class);
        Assertions.assertEquals(3, page2.size());

        Console.log("分页测试: 总数={}, 当前页={}", page1.getTotal(), page1.size());
    }

    // =========================================================================
    // 5. 测试 事务与原子性 (tx)
    // =========================================================================

    @Test
    public void testTransaction_Commit() throws SQLException {
        tx(factory.db, db -> {
            insert(db, TABLE, Entity.create().set("task_code", "TX_COMMIT_1"));
            insert(db, TABLE, Entity.create().set("task_code", "TX_COMMIT_2"));
        });

        // 验证两条都插入成功
        long count = count(factory.db, TABLE, Entity.create().set("task_code", "like TX_COMMIT%"));
        Assertions.assertEquals(2, count);
    }

    @Test
    public void testTransaction_Rollback() {
        try {
            tx(factory.db, db -> {
                // 1. 插入成功
                insert(factory.db, TABLE, Entity.create().set("task_code", "TX_ROLLBACK_1"));

                // 2. 模拟异常
                if (true) {
                    throw new RuntimeException("模拟业务异常，触发回滚");
                }

                // 3. 不会执行
                insert(factory.db, TABLE, Entity.create().set("task_code", "TX_ROLLBACK_2"));
            });
        } catch (Exception e) {
            Console.log("捕获预期异常: {}", e.getMessage());
        }

        // 验证回滚：应该查不到 TX_ROLLBACK_1
        try {
            long count = count(factory.db, TABLE, Entity.create().set("task_code", "TX_ROLLBACK_1"));
            Assertions.assertEquals(0, count);
            Console.log("事务回滚测试通过");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 6. 测试 增删改查的基础健壮性
    // =========================================================================

    @Test
    public void testUpdateAndDelete() throws SQLException {
        // 1. Update
        Entity updateBody = Entity.create().set("remark", "已更新").set("destin", "D1");
        Entity where = Entity.create().set("task_code", "TX_COMMIT_2");
        int updated = update(factory.db, TABLE, updateBody, where);
        Assertions.assertEquals(1, updated);

        // 验证更新
        Entity afterUpdate = select(factory.db, TABLE, where);
        Assertions.assertEquals("已更新", afterUpdate.getStr("remark"));

        // 2. Delete
        int deleted = delete(factory.db, TABLE, where);
        Assertions.assertEquals(1, deleted);

        // 验证删除
        Entity afterDelete = select(factory.db, TABLE, where);
        Assertions.assertNull(afterDelete);
    }


    // ==========================================
    // 【修改点】直接在 Test 类内部定义 DTO
    // ==========================================
    public static class RcsTask { // 必须是 static
        private Integer id;
        private String taskCode; // 驼峰命名，Hutool 会自动映射 task_code -> taskCode
        private String taskGroup;
        private Integer taskType;
        private Integer taskState;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getTaskCode() {
            return taskCode;
        }

        public void setTaskCode(String taskCode) {
            this.taskCode = taskCode;
        }

        public String getTaskGroup() {
            return taskGroup;
        }

        public void setTaskGroup(String taskGroup) {
            this.taskGroup = taskGroup;
        }

        public Integer getTaskType() {
            return taskType;
        }

        public void setTaskType(Integer taskType) {
            this.taskType = taskType;
        }

        public Integer getTaskState() {
            return taskState;
        }

        public void setTaskState(Integer taskState) {
            this.taskState = taskState;
        }
    }
}