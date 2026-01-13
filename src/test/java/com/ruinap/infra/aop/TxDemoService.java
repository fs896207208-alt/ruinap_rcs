package com.ruinap.infra.aop;

import cn.hutool.db.Entity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.framework.annotation.Transactional;
import com.ruinap.persistence.factory.RcsDSFactory;
import com.ruinap.persistence.repository.BaseDao;

import java.sql.SQLException;

/**
 * 事务测试专用 Service
 */
@Service
public class TxDemoService extends BaseDao {

    @Autowired
    public RcsDSFactory factory;

    public static final String TABLE = "rcs_task";

    /**
     * 场景 1: 正常提交
     */
    @Transactional
    public void insertSuccess(String code) throws SQLException {
        // 插入数据
        insert(factory.db, TABLE, Entity.create().set("task_code", code).set("remark", "事务测试-成功"));
    }

    /**
     * 【手动事务测试】
     * 不依赖 AOP，直接调用父类 CommonDB 的 tx() 方法。
     * 验证底层数据库的回滚能力是否正常。
     */
    public void testManualRollback(String code) throws SQLException {
        // 手动开启事务 (模拟 AOP 做的事情)
        this.tx(factory.db, db -> {
            try {
                // 1. 插入数据
                // 注意：这里调用的是父类的 insert，它会自动参与到当前线程的事务中
                insert(factory.db, TABLE, Entity.create().set("task_code", code).set("remark", "手动事务-将要回滚"));

                // 2. 打印日志证明插进去了 (在事务内是能查到的，但未提交)
                System.out.println(">>> 事务内已执行插入语句...");

                // 3. 模拟业务报错 -> 触发 Hutool 回滚
                throw new RuntimeException("手动触发异常，测试底层回滚能力！");

            } catch (SQLException e) {
                // 包装异常抛出，确保触发回滚
                throw new RuntimeException(e);
            }
        });
    }

    @Transactional
    public void insertAndRollback(String code) throws SQLException {
        insert(factory.db, TABLE, Entity.create().set("task_code", code).set("remark", "AOP测试-将要回滚"));
        throw new RuntimeException("模拟业务异常，AOP 应拦截并回滚！");
    }
}
