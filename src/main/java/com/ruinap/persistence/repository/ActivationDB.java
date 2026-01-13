package com.ruinap.persistence.repository;

import cn.hutool.db.Entity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.persistence.factory.RcsDSFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 激活表 数据库
 *
 * @author qianye
 * @create 2025-12-17 16:42
 */
@Service
public class ActivationDB extends BaseDao {
    @Autowired
    private RcsDSFactory factory;
    /**
     * 表名称
     */
    public String TABLE_NAME = "RCS_ACTIVATION";

    /**
     * 判断表是否存在
     *
     * @return true:存在 false:不存在
     * @throws SQLException 抛出数据库异常
     */
    public boolean checkTableExists() throws SQLException {
        return checkTableExists(factory.h2Db, "PUBLIC", TABLE_NAME);
    }

    /**
     * 创建表
     * 修复点：
     * 1. 移除多余的 SET 语句 (JDBC 不需要)
     * 2. 移除 ENGINE 等 MySQL 专用后缀 (保持 SQL 纯净)
     * 3. 修正 ` rcs_activation` 中的空格错误
     * 4. 拆分为两步执行 (DROP 和 CREATE)，避免多语句执行失败风险
     */
    public Integer createTable() throws SQLException {
        // 1. 先删
        String dropSql = "DROP TABLE IF EXISTS `" + TABLE_NAME + "`";
        execute(factory.h2Db, dropSql);

        // 2. 后建 (精简版 SQL)
        String createSql = "CREATE TABLE `" + TABLE_NAME + "` (" +
                "`id` int(10) NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`machine_code` varchar(50) DEFAULT NULL COMMENT '机器码'," +
                "`activation_code` varchar(50) DEFAULT NULL COMMENT '激活码'," +
                "`secure_code` varchar(50) DEFAULT NULL COMMENT '安全码'," +
                "`expired_date` datetime(3) DEFAULT NULL COMMENT '过期时间'," +
                "PRIMARY KEY (`id`)" +
                ") COMMENT = '调度激活表'";

        return execute(factory.h2Db, createSql);
    }

    /**
     * 插入数据
     *
     * @param entity 实体
     * @return 影响行数, 0:失败 >0:成功
     * @throws SQLException 抛出数据库异常
     */
    public Integer insert(Entity entity) throws SQLException {
        return insert(factory.h2Db, TABLE_NAME, entity);
    }

    /**
     * 查询数据
     *
     * @param where 条件
     * @return 数据列表
     * @throws SQLException 抛出数据库异常
     */
    public List<Entity> query(Entity where) throws SQLException {
        return selectList(factory.h2Db, TABLE_NAME, where);
    }

    /**
     * 删除数据
     *
     * @param where 条件
     * @return 影响行数, 0:失败 >0:成功
     * @throws SQLException 抛出数据库异常
     */
    public Integer delete(Entity where) throws SQLException {
        return delete(factory.h2Db, TABLE_NAME, where);
    }
}
