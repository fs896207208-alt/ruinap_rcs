package com.ruinap.persistence.repository;

import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.persistence.factory.RcsDSFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 统计数据库
 *
 * @author qianye
 * @create 2025-11-26 14:36
 */
@Service
public class StatisticsDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;

    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_statistics";

    /**
     * 检测表是否存在
     *
     * @return true:存在 false:不存在
     * @throws SQLException
     */
    public boolean checkTableExists() throws SQLException {
        return checkTableExists(factory.db, factory.dbSetting.getDatabaseName(), TABLE_NAME);
    }

    /**
     * 新增统计
     *
     * @param body 新增体
     * @return 受影响行数，大于0表示成功
     */
    public Integer createStatistics(Entity body) throws SQLException {
        return insert(factory.db, TABLE_NAME, body);
    }

    /**
     * 修改统计
     *
     * @param body  修改体
     * @param where 条件
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer updateStatistics(Entity body, Entity where) throws SQLException {
        return update(factory.db, TABLE_NAME, body, where);
    }

    /**
     * 查询统计
     *
     * @param where 条件
     * @return 统计对象
     * @throws SQLException
     */
    public Entity selectStatistics(Entity where) throws SQLException {
        return select(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询统计
     *
     * @param sql    sql语句
     * @param params 参数条件
     * @return 统计对象
     * @throws SQLException
     */
    public Entity selectStatistics(String sql, Object... params) throws SQLException {
        return select(factory.db, sql, params);
    }

    /**
     * 查询统计列表
     *
     * @param where 条件
     * @return 任务列表
     * @throws RuntimeException
     */
    public List<Entity> selectStatisticsList(Entity where) throws SQLException {
        return selectList(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询统计列表
     *
     * @param wheres 特殊条件
     * @return 统计对象
     * @throws SQLException
     */
    public List<Entity> queryStatisticsList(Condition... wheres) throws SQLException {
        return selectList(factory.db, TABLE_NAME, wheres);
    }
}