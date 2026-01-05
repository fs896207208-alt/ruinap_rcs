package com.ruinap.persistence;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;

/**
 * AGV 轨迹日志批量写入性能基准测试
 * 用于验证 rcs_db.setting 中 rewriteBatchedStatements=true 的效果
 */
@DisplayName("批量写入性能测试(JDBC)")
class BatchInsertPerformanceTest {

    // ==== 请根据你的实际环境修改以下配置 ====
    private static final String DB_IP = "127.0.0.1";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "slamopto_rcs3"; // 使用你的数据库名
    private static final String USER = "root";
    private static final String PASS = "123456";

    // 测试插入的数据量 (建议 1万 - 5万)
    private static final int DATA_SIZE = 200000;

    // 基础 URL 参数
    // 关键点：rewriteBatchedStatements=true 必须开启，否则 MySQL 驱动会把 batch 拆成单条发送
    private static final String BASE_URL_PARAMS = "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&rewriteBatchedStatements=true";

    @BeforeEach
    void initTable() throws SQLException {
        // 使用一个普通连接初始化表结构
        String url = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS;

        try (Connection conn = DriverManager.getConnection(url, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 创建一个测试用的临时表 (Memory 引擎或者普通 InnoDB 都可以，这里用 InnoDB 模拟真实场景)
            // 模拟 t_agv_trace_log 表结构
            String sql = """
                        CREATE TABLE IF NOT EXISTS t_agv_trace_test (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            agv_code VARCHAR(32) NOT NULL,
                            x INT NOT NULL,
                            y INT NOT NULL,
                            battery INT,
                            create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """;
            stmt.execute(sql);

            // 清理旧数据
            stmt.execute("TRUNCATE TABLE t_agv_trace_test");
        }
    }

    @Test
    @DisplayName("基准测试：JDBC 原生批量写入")
    void testBatchInsert() throws SQLException {
        String url = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS;
        String sql = "INSERT INTO t_agv_trace_test (agv_code, x, y, battery) VALUES (?, ?, ?, ?)";

        System.out.println(">>> 开始批量插入测试，数据量: " + DATA_SIZE);
        System.out.println(">>> JDBC URL: " + url);

        try (Connection conn = DriverManager.getConnection(url, USER, PASS)) {
            // 开启手动事务，这是批量写入高性能的关键
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                for (int i = 0; i < DATA_SIZE; i++) {
                    ps.setString(1, "AGV_" + RandomUtil.randomInt(1, 10));
                    ps.setInt(2, RandomUtil.randomInt(0, 50000));
                    ps.setInt(3, RandomUtil.randomInt(0, 50000));
                    ps.setInt(4, RandomUtil.randomInt(0, 100));

                    ps.addBatch(); // 加入批处理

                    // 每 2000 条刷入一次，或者全部写完一次性刷入
                    // 注意：rewriteBatchedStatements 在批次越大时优势越明显
                    if ((i + 1) % 2000 == 0) {
                        ps.executeBatch();
                        ps.clearBatch();
                    }
                }
                // 处理剩余的数据
                ps.executeBatch();

                conn.commit(); // 提交事务
                stopWatch.stop();

                System.out.println("--------------------------------------------------");
                System.out.println("总耗时: " + stopWatch.getTotalTimeMillis() + " ms");
                System.out.println("TPS: " + (DATA_SIZE / stopWatch.getTotalTimeSeconds()));
                System.out.println("--------------------------------------------------");
            }
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        // 测试结束后删除表，保持环境清洁
        // 如果想保留数据观察，可以注释掉下面这行
        String url = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS;
        try (Connection conn = DriverManager.getConnection(url, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_agv_trace_test");
        }
    }
}