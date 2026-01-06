package com.ruinap.persistence.repository;

import cn.hutool.db.Entity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.datasource.RcsDSFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 参数数据库
 *
 * @author qianye
 * @create 2024-05-23 10:18
 */
@Service
public class ConfigDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;
    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_config";
    /**
     * 获取参数值的存储过程名称
     */
    public final String PROCEDURE_GETCONFIGVALUE = "getConfigValue";

    /**
     * RcsLock 实例，用于控制并发访问
     */
    private final RcsLock RCS_LOCK = RcsLock.ofStamped();

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
     * 检测参数的存储过程是否存在
     *
     * @return true:存在 false:不存在
     * @throws SQLException
     */
    public boolean checkProcedureExists() throws SQLException {
        boolean flag = checkProcedureExists(factory.db, factory.dbSetting.getDatabaseName(), PROCEDURE_GETCONFIGVALUE);
        if (!flag) {
            String logStr = "数据库存储过程[" + PROCEDURE_GETCONFIGVALUE + "]不存在，请检查文件夹 doc/db 里是否存在创建该存储过程的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        return flag;
    }

    /**
     * 获取参数值
     *
     * @param configKey 参数键名
     * @return 返回参数Entity，如果不存在，返回null
     * @throws SQLException
     */
    public Entity getConfigValue(String configKey) throws SQLException {
        Entity select = select(factory.db, TABLE_NAME, new Entity().set("config_key", configKey));
        if (select == null || select.isEmpty()) {
            return null;
        }
        return select;
    }

    /**
     * 设置参数值
     *
     * @param configKey   参数键名
     * @param configValue 参数值
     * @return 受影响行数，0表示无更新
     * @throws SQLException
     */
    public int updateConfigValue(String configKey, String configValue) throws SQLException {
        return update(factory.db, TABLE_NAME, new Entity().set("config_value", configValue), new Entity().set("config_key", configKey));
    }

    /**
     * 调用存储过程获取参数值
     *
     * @param configKey 参数键名
     * @param prefix    参数值的前缀
     * @return 参数值
     * @throws SQLException
     */
    private String getStoredConfigValue(String configKey, String prefix) throws SQLException {
        Connection conn = factory.db.getConnection();
        CallableStatement cstmt = conn.prepareCall("{call " + PROCEDURE_GETCONFIGVALUE + "(?, ?, ?, ?)}");

        cstmt.setString(1, configKey);
        cstmt.setInt(2, 10);
        cstmt.setString(3, prefix);
        cstmt.registerOutParameter(4, Types.VARCHAR);
        cstmt.execute();

        return cstmt.getString(4);
    }

    /**
     * 获取任务组号 G0000000001
     *
     * @return 组号
     */
    public String taskGroupKey() {
        return RCS_LOCK.supplyInWrite(() -> {
            try {
                return getStoredConfigValue("sys.taskGroup.key", "G");
            } catch (SQLException e) {
                // 将受检异常包装为运行时异常抛出，代码更简洁
                // 上层如果有全局异常处理器 (GlobalExceptionHandler)，依然能捕获到
                throw new RuntimeException("获取任务组号失败", e);
            }
        });
    }

    /**
     * 获取任务编号 T0000000001
     *
     * @return 编号
     */
    public String taskCodeKey() {
        return RCS_LOCK.supplyInWrite(() -> {
            try {
                return getStoredConfigValue("sys.task.key", "T");
            } catch (SQLException e) {
                throw new RuntimeException("获取任务编号失败", e);
            }
        });
    }

    /**
     * 获取工作状态参数值
     *
     * @return 参数值
     */
    public Integer getWorkState() {
        Integer count = 0;
        try {
            Entity configValue = getConfigValue("rcs.work.state");
            if (configValue != null && !configValue.isEmpty()) {
                count = configValue.getInt("config_value");
            }
        } catch (Exception e) {
            // 记录异常日志
            throw new RuntimeException(e);
        }
        return count;
    }
}
