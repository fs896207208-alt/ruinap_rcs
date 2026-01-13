package com.ruinap.persistence.repository;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.factory.RcsDSFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 充电桩数据库
 *
 * @author qianye
 * @create 2024-06-12 15:28
 */
@Service
public class ChargePileDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;

    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_charge_pile";

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
     * 新建充电桩
     *
     * @param body 数据体
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer createChargePile(Entity body) throws SQLException {
        return insert(factory.db, TABLE_NAME, body);
    }

    /**
     * 修改充电桩
     *
     * @param body  修改体
     * @param where 条件
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer updateChargePile(Entity body, Entity where) throws SQLException {
        return update(factory.db, TABLE_NAME, body, where);
    }

    /**
     * 查询充电桩
     *
     * @param where 条件
     * @return 充电桩数据
     * @throws SQLException
     */
    public Entity selectChargePile(Entity where) throws SQLException {
        return select(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询充电桩列表
     *
     * @param where 条件
     * @return 充电桩列表
     * @throws SQLException
     */
    public List<Entity> selectChargePileList(Entity where) throws SQLException {
        return selectList(factory.db, TABLE_NAME, where);
    }

    /**
     * 设置充电桩离线
     *
     * @param code 充电桩编码
     * @return 受影响行数，大于0表示成功
     */
    public Integer offLine(int code) {
        Entity body = new Entity();
        body.set("state", 0);
        Entity where = new Entity();
        where.set("code", code);
        try {
            return updateChargePile(body, where);
        } catch (SQLException e) {
            RcsLog.sysLog.error(e);
            return 0;
        }
    }

    /**
     * 设置所有充电桩离线
     *
     * @return 受影响行数，大于0表示成功
     */
    public Integer offLineAll() {
        try {
            return execute(factory.db, StrUtil.format("update {} set state = 0 , idle_state = -1 where state != 0", TABLE_NAME));
        } catch (SQLException e) {
            RcsLog.sysLog.error(e);
            return 0;
        }
    }
}