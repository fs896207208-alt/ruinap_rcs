package com.ruinap.persistence.repository;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.factory.RcsDSFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * AGV数据库
 *
 * @author qianye
 * @create 2024-11-15 14:36
 */
@Service
public class AgvDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;

    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_agv";

    /**
     * 检测表是否存在
     *
     * @return true:存在 false:不存在
     * @throws SQLException
     */
    public boolean checkTableExists() throws SQLException {
        boolean flag = checkTableExists(factory.db, factory.dbSetting.getDatabaseName(), TABLE_NAME);
        if (!flag) {
            String logStr = "数据库表[" + TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }
        return flag;
    }

    /**
     * 新增AGV
     *
     * @param body 新增体
     * @return 受影响行数，大于0表示成功
     */
    public Integer createAgv(Entity body) throws SQLException {
        return insert(factory.db, TABLE_NAME, body);
    }

    /**
     * 修改AGV
     *
     * @param body  修改体
     * @param where 条件
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer updateAgv(Entity body, Entity where) throws SQLException {
        return update(factory.db, TABLE_NAME, body, where);
    }

    /**
     * 查询AGV
     *
     * @param where 条件
     * @return AGV对象
     * @throws SQLException
     */
    public Entity selectAgv(Entity where) throws SQLException {
        return select(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询AGV
     *
     * @param sql    sql语句
     * @param params 参数条件
     * @return AGV对象
     * @throws SQLException
     */
    public Entity selectAgv(String sql, Object... params) throws SQLException {
        return select(factory.db, sql, params);
    }

    /**
     * 查询AGV列表
     *
     * @param where 条件
     * @return 任务列表
     * @throws RuntimeException
     */
    public List<Entity> selectAgvList(Entity where) throws SQLException {
        return selectList(factory.db, TABLE_NAME, where);
    }

    /**
     * 更新AGV充电信号
     *
     * @param rcsAgv       AGV
     * @param chargeSignal 充电信号 0正常 1高优先级信号 2低优先级信号
     * @return 受影响行数，大于0表示成功
     */
    public Integer updateAgvChargeSignal(RcsAgv rcsAgv, int chargeSignal) {
        Entity body = new Entity();
        body.set("charge_signal", chargeSignal);
        Entity where = new Entity();
        where.set("agv_id", rcsAgv.getAgvId());

        try {
            return updateAgv(body, where);
        } catch (SQLException e) {
            RcsLog.sysLog.error(e);
            return 0;
        }
    }

    /**
     * 更新AGV隔离状态
     *
     * @param agvId          AGV
     * @param isolationState 隔离状态 0未隔离 1在线隔离 2离线隔离
     * @return 受影响行数，大于0表示成功
     */
    public Integer updateAgvIsolationState(String agvId, int isolationState) {
        Entity body = new Entity();
        body.set("isolation_state", isolationState);
        Entity where = new Entity();
        where.set("agv_id", agvId);

        try {
            return updateAgv(body, where);
        } catch (SQLException e) {
            RcsLog.sysLog.error(e);
            return 0;
        }
    }

    /**
     * 更新AGV控制权
     *
     * @param agvId      AGV
     * @param agvControl 控制权 0调度 1其他
     * @return 受影响行数，大于0表示成功
     */
    public Integer updateAgvControl(String agvId, int agvControl) {
        Entity body = new Entity();
        body.set("agv_control", agvControl);
        Entity where = new Entity();
        where.set("agv_id", agvId);

        try {
            return updateAgv(body, where);
        } catch (SQLException e) {
            RcsLog.sysLog.error(e);
            return 0;
        }
    }

    /**
     * 设置所有AGV离线
     *
     * @return 受影响行数，大于0表示成功
     */
    public Integer offLineAll() {
        try {
            return execute(factory.db, StrUtil.format("update {} set agv_state = -1, light = 1 where agv_state != -1", TABLE_NAME));
        } catch (SQLException e) {
            RcsLog.sysLog.error(e);
            return 0;
        }
    }
}