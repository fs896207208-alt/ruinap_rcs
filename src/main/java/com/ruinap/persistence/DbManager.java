package com.ruinap.persistence;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.*;
import lombok.Getter;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据库管理
 * <p>
 * 负责在系统启动时检查所有必要的表是否存在，不存在则自动创建。
 * 必须在同步数据库数据 Flyway.migrate 之后执行。
 *
 * @author qianye
 * @create 2025-12-17 17:57
 */
@Component
@Order(2)
public class DbManager implements CommandLineRunner {

    @Autowired
    private ActivationDB activationDB;
    @Autowired
    private ConfigDB configDB;
    @Autowired
    private AlarmDB alarmDB;
    @Autowired
    private AgvDB agvDB;
    @Autowired
    private ChargePeriodDB chargePeriodDB;
    @Autowired
    private ChargePileDB chargePileDB;
    @Autowired
    private TaskDB taskDB;
    @Autowired
    private StatisticsDB statisticsDB;


    /**
     * 告警列表数据缓存
     */
    @Getter
    private List<Entity> alarmListCache = new CopyOnWriteArrayList<>();
    /**
     * 第三方数据缓存
     */
    @Getter
    private List<JSONObject> thirdPartyCache = new CopyOnWriteArrayList<>();


    @Override
    public void run(String... args) {
        try {
            // 初始化数据库表
            checkTableExists();
        } catch (Exception e) {
            RcsLog.consoleLog.error("数据库表结构初始化失败，系统可能无法正常运行！", e);
            // 根据严重程度，这里可以选择是否 System.exit(1);
        }

        // 更新告警数据
        replaceAlarmList();
    }

    /**
     * 检查数据库表是否存在
     */
    private void checkTableExists() throws SQLException {
        // 存在标记
        boolean flag;
        //检查激活表是否存在
        flag = activationDB.checkTableExists();
        if (!flag) {
            RcsLog.consoleLog.info("检测到H2数据库表 [{}] 不存在，正在创建表", activationDB.TABLE_NAME);
            activationDB.createTable();
            RcsLog.consoleLog.info("H2数据库表 [{}] 创建成功", activationDB.TABLE_NAME);
        }

        // 检查参数表是否存在
        flag = configDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + configDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.algorithmLog.error(logStr);
            throw new RuntimeException(logStr);
        }
        // 检查存储过程是否存在
        flag = configDB.checkProcedureExists();
        if (!flag) {
            String logStr = "数据库存储过程[" + configDB.PROCEDURE_GETCONFIGVALUE + "]不存在，请检查文件夹 doc/db 里是否存在创建该存储过程的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.algorithmLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查告警表是否存在
        flag = alarmDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + alarmDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.algorithmLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查AGV表是否存在
        flag = agvDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + agvDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查充电时间段表是否存在
        flag = chargePeriodDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + chargePeriodDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查充电桩表是否存在
        flag = chargePileDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + chargePileDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查任务表是否存在
        flag = taskDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + taskDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查统计表是否存在
        flag = statisticsDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + statisticsDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }
    }


    /**
     * 更新告警数据
     */
    private void replaceAlarmList() {
        // 获取今天 0:00:00 的时间
        DateTime startTime = DateUtil.beginOfDay(DateUtil.date());
        // 获取今天 23:59:59 的时间
        DateTime endTime = DateUtil.endOfDay(DateUtil.date());
        try {
            alarmListCache = alarmDB.queryAlarmList(startTime, endTime);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
