package com.ruinap.persistence.repository;

import cn.hutool.core.date.DateTime;
import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.datasource.RcsDSFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 告警信息数据库
 *
 * @author qianye
 * @create 2024-05-22 16:42
 */
@Service
public class AlarmDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;
    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_alarm";


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
     * 新建告警信息
     *
     * @param body 数据体
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer createAlarm(Entity body) throws SQLException {
        return insert(factory.db, TABLE_NAME, body);
    }

    /**
     * 查询告警信息
     *
     * @param where 条件
     * @return 告警信息列表
     * @throws SQLException
     */
    public List<Entity> queryAlarmList(Entity where) throws SQLException {
        return selectList(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询告警信息
     *
     * @param wheres 特殊条件
     * @return 告警信息列表
     * @throws SQLException
     */
    public List<Entity> queryAlarmList(Condition... wheres) throws SQLException {
        return selectList(factory.db, TABLE_NAME, wheres);
    }

    /**
     * 查询告警信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 告警信息列表
     * @throws SQLException
     */
    public List<Entity> queryAlarmList(DateTime startTime, DateTime endTime) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE create_time BETWEEN ? AND ? order by create_time desc";
        return queryList(factory.db, sql, startTime, endTime);
    }


    /**
     * 修改告警信息
     *
     * @param body  修改体
     * @param where 条件
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer updateAlarm(Entity body, Entity where) throws SQLException {
        return update(factory.db, TABLE_NAME, body, where);
    }
}
