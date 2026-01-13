package com.ruinap.persistence;

import cn.hutool.core.util.StrUtil;
import com.ruinap.infra.config.DbSetting;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.factory.RcsDSFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Flyway数据库管理类
 * <p>
 * Flyway 是一个用于数据库版本控制和迁移的开源工具，主要用于管理数据库的版本演进。它支持将数据库的修改和迁移脚本与代码库中的版本控制结合起来，能够帮助开发人员更容易地进行数据库的变更、升级和管理。
 *
 * @author qianye
 * @create 2025-01-11 15:10
 */
@Component
@Order(1)
public class FlywayManager implements CommandLineRunner {

    @Autowired
    private DbSetting dbSetting;
    @Autowired
    private RcsDSFactory factory;

    /**
     * 容器启动后自动调用此方法
     */
    @Override
    public void run(String... args) throws Exception {
        migrate();
    }

    /**
     * 执行数据库迁移
     */
    public void migrate() {
        // Flyway指定数据库脚本的存放路径
        String locations = dbSetting.getKeyByGroupAndKey(factory.RCS_DB_MYSQL, "locations");

        RcsLog.consoleLog.warn("Flyway 检查数据库是否需要同步");
        RcsLog.algorithmLog.warn("Flyway 检查数据库是否需要同步");
        // 创建 Flyway 实例
        Flyway flyway = Flyway.configure()
                .dataSource(factory.druidDSFactory.getDataSource(factory.RCS_DB_MYSQL))
                // 迁移脚本路径
                .locations(locations)
                .load();

        // 在迁移之前执行 repair 修复迁移历史
        flyway.repair();
        // 执行迁移
        MigrateResult migrateResult = flyway.migrate();
        //迁移前版本
        String initialVersion = migrateResult.initialSchemaVersion == null ? "0" : migrateResult.initialSchemaVersion;
        //迁移后目标版本
        String targetVersion = migrateResult.targetSchemaVersion == null ? "0" : migrateResult.targetSchemaVersion;
        //判断是否有迁移后目标版本
        if (migrateResult.targetSchemaVersion == null) {
            RcsLog.consoleLog.info("Flyway 数据库无需同步");
            RcsLog.algorithmLog.info("Flyway 数据库无需同步");
        } else {
            // 获取并打印迁移结果
            RcsLog.consoleLog.info(StrUtil.format("Flyway 同步前版本: {}", initialVersion));
            RcsLog.consoleLog.info(StrUtil.format("Flyway 同步后目标版本: {}", targetVersion));
            RcsLog.consoleLog.info(StrUtil.format("Flyway 执行SQL脚本数量: {}", migrateResult.migrationsExecuted));
            RcsLog.consoleLog.info("Flyway 数据库同步完成");

            RcsLog.algorithmLog.info(StrUtil.format("Flyway 同步前版本: {}", initialVersion));
            RcsLog.algorithmLog.info(StrUtil.format("Flyway 同步后目标版本: {}", targetVersion));
            RcsLog.algorithmLog.info(StrUtil.format("Flyway 执行SQL脚本数量: {}", migrateResult.migrationsExecuted));
            RcsLog.algorithmLog.info("Flyway 数据库同步完成");
        }
    }
}
