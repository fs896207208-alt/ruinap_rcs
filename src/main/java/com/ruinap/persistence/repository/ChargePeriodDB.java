package com.ruinap.persistence.repository;

import cn.hutool.db.Entity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.persistence.factory.RcsDSFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 充电时间段段数据库
 *
 * @author qianye
 * @create 2024-06-12 15:28
 */
@Service
public class ChargePeriodDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;

    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_charge_period";

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
     * 新建充电时间段
     *
     * @param body 数据体
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer createChargePeriod(Entity body) throws SQLException {
        return insert(factory.db, TABLE_NAME, body);
    }

    /**
     * 修改充电时间段
     *
     * @param body  修改体
     * @param where 条件
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer updateChargePeriod(Entity body, Entity where) throws SQLException {
        return update(factory.db, TABLE_NAME, body, where);
    }

    /**
     * 查询充电时间段
     *
     * @param where 条件
     * @return 充电时间段数据
     * @throws SQLException
     */
    public Entity selectChargePeriod(Entity where) throws SQLException {
        return select(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询充电时间段列表
     *
     * @param where 条件
     * @return 充电时间段列表
     * @throws SQLException
     */
    public List<Entity> selectChargePeriodList(Entity where) throws SQLException {
        return selectList(factory.db, TABLE_NAME, where);
    }
}