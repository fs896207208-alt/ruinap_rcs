package com.ruinap.infra.framework.event;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.framework.test.SpringRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author qianye
 * @create 2025-12-12 10:32
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class AgvBusinessTest {

    @Autowired
    private AgvTaskService agvTaskService;

    @Before
    public void setup() {
        ExternalListeners.LOGS.clear();
    }

    /**
     * 测试场景：任务开始 (EXECUTING)
     * 预期：WMS 和 Web 收到通知 (异步)，WCS 不收 (因为不是 COMPLETED)
     */
    @Test
    public void testTaskStart() throws InterruptedException {
        System.out.println("=== 测试场景：任务开始 ===");

        // 只有 2 个异步监听器会被触发
        ExternalListeners.ASYNC_LATCH = new CountDownLatch(2);

        agvTaskService.updateTaskStatus("T001", "AGV_1", AgvTaskEvent.TaskStatus.EXECUTING, "出发");

        // 等待异步完成
        boolean ok = ExternalListeners.ASYNC_LATCH.await(2, TimeUnit.SECONDS);
        Assert.assertTrue("异步任务超时", ok);

        // 验证日志数量
        Assert.assertEquals(2, ExternalListeners.LOGS.size());

        // 验证异步执行 (检查线程名包含 vt-)
        // 注意：ExternalListeners.LOGS 里的字符串格式是 "WMS收到:xxx|线程:vt-xx"
        boolean hasVirtualThread = ExternalListeners.LOGS.stream()
                .anyMatch(log -> log.contains("vt-"));
        Assert.assertTrue("应该在虚拟线程中执行", hasVirtualThread);

        System.out.println("=== 通过 ===");
    }

    /**
     * 测试场景：任务完成 (COMPLETED)
     * 预期：WMS, Web, WCS 全部收到
     */
    @Test
    public void testTaskComplete() throws InterruptedException {
        System.out.println("=== 测试场景：任务完成 ===");

        // 2 个异步监听器
        ExternalListeners.ASYNC_LATCH = new CountDownLatch(2);

        agvTaskService.updateTaskStatus("T001", "AGV_1", AgvTaskEvent.TaskStatus.COMPLETED, "到站");

        // WCS 是同步的，所以这行代码执行完，WCS 的日志应该已经在 list 里了
        // 我们可以先断言 WCS
        boolean wcsReceived = ExternalListeners.LOGS.stream().anyMatch(l -> l.contains("WCS收到"));
        Assert.assertTrue("WCS 应该同步收到消息", wcsReceived);

        // 再等待异步的 WMS 和 Web
        ExternalListeners.ASYNC_LATCH.await(2, TimeUnit.SECONDS);

        // 总共应该有 3 条日志
        Assert.assertEquals(3, ExternalListeners.LOGS.size());

        System.out.println("=== 通过 ===");
    }
}
