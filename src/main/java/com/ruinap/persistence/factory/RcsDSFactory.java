package com.ruinap.persistence.factory;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.GlobalDbConfig;
import cn.hutool.db.ds.druid.DruidDSFactory;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.log.level.Level;
import cn.hutool.setting.Setting;
import com.ruinap.infra.config.DbSetting;
import com.ruinap.infra.config.common.PathUtils;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * 调度数据源工厂
 * <p>
 * 统一管理 MySQL 和 H2 两个数据源，均持久化存储。
 * 1. MySQL: 核心业务数据。
 * 2. H2 (File): 本地辅助数据，AES加密存储于硬盘。
 * </p>
 *
 * @author qianye
 * @create 2024-05-21 15:41
 */
@Component
@Order(0)
public class RcsDSFactory implements CommandLineRunner {

    @Autowired
    public DbSetting dbSetting;

    /**
     * MySQL数据库配置分组名称
     */
    public final String RCS_DB_MYSQL = "rcs_db_mysql";

    /**
     * MySQL数据库连接
     */
    public Db db;

    /**
     * H2数据库连接
     */
    public Db h2Db;

    /**
     * 连接池工厂
     */
    public DruidDSFactory druidDSFactory;

    /**
     * 启动方法
     *
     * @param args 启动参数
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        // 1. 全局配置
        //开启 Hutool 数据库全局配置：忽略大小写
        GlobalDbConfig.setCaseInsensitive(true);
        //开启 SQL 日志打印（必须开启，日志会被 SqlAppender 捕获并决定是否打印）
        GlobalDbConfig.setShowSql(true, false, true, Level.DEBUG);

        // 2. 初始化 MySQL
        initMysql();
        // 3. 初始化 H2
        initH2();

        RcsLog.consoleLog.info("RcsDSFactory 初始化完成");
        RcsLog.algorithmLog.info("RcsDSFactory 初始化完成");
    }

    /**
     * 初始化数据库连接池
     * <p>
     * 本段代码主要完成以下功能：
     * <p>
     * 1. 检查并创建数据库。
     * <p>
     * 2. 获取配置设置，其中包括数据库连接相关的设置。
     * <p>
     * 3. 使用DruidDSFactory创建数据源（DataSource），该数据源用于后续的数据库连接。
     * <p>
     * 4. 基于数据源获取数据库连接，并将其封装供后续使用。
     */
    public void initMysql() {
        // 获取数据库名称
        String dbName = dbSetting.getDatabaseName();
        try {
            // 检查并创建数据库
            int databaseCount = checkCreateDatabaseExists();
            if (databaseCount > 0) {
                RcsLog.consoleLog.warn(StrUtil.format("RcsDSFactory 数据库不存在，已创建数据库：{}", dbName));
                RcsLog.algorithmLog.warn(StrUtil.format("RcsDSFactory 数据库不存在，已创建数据库：{}", dbName));
            } else {
                RcsLog.consoleLog.info(StrUtil.format("RcsDSFactory 数据库[{}]已存在", dbName));
                RcsLog.algorithmLog.info(StrUtil.format("RcsDSFactory 数据库[{}]已存在", dbName));
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库[" + dbName + "]连接失败", e);
        }

        // 获取数据库连接池配置
        Setting setting = dbSetting.getSetting();
        // 创建连接池工厂，并传入配置
        druidDSFactory = new DruidDSFactory(setting);
        // 初始化Hikari数据源，指定获取分组
        DataSource ds = druidDSFactory.getDataSource(RCS_DB_MYSQL);
        // 使用数据源获取数据库连接，并将其赋值给db变量
        db = Db.use(ds);
        RcsLog.consoleLog.info("MySQL 初始化完成");
    }

    /**
     * 初始化 H2 数据库
     */
    private void initH2() {
        try {
            // 1. 获取H2db存储路径
            Path dbDir = PathUtils.H2DB_DIR;
            if (!FileUtil.exist(dbDir.toFile())) {
                FileUtil.mkdir(dbDir.toFile());
            }

            // 2. 数据库连接信息
            /* H2 数据库配置分组名称 */
            String rcsDbH2Group = "rcs_db_h2";
            String url = dbSetting.getKeyByGroupAndKey(rcsDbH2Group, "url");
            String username = dbSetting.getKeyByGroupAndKey(rcsDbH2Group, "username");
            String password = dbSetting.getKeyByGroupAndKey(rcsDbH2Group, "password");
            String driver = dbSetting.getKeyByGroupAndKey(rcsDbH2Group, "driver");

            // 3. 创建连接 (H2 本地文件模式开销很小，使用 SimpleDataSource 即可)
            SimpleDataSource h2Ds = new SimpleDataSource(url, username, password, driver);
            h2Db = Db.use(h2Ds);

            RcsLog.consoleLog.info("H2 初始化完成");
        } catch (Exception e) {
            RcsLog.sysLog.error("H2 初始化失败", e);
        }
    }

    /**
     * 检查并创建数据库
     *
     * @return 是否创建数据库 1：创建数据库成功 0：数据库已存在
     * @throws SQLException
     */
    private int checkCreateDatabaseExists() throws SQLException {
        // 获取数据库名称
        String dbName = dbSetting.getDatabaseName();
        int count = 0;
        String dbIp = dbSetting.getDatabaseIp();
        String port = dbSetting.getDatabasePort();
        // 获取数据库连接
        String dbUrl = StrUtil.format("jdbc:mysql://{}:{}/mysql", dbIp, port);
        String username = dbSetting.getKeyByGroupAndKey(RCS_DB_MYSQL, "username");
        String password = dbSetting.getKeyByGroupAndKey(RCS_DB_MYSQL, "password");
        // 1. 连接到mysql数据库
        Db db = Db.use(new SimpleDataSource(dbUrl, username, password));
        // 2. 检查数据库是否存在
        String checkDbSql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
        Entity dbResult = db.queryOne(checkDbSql, dbName);
        if (dbResult == null) {
            // 3. 如果数据库不存在，创建数据库
            String createDbSql = "CREATE DATABASE " + dbName + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            db.execute(createDbSql);
            count = 1;
        }
        // 4. 关闭临时连接
        db.closeConnection(db.getConnection());
        return count;
    }

    /**
     * 关闭数据库连接池
     */
    @PreDestroy
    public void shutdown() {
        if (druidDSFactory != null) {
            // 关闭连接池
            druidDSFactory.close();
            RcsLog.consoleLog.warn("RcsDSFactory 资源释放完毕");
            RcsLog.algorithmLog.warn("RcsDSFactory 资源释放完毕");
        }
    }
}
