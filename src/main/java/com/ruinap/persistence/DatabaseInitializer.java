package com.ruinap.persistence;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.ActivationDB;
import com.ruinap.persistence.repository.AlarmDB;
import com.ruinap.persistence.repository.ConfigDB;

/**
 * 数据库结构初始化器
 * <p>
 * 负责在系统启动时检查所有必要的表是否存在，不存在则自动创建。
 * 必须在同步数据库数据 Flyway.migrate 之后执行。
 *
 * @author qianye
 * @create 2025-12-17 17:57
 */
@Component
@Order(2)
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private ActivationDB activationDB;
    @Autowired
    private ConfigDB configDB;
    @Autowired
    private AlarmDB alarmDB;


    @Override
    public void run(String... args) {
        try {
            // 初始化数据库表
            checkTableExists();
        } catch (Exception e) {
            RcsLog.consoleLog.error("数据库表结构初始化失败，系统可能无法正常运行！", e);
            // 根据严重程度，这里可以选择是否 System.exit(1);
        }
    }

    /**
     * 检查数据库表是否存在
     */
    private void checkTableExists() {
        try {
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

//            // 检查AGV表是否存在
//            flag = AgvDB.checkTableExists();
//            if (!flag) {
//                String logStr = "数据库表[" + AgvDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
//                RcsLog.consoleLog.error(logStr);
//                RcsLog.sysLog.error(logStr);
//                throw new RcsException(logStr);
//            }
//
//            // 检查充电桩表是否存在
//            flag = ChargePileDB.checkTableExists();
//            if (!flag) {
//                String logStr = "数据库表[" + ChargePileDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
//                RcsLog.consoleLog.error(logStr);
//                RcsLog.sysLog.error(logStr);
//                throw new RcsException(logStr);
//            }
//
//            // 检查线路表是否存在
//            flag = RoutePresetDB.checkTableExists();
//            if (!flag) {
//                String logStr = "数据库表[" + RoutePresetDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
//                RcsLog.consoleLog.error(logStr);
//                RcsLog.sysLog.error(logStr);
//                throw new RcsException(logStr);
//            }
//
//            // 检查线路明细表是否存在
//            flag = RoutePresetDB.checkDetailTableExists();
//            if (!flag) {
//                String logStr = "数据库表[" + RoutePresetDB.TABLE_DETAIL_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
//                RcsLog.consoleLog.error(logStr);
//                RcsLog.sysLog.error(logStr);
//                throw new RcsException(logStr);
//            }
//
//            // 检查任务触发器记录表是否存在
//            flag = TaskMonitorLogDB.checkTableExists();
//            if (!flag) {
//                String logStr = "数据库表[" + TaskMonitorLogDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
//                RcsLog.consoleLog.error(logStr);
//                RcsLog.sysLog.error(logStr);
//                throw new RcsException(logStr);
//            }
//
//            // 检查任务表是否存在
//            flag = TaskDB.checkTableExists();
//            if (!flag) {
//                String logStr = "数据库表[" + TaskDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
//                RcsLog.consoleLog.error(logStr);
//                RcsLog.sysLog.error(logStr);
//                throw new RcsException(logStr);
//            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
