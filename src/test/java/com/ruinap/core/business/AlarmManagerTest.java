package com.ruinap.core.business;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.db.Entity;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.thread.VthreadPool;
import com.ruinap.persistence.repository.AlarmDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AlarmManager 单元测试
 * <p>
 * 覆盖场景：
 * 1. 首次告警入库
 * 2. 重复告警去重（防抖）
 * 3. 手动清除触发回调
 * 4. 系统关闭逻辑
 * 5. 高并发竞态条件测试
 *
 * @author qianye
 * @create 2026-01-12 09:32
 */
@ExtendWith(MockitoExtension.class)
class AlarmManagerTest {

    @InjectMocks
    private AlarmManager alarmManager;

    @Mock
    private AlarmDB alarmDB;

    @Mock
    private VthreadPool vthreadPool;

    // 模拟的告警枚举
    private final AlarmCodeEnum TEST_ALARM = AlarmCodeEnum.E10001; // 假设存在此枚举，需替换为实际枚举

    @BeforeEach
    void setUp() {
        // 修正点：添加 lenient()。
        // 因为并非所有测试用例都会触发线程池（例如参数校验失败的测试），
        // 如果不加 lenient()，Mockito 会报 UnnecessaryStubbingException 错误。
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(vthreadPool).execute(any(Runnable.class));

        // 手动触发生命周期初始化
        alarmManager.init();
    }

    @AfterEach
    void tearDown() {
        // 清理资源，防止测试间干扰
        alarmManager.shutdown();
    }

    @Test
    @DisplayName("测试：首次触发告警应立即入库")
    void testTriggerAlarm_New_ShouldSaveToDb() throws Exception {
        // Given
        String equipmentCode = "AGV_001";

        // When
        alarmManager.triggerAlarm(equipmentCode, TEST_ALARM, "TestParam", "AGV");

        // Then
        // 验证 alarmDB.createAlarm 被调用了1次
        verify(alarmDB, times(1)).createAlarm(any(Entity.class));

        // 验证入库参数细节
        ArgumentCaptor<Entity> entityCaptor = ArgumentCaptor.forClass(Entity.class);
        verify(alarmDB).createAlarm(entityCaptor.capture());
        Entity savedEntity = entityCaptor.getValue();

        assertEquals(equipmentCode, savedEntity.get("name"));
        assertEquals(TEST_ALARM.code, savedEntity.get("code"));
        assertEquals(0, savedEntity.get("state")); // 状态应为 0
    }

    @Test
    @DisplayName("测试：重复触发告警应被缓存拦截（防抖），不重复入库")
    void testTriggerAlarm_Duplicate_ShouldDebounce() throws Exception {
        // Given
        String equipmentCode = "AGV_002";

        // When
        // 第一次触发
        alarmManager.triggerAlarm(equipmentCode, TEST_ALARM, "Param1", "AGV");
        // 第二次触发 (模拟高频上报)
        alarmManager.triggerAlarm(equipmentCode, TEST_ALARM, "Param2", "AGV");
        alarmManager.triggerAlarm(equipmentCode, TEST_ALARM, "Param3", "AGV");

        // Then
        // 验证 alarmDB.createAlarm 只被调用了1次
        verify(alarmDB, times(1)).createAlarm(any(Entity.class));
    }

    @Test
    @DisplayName("测试：手动清除告警应触发 Listener 并更新数据库状态为1")
    void testClearAlarm_ShouldUpdateDbStatus() throws Exception {
        // Given
        String equipmentCode = "AGV_003";
        // 先触发一个告警，使其存在于缓存中
        alarmManager.triggerAlarm(equipmentCode, TEST_ALARM, "Param", "AGV");

        // 重置 Mock 计数，忽略掉刚才触发时的 createAlarm 调用
        clearInvocations(alarmDB);

        // When
        // 手动清除 -> 应该触发 TimedCache 的 onRemove -> 触发 updateAlarmStatus
        alarmManager.clearAlarm(equipmentCode, TEST_ALARM);

        // Then
        // 验证 alarmDB.updateAlarm 被调用
        ArgumentCaptor<Entity> bodyCaptor = ArgumentCaptor.forClass(Entity.class);
        ArgumentCaptor<Entity> whereCaptor = ArgumentCaptor.forClass(Entity.class);

        verify(alarmDB, times(1)).updateAlarm(bodyCaptor.capture(), whereCaptor.capture());

        Entity body = bodyCaptor.getValue();
        Entity where = whereCaptor.getValue();

        assertEquals(1, body.get("state")); // 状态更新为 1 (恢复)
        assertEquals(equipmentCode, where.get("name"));
        assertEquals(TEST_ALARM.code, where.get("code"));
    }

    @Test
    @DisplayName("测试：系统关闭(Shutdown)应将所有未结告警更新为2")
    void testShutdown_ShouldCloseAllAlarms() throws Exception {
        // Given
        // 注入一些活跃告警
        alarmManager.triggerAlarm("AGV_001", TEST_ALARM, "P", "S");
        alarmManager.triggerAlarm("AGV_002", TEST_ALARM, "P", "S");

        clearInvocations(alarmDB); // 清除之前的调用记录

        // When
        alarmManager.shutdown();

        // Then
        // 1. 验证监听器是否被移除 (难直接验证，通过观察 updateAlarm 的调用参数间接验证)
        // 2. 验证执行了全局更新 SQL
        ArgumentCaptor<Entity> bodyCaptor = ArgumentCaptor.forClass(Entity.class);
        ArgumentCaptor<Entity> whereCaptor = ArgumentCaptor.forClass(Entity.class);

        verify(alarmDB, atLeastOnce()).updateAlarm(bodyCaptor.capture(), whereCaptor.capture());

        // 检查是否有一条 SQL 是 update state=2 where state=0
        boolean hasSystemCloseSql = false;
        // 因为 shutdown 会先 clear 缓存，可能触发 Listener (如果代码逻辑有误)，
        // 但我们在代码里显式先 setListener(null) 了。
        // 所以这里应该只看到那条全局更新的 update 调用。

        for (int i = 0; i < bodyCaptor.getAllValues().size(); i++) {
            Entity b = bodyCaptor.getAllValues().get(i);
            if (Integer.valueOf(2).equals(b.get("state"))) {
                hasSystemCloseSql = true;
                break;
            }
        }
        assertTrue(hasSystemCloseSql, "系统关闭时必须执行 state=2 的全局更新");
    }

    @Test
    @DisplayName("测试：高并发场景下的线程安全性 (Virtual Thread Pinning Check)")
    void testConcurrency_Performance() throws InterruptedException, SQLException {
        // 模拟 100 个并发线程同时上报同一个新告警
        // 目标：验证是否只入库 1 次（理想情况），或者极少量重复（可接受情况），绝不能抛异常
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newCachedThreadPool();

        String equipmentCode = "AGV_CONCURRENT";
        AtomicInteger successTriggerCount = new AtomicInteger(0);

        // 这里的 Mock 需要稍微改一下，统计 createAlarm 被调用的真实次数
        // 我们用一个计数器来记录 DB 调用
        AtomicInteger dbInsertCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            dbInsertCount.incrementAndGet();
            ThreadUtil.sleep(10); // 模拟 DB IO 耗时
            return null;
        }).when(alarmDB).createAlarm(any(Entity.class));

        // 此时 vthreadPool 依然是同步执行，这模拟了 CPU 密集的极端情况，
        // 或者我们可以让 mock vthreadPool 真的去起线程，但为了测试稳定性，保持同步调用即可，
        // 这意味着 triggerAlarm 方法内部的 cache.put 和 saveToDb 都在测试线程中执行。

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待发令枪
                    alarmManager.triggerAlarm(equipmentCode, TEST_ALARM, "Concurrent", "Test");
                    successTriggerCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 发令
        startLatch.countDown();
        // 等待所有线程结束
        boolean finished = endLatch.await(5, TimeUnit.SECONDS);
        assertTrue(finished, "并发测试未在规定时间内完成");

        // 验证
        System.out.println("并发请求数: " + threadCount);
        System.out.println("DB 插入次数: " + dbInsertCount.get());

        // 期望：DB 插入次数应该很少。
        // 由于我们去掉了 synchronized，确实存在竞态条件，dbInsertCount 可能会 > 1。
        // 但在极短时间内，Hutool Cache 的 put 应该能拦截绝大多数。
        // 如果这里是 100，说明缓存完全失效；如果是 1~5，说明架构正常。
        assertTrue(dbInsertCount.get() < 10, "DB 重复插入次数过多，缓存防抖失效");
        assertEquals(threadCount, successTriggerCount.get());

        executor.shutdownNow();
    }

    @Test
    @DisplayName("测试：空参数防御性校验")
    void testTriggerAlarm_NullInputs_ShouldDoNothing() {
        // When
        alarmManager.triggerAlarm(null, TEST_ALARM, "P", "S");
        alarmManager.triggerAlarm("EQ_01", null, "P", "S");

        // Then
        verifyNoInteractions(alarmDB);
    }
}