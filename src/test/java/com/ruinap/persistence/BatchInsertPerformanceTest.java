package com.ruinap.persistence;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.RandomUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;
import java.util.concurrent.TimeUnit;

/**
 * AGV 轨迹日志批量写入性能基准测试
 * 用于验证 rcs_db.setting 中 rewriteBatchedStatements=true 的效果
 */
public class BatchInsertPerformanceTest {

    // ==== 请根据你的实际环境修改以下配置 ====
    private static final String DB_IP = "127.0.0.1";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "slamopto_rcs3"; // 使用你的数据库名
    private static final String USER = "root";
    private static final String PASS = "123456";

    // 测试插入的数据量 (建议 1万 - 5万)
    private static final int DATA_SIZE = 200000;

    // 基础 URL 参数
    private static final String BASE_URL_PARAMS = "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8";

    @Before
    public void initTable() throws SQLException {
        // 使用一个普通连接初始化表结构
        String url = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS;
        try (Connection conn = DriverManager.getConnection(url, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // 创建一个临时测试表
            stmt.execute("DROP TABLE IF EXISTS t_agv_trace_test");
            stmt.execute("CREATE TABLE t_agv_trace_test (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "agv_code VARCHAR(32), " +
                    "pos_x INT, " +
                    "pos_y INT, " +
                    "battery INT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            System.out.println(">>> 测试表 t_agv_trace_test 初始化完成");
        }
    }

    @Test
    public void compareBatchPerformance() throws SQLException {
        StopWatch stopWatch = new StopWatch("Batch Insert Benchmark");

        // 场景 1: 未开启优化 (模拟默认配置)
        // 显式设置 rewriteBatchedStatements=false
        String urlNormal = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS + "&rewriteBatchedStatements=false";
        runBatchTest(stopWatch, "未开启优化 (False)", urlNormal);

        // 清理数据，准备下一轮
        clearTable();

        // 场景 2: 开启优化 (推荐配置)
        String urlOptimized = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS + "&rewriteBatchedStatements=true";
        runBatchTest(stopWatch, "已开启优化 (True)", urlOptimized);

        // 打印结果对比
        System.out.println("\n=============================================");
        System.out.println(stopWatch.prettyPrint(TimeUnit.SECONDS));
        System.out.println("=============================================");
    }

    private void runBatchTest(StopWatch stopWatch, String taskName, String jdbcUrl) throws SQLException {
        System.out.println("正在执行: " + taskName + " | 数据量: " + DATA_SIZE);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, USER, PASS)) {
            conn.setAutoCommit(false); // 开启事务

            String sql = "INSERT INTO t_agv_trace_test (agv_code, pos_x, pos_y, battery) VALUES (?, ?, ?, ?)";

            stopWatch.start(taskName);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < DATA_SIZE; i++) {
                    ps.setString(1, "AGV_" + RandomUtil.randomInt(1, 10));
                    ps.setInt(2, RandomUtil.randomInt(0, 50000));
                    ps.setInt(3, RandomUtil.randomInt(0, 50000));
                    ps.setInt(4, RandomUtil.randomInt(0, 100));

                    ps.addBatch(); // 加入批处理

                    // 每 1000 条刷入一次，或者全部写完一次性刷入
                    // 注意：rewriteBatchedStatements 在批次越大时优势越明显
                    if ((i + 1) % 2000 == 0) {
                        ps.executeBatch();
                        ps.clearBatch();
                    }
                }
                // 处理剩余的数据
                ps.executeBatch();
            }

            conn.commit(); // 提交事务
            stopWatch.stop();
        }
    }

    private void clearTable() throws SQLException {
        String url = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS;
        try (Connection conn = DriverManager.getConnection(url, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE t_agv_trace_test");
        }
    }

    @After
    public void cleanup() throws SQLException {
        // 测试结束后删除表，保持环境清洁
        String url = "jdbc:mysql://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME + BASE_URL_PARAMS;
        try (Connection conn = DriverManager.getConnection(url, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_agv_trace_test");
            System.out.println(">>> 测试结束，清理临时表完成");
        }
    }
}