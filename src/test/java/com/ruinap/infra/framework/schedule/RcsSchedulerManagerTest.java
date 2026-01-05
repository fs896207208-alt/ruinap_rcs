package com.ruinap.infra.framework.schedule;

import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.config.pojo.CoreConfig;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;

/**
 * RcsSchedulerManager 集成测试
 * <p>
 * 核心策略：
 * 1. 启动真实容器，让 @Component 自动扫描。
 * 2. 使用反射 (Reflection) 动态修改 CoreYaml 的静态配置，模拟 rcs_core.yaml 的内容。
 * 3. 验证任务是否真的被提交到了 VthreadPool 并执行。
 *
 * @author qianye
 * @create 2025-12-12 17:46
 */
@SpringBootTest
@DisplayName("调度管理器(RcsScheduler)集成测试")
public class RcsSchedulerManagerTest {

    @Autowired
    private RcsSchedulerManager schedulerManager;

    @Test
    @DisplayName("测试：动态任务调度")
    public void testTaskScheduling() throws Exception {
        System.out.println(">>> [Test] 等待任务执行 (sleep 1s)...");

        // 1. 动态修改配置 (模拟从 YAML 读取)
        mockCoreYamlStatic();

        // 3. 观察控制台输出
        // 预期：RcsSchedulerManager 会扫描到 @RcsScheduled 注解的方法，并开始周期性打印日志
        Thread.sleep(1000);

        System.out.println("-------------------------------------");
        System.out.println("           请检查上方日志             ");
        System.out.println(" 预期: [DynamicTimer] 任务被执行... ");
        System.out.println("---------------- 结果 ----------------");
        System.out.println("-------------------------------------");
    }

    /**
     * 静态 Mock 方法 (严格保留原反射逻辑)
     */
    private static void mockCoreYamlStatic() throws Exception {
        CoreConfig coreConfig = new CoreConfig();

        // 1. Timer Config
        LinkedHashMap<String, String> timerProps = new LinkedHashMap<>();
        timerProps.put("enabled", "true");
        timerProps.put("period", "100");
        timerProps.put("unit", "MILLISECONDS");
        timerProps.put("async", "true");
        LinkedHashMap<String, LinkedHashMap<String, String>> timerCommon = new LinkedHashMap<>();
        timerCommon.put("testDynamicTimer", timerProps);
        coreConfig.setRcsTimer(timerCommon);

        // 2. ThreadPool Config (防止 VthreadPool 初始化崩溃)
        LinkedHashMap<String, Integer> threadPool = new LinkedHashMap<>();
        threadPool.put("core_pool_size", 10);
        threadPool.put("max_pool_size", 50);
        threadPool.put("work_queue", 100);
        coreConfig.setRcsThreadPool(threadPool);

        // 3. Others
        coreConfig.setRcsSys(new LinkedHashMap<>());
        coreConfig.setRcsPort(new LinkedHashMap<>());
        coreConfig.setAlgorithmCommon(new LinkedHashMap<>());

        Field configField = CoreYaml.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, coreConfig);
    }
}