package com.ruinap.persistence.db.database;

import cn.hutool.db.Db;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.thread.VthreadPool;
import com.ruinap.persistence.datasource.RcsDSFactory;
import com.ruinap.persistence.repository.ConfigDB;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * ConfigDB 单元测试 (修正版)
 * <p>
 * 重点模拟数据库存储过程的“累加特性”，验证在高并发下：
 * 1. RcsLock 是否保证了逻辑的串行化。
 * 2. 生成的单号是否唯一且连续（不重号）。
 *
 * @author qianye
 */
@SpringBootTest // 组合注解，已包含 @ExtendWith(SpringRunner.class)
@DisplayName("数据库配置表(ConfigDB)并发测试")
class ConfigDBTest {

    @Autowired
    private RcsDSFactory factory;
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private ConfigDB configDB;

    @Mock
    private Db mockDb;
    @Mock
    private Connection mockConnection;
    @Mock
    private CallableStatement mockCallableStatement;

    private AutoCloseable closeable;

    // 【核心】用这个原子变量模拟数据库里的 Sequence 计数器
    // 真实的存储过程逻辑：return ++currentValue;
    private final AtomicInteger dbSequenceSimulator = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws SQLException {
        closeable = MockitoAnnotations.openMocks(this);

        // 注入 Mock 对象
        factory.db = mockDb;

        // 模拟 JDBC 链路
        when(mockDb.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareCall(anyString())).thenReturn(mockCallableStatement);

        // 模拟参数设置（不做实际操作）
        doNothing().when(mockCallableStatement).setString(anyInt(), anyString());
        doNothing().when(mockCallableStatement).setInt(anyInt(), anyInt());
        doNothing().when(mockCallableStatement).registerOutParameter(anyInt(), anyInt());
        when(mockCallableStatement.execute()).thenReturn(true);

        // 【关键修正】重置模拟器，确保每个测试方法开始时数据库都是从 0 开始
        dbSequenceSimulator.set(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * 【核心测试】高并发下模拟存储过程累加
     * 场景：100个线程同时抢单号
     * 预期：生成 T0000000001 ~ T0000000100，无重复，无遗漏
     */
    @Test
    @DisplayName("测试：高并发单号生成 (100线程)")
    void testTaskCodeKey_Concurrency_Sequence() {
        int threadCount = 100;

        // 1. 定义 Mock 行为：模拟存储过程的原子递增
        try {
            when(mockCallableStatement.getString(4)).thenAnswer(invocation -> {
                // 模拟数据库处理耗时（放大并发冲突的可能性）
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    // ignore
                }

                int currentSeq = dbSequenceSimulator.incrementAndGet();
                // 格式化为 T + 10位数字，例如 T0000000001
                return String.format("T%010d", currentSeq);
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 用于收集结果的线程安全 Set
        Set<String> generatedKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // 2. 启动并发任务
        CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = vthreadPool.runAsync(() -> {
                try {
                    // 调用被测方法
                    String key = configDB.taskCodeKey();
                    generatedKeys.add(key);
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("生成单号抛出异常: " + e.getMessage());
                }
            });
        }

        // 3. 等待所有任务完成
        CompletableFuture.allOf(futures).join();

        // 4. 严苛断言
        System.out.println("生成的单号数量: " + generatedKeys.size());
        System.out.println("数据库模拟器最终值: " + dbSequenceSimulator.get());

        // 断言1: 必须生成了 100 个不重复的 Key
        // JUnit 6: assertEquals(expected, actual, message)
        Assertions.assertEquals(threadCount, generatedKeys.size(), "出现重复单号或部分失败");

        // 断言2: 数据库模拟器应该恰好被调用了 100 次
        Assertions.assertEquals(threadCount, dbSequenceSimulator.get(), "存储过程调用次数不符");

        // 断言3: 验证边界值（确保没丢数据）
        // JUnit 6: assertTrue(condition, message)
        Assertions.assertTrue(generatedKeys.contains("T0000000001"), "必须包含第一个号 T0000000001");
        Assertions.assertTrue(generatedKeys.contains("T0000000100"), "必须包含最后一个号 T0000000100");
    }

    /**
     * 【精准状态测试】验证小规模并发下的数值准确性
     * 场景：数据库初始值为 1
     * 动作：3个线程并发调用
     * 预期：
     * 1. 返回值必须是 T0000000001, T0000000002, T0000000003
     * 2. 最终数据库的值必须变为 4
     */
    @Test
    @DisplayName("测试：小规模并发数值准确性 (初始值=1)")
    void testTaskCodeKey_SmallConcurrency_ExactValues() {
        int threadCount = 3;
        // 1. 设置初始值为 1
        AtomicInteger specificSimulator = new AtomicInteger(1);

        try {
            // 2. 定义行为：模拟 "获取当前值 -> 累加" 的过程
            when(mockCallableStatement.getString(4)).thenAnswer(invocation -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }

                // getAndIncrement: 先返回 1，再变成 2
                int currentVal = specificSimulator.getAndIncrement();
                return String.format("T%010d", currentVal);
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 用于收集结果
        Set<String> results = Collections.synchronizedSet(new HashSet<>());

        // 3. 启动3个线程
        CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = vthreadPool.runAsync(() -> {
                results.add(configDB.taskCodeKey());
            });
        }

        CompletableFuture.allOf(futures).join();

        // 5. 核心断言
        // JUnit 6: assertEquals(expected, actual, message)
        Assertions.assertEquals(3, results.size(), "必须生成3个单号");

        // 验证具体的值
        // JUnit 6: assertTrue(condition, message)
        Assertions.assertTrue(results.contains("T0000000001"), "必须包含 T0000000001");
        Assertions.assertTrue(results.contains("T0000000002"), "必须包含 T0000000002");
        Assertions.assertTrue(results.contains("T0000000003"), "必须包含 T0000000003");

        // 验证副作用：数据库的值是否正确变成了 4
        Assertions.assertEquals(4, specificSimulator.get(), "数据库最终值必须是4");
    }

    /**
     * 【严苛并发测试】验证多线程同时抢占下的序列号连续性
     * 场景：数据库初始值为 1，3个线程同时请求
     * 预期：生成 T...1, T...2, T...3，且数据库最终变为 4
     */
    @Test
    @DisplayName("测试：极限并发下序列号连续性 (Latch发令枪)")
    void testTaskCodeKey_StrictConcurrency_StartFromOne() throws Exception {
        int threadCount = 3;

        // 1. 模拟数据库 Sequence：初始值为 1
        AtomicInteger dbSequence = new AtomicInteger(1);

        try {
            when(mockCallableStatement.getString(4)).thenAnswer(invocation -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
                int val = dbSequence.getAndIncrement();
                return String.format("T%010d", val);
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 3. 准备并发工具
        CountDownLatch startGun = new CountDownLatch(1);
        Set<String> results = Collections.synchronizedSet(new HashSet<>());
        CompletableFuture<?>[] futures = new CompletableFuture[threadCount];

        // 4. 创建线程但不立即执行业务，而是等待发令枪
        for (int i = 0; i < threadCount; i++) {
            futures[i] = vthreadPool.runAsync(() -> {
                try {
                    // 线程在此阻塞，等待主线程开枪
                    startGun.await();

                    // 【关键点】同时冲击 ConfigDB.taskCodeKey()
                    String key = configDB.taskCodeKey();
                    results.add(key);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // 5. 预备... 砰！(所有线程同时开跑)
        Thread.sleep(50);
        startGun.countDown();

        // 6. 等待结束
        CompletableFuture.allOf(futures).join();

        // 8. 严苛断言
        // JUnit 6 参数顺序调整
        Assertions.assertEquals(3, results.size(), "生成的单号数量不对");

        Assertions.assertTrue(results.contains("T0000000001"), "缺少 T0000000001");
        Assertions.assertTrue(results.contains("T0000000002"), "缺少 T0000000002");
        Assertions.assertTrue(results.contains("T0000000003"), "缺少 T0000000003");

        // 验证C: 数据库最终状态必须是 4
        Assertions.assertEquals(4, dbSequence.get(), "数据库最终值必须为4");
    }
}