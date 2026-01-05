package com.ruinap.infra.aop;

import cn.hutool.db.Entity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.persistence.repository.BaseDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

/**
 * AOP 事务切面集成测试
 *
 * @author qianye
 * @create 2025-12-11 16:32
 */
@SpringBootTest // 组合注解，替代 @RunWith
public class TransactionAspectTest extends BaseDao {

    @Autowired
    private TxDemoService txDemoService;

    private static final String TABLE = "rcs_task";

    @BeforeEach // JUnit 6
    public void cleanData() throws SQLException {
        // 每次测试前清理脏数据
        delete(txDemoService.factory.db, TABLE, Entity.create().set("remark", "like 事务测试%"));
    }

    /**
     * 测试 1: 正常事务提交
     */
    @Test
    public void testTransactionCommit() throws SQLException {
        String code = "TX_OK_001";

        System.out.println("=== 开始测试: 事务正常提交 ===");
        txDemoService.insertSuccess(code);

        // 验证：数据库里应该有这条数据
        long count = count(txDemoService.factory.db, TABLE, Entity.create().set("task_code", code));
        Assertions.assertEquals(1, count, "事务提交失败，数据未入库");
        System.out.println("=== 测试通过: 数据已入库 ===");
    }

    /**
     * 测试 2: 手动回滚 (利用 ApplicationLock 抛异常)
     */
    @Test
    public void testManualRollback() {
        String code = "TX_MANUAL_ROLLBACK";
        System.out.println("=== 开始测试: 事务异常回滚 ===");

        try {
            txDemoService.testManualRollback(code);
            Assertions.fail("预期应当抛出异常，但未抛出！");
        } catch (Exception e) {
            System.out.println("-> 捕获预期异常: " + e.getMessage());
        }

        // 验证：数据库里应该 **没有** 这条数据
        try {
            long count = count(txDemoService.factory.db, TABLE, Entity.create().set("task_code", code));
            System.out.println("-> 查询数据库记录数: " + count);

            // JUnit 6: assertEquals(expected, actual, message)
            Assertions.assertEquals(0, count, "严重故障：事务未回滚！脏数据已入库！");
            System.out.println("=== 测试通过: 事务已完美回滚 ===");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTransactionRollback() {
        String code = "AOP_ROLLBACK_TEST";
        System.out.println("=== AOP 自动化事务测试 ===");

        try {
            // 调用带注解的方法
            txDemoService.insertAndRollback(code);
            Assertions.fail("未抛出异常");
        } catch (Exception e) {
            System.out.println("-> 捕获异常: " + e.getMessage());
        }

        // 验证：应该回滚
        try {
            long count = count(txDemoService.factory.db, TABLE, Entity.create().set("task_code", code));
            // JUnit 6: assertEquals(expected, actual, message)
            Assertions.assertEquals(0, count, "事务回滚失败，脏数据存在");
            System.out.println("=== 测试通过: 回滚成功 ===");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}