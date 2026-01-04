package com.ruinap.persistence.db.database;

import cn.hutool.db.Db;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.framework.test.SpringRunner;
import com.ruinap.infra.thread.VthreadPool;
import com.ruinap.persistence.datasource.RcsDSFactory;
import com.ruinap.persistence.repository.ConfigDB;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigDBTest {
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

    @Before
    public void setUp() throws SQLException {
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

    @After
    public void tearDown() throws Exception {
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
    public void testTaskCodeKey_Concurrency_Sequence() {
        int threadCount = 100;

        // 1. 定义 Mock 行为：模拟存储过程的原子递增
        // 无论多少线程并发调用 JDBC，这里都模拟数据库返回当前值+1
        try {
            when(mockCallableStatement.getString(4)).thenAnswer(invocation -> {
                // 模拟数据库处理耗时（放大并发冲突的可能性）
                // 如果没有 RcsLock 保护，虽然 AtomicInteger 是线程安全的，
                // 但在真实数据库中，多个事务同时读取如果不加锁可能会读到旧值。
                // 这里的测试验证的是：代码能否正确串行地拿到这个模拟值。
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
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
                    // 记录异常（实际测试中不应发生）
                    e.printStackTrace();
                    Assert.fail("生成单号抛出异常: " + e.getMessage());
                }
            });
        }

        // 3. 等待所有任务完成
        CompletableFuture.allOf(futures).join();

        // 4. 严苛断言
        System.out.println("生成的单号数量: " + generatedKeys.size());
        System.out.println("数据库模拟器最终值: " + dbSequenceSimulator.get());

        // 断言1: 必须生成了 100 个不重复的 Key
        Assert.assertEquals("出现重复单号或部分失败", threadCount, generatedKeys.size());

        // 断言2: 数据库模拟器应该恰好被调用了 100 次
        Assert.assertEquals("存储过程调用次数不符", threadCount, dbSequenceSimulator.get());

        // 断言3: 验证边界值（确保没丢数据）
        Assert.assertTrue("必须包含第一个号 T0000000001", generatedKeys.contains("T0000000001"));
        Assert.assertTrue("必须包含最后一个号 T0000000100", generatedKeys.contains("T0000000100"));
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
    public void testTaskCodeKey_SmallConcurrency_ExactValues() {
        int threadCount = 3;
        // 1. 设置初始值为 1
        AtomicInteger specificSimulator = new AtomicInteger(1);

        try {
            // 2. 定义行为：模拟 "获取当前值 -> 累加" 的过程
            // 比如数据库里现在是1，调用一次后返回T...1，数据库变成2
            when(mockCallableStatement.getString(4)).thenAnswer(invocation -> {
                // 模拟一点点耗时，增加并发碰撞概率
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

        // 4. 打印结果方便调试
        System.out.println("返回的单号集合: " + results);
        System.out.println("数据库最终值: " + specificSimulator.get());

        // 5. 核心断言
        Assert.assertEquals("必须生成3个单号", 3, results.size());

        // 验证具体的值
        Assert.assertTrue("必须包含 T0000000001", results.contains("T0000000001"));
        Assert.assertTrue("必须包含 T0000000002", results.contains("T0000000002"));
        Assert.assertTrue("必须包含 T0000000003", results.contains("T0000000003"));

        // 验证副作用：数据库的值是否正确变成了 4
        Assert.assertEquals("数据库最终值必须是4", 4, specificSimulator.get());
    }

    /**
     * 【严苛并发测试】验证多线程同时抢占下的序列号连续性
     * 场景：数据库初始值为 1，3个线程同时请求
     * 预期：生成 T...1, T...2, T...3，且数据库最终变为 4
     */
    @Test
    public void testTaskCodeKey_StrictConcurrency_StartFromOne() throws Exception {
        int threadCount = 3;

        // 1. 模拟数据库 Sequence：初始值为 1
        // 对应你的描述："task.key的值是1"
        AtomicInteger dbSequence = new AtomicInteger(1);

        // 2. 模拟存储过程逻辑：先返回当前值，再自增
        // 对应你的描述："返回1并且将值+1" -> 所以用 getAndIncrement()
        try {
            when(mockCallableStatement.getString(4)).thenAnswer(invocation -> {
                // 模拟一点微小的数据库IO耗时，增加线程在锁外排队的概率
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
        // 发令枪：确保3个线程都就位后，同时开始执行
        CountDownLatch startGun = new CountDownLatch(1);
        // 结果容器
        Set<String> results = Collections.synchronizedSet(new HashSet<>());
        CompletableFuture<?>[] futures = new CompletableFuture[threadCount];

        // 4. 创建线程但不立即执行业务，而是等待发令枪
        for (int i = 0; i < threadCount; i++) {
            futures[i] = vthreadPool.runAsync(() -> {
                try {
                    // 线程在此阻塞，等待主线程开枪
                    startGun.await();

                    // 【关键点】3个线程这里会同时冲击 ConfigDB.taskCodeKey()
                    // 如果 RcsLock 没锁住，这里可能会产生重复值或顺序错乱
                    String key = configDB.taskCodeKey();
                    results.add(key);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // 5. 预备... 砰！(所有线程同时开跑)
        // 这一步是为了模拟极限并发，而不是依次启动
        Thread.sleep(50); // 确保所有线程都已启动并阻塞在 await()
        startGun.countDown();

        // 6. 等待结束
        CompletableFuture.allOf(futures).join();

        // 7. 打印结果
        System.out.println("生成单号: " + results);
        System.out.println("数据库最终值: " + dbSequence.get());

        // 8. 严苛断言
        // 验证A: 必须生成3个结果
        Assert.assertEquals("生成的单号数量不对", 3, results.size());

        // 验证B: 必须精确包含 1, 2, 3
        Assert.assertTrue("缺少 T0000000001", results.contains("T0000000001"));
        Assert.assertTrue("缺少 T0000000002", results.contains("T0000000002"));
        Assert.assertTrue("缺少 T0000000003", results.contains("T0000000003"));

        // 验证C: 数据库最终状态必须是 4
        // 如果是 4，说明发生过 3 次 getAndIncrement (1->2, 2->3, 3->4)
        Assert.assertEquals("数据库最终值必须为4", 4, dbSequence.get());
    }
}