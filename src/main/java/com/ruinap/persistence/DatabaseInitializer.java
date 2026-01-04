package com.ruinap.persistence;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.ActivationDB;

import java.sql.SQLException;

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
     * 初始化激活表
     *
     * @throws SQLException
     */
    private void checkTableExists() throws SQLException {
        if (!activationDB.checkTableExists()) {
            RcsLog.consoleLog.info("检测到H2数据库表 [{}] 不存在，正在创建表", activationDB.TABLE_NAME);
            activationDB.createTable();
            RcsLog.consoleLog.info("H2数据库表 [{}] 创建成功", activationDB.TABLE_NAME);
        }
    }
}
