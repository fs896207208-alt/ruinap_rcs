package com.ruinap.infra.framework.test;

import com.ruinap.infra.framework.boot.SpringBootApplication;
import com.ruinap.infra.framework.core.AnnotationConfigApplicationContext;
import com.ruinap.infra.log.RcsLog;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.reflections.Reflections;

import java.util.Set;

/**
 * 【仿真运行器】集成 JUnit Jupiter 与 LCLM 容器
 * <p>
 * <strong>作用：</strong><br>
 * 接管 JUnit 的测试执行流程。它做两件事：<br>
 * 1. 启动 IoC 容器 (AnnotationConfigApplicationContext)。<br>
 * 2. 拦截测试类的创建，将容器中的依赖注入到测试类中 (@Autowired)。
 * </p>
 *
 * @author qianye
 * @create 2025-12-11 13:15
 */
public class SpringRunner implements BeforeAllCallback, TestInstancePostProcessor {

    /**
     * 全局静态容器，模拟 Spring TestContext 的缓存机制
     * 使用 volatile + synchronized 确保并发测试时的线程安全
     */
    private static volatile AnnotationConfigApplicationContext context;
    private static final Object STARTUP_LOCK = new Object();

    /**
     * 【阶段1：容器初始化】
     * 在当前测试类的所有测试方法执行前运行。
     * 负责寻找主配置类并启动 IoC 容器。
     */
    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        // 双重检查锁定 (Double-Check Locking) 确保容器只启动一次
        if (context == null) {
            synchronized (STARTUP_LOCK) {
                if (context == null) {
                    RcsLog.consoleLog.info("====== [JUnit 6] 正在启动 RCS 仿真容器 ======");

                    // 获取当前运行的测试类
                    Class<?> testClass = extensionContext.getRequiredTestClass();

                    // 定位主配置类
                    Class<?> mainConfigClass = locateMainConfigClass(testClass);

                    // 启动容器
                    long start = System.currentTimeMillis();
                    context = new AnnotationConfigApplicationContext(mainConfigClass);
                    long end = System.currentTimeMillis();

                    RcsLog.consoleLog.info("====== [JUnit 6] 容器启动完成，耗时: {} ms ======", (end - start));
                }
            }
        }
    }

    /**
     * 【阶段2：依赖注入】
     * 在每个测试实例创建后立即执行。
     * 将容器中的 Bean 注入到测试类的 @Autowired 字段中。
     */
    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext) throws Exception {
        if (context != null) {
            // 利用容器已有的能力进行注入
            context.autowireBean(testInstance);
            RcsLog.consoleLog.debug("[SpringRunner] 已完成测试实例注入: {}", testInstance.getClass().getSimpleName());
        } else {
            throw new IllegalStateException("容器未启动，无法进行依赖注入！请检查 @SpringBootTest 配置。");
        }
    }

    /**
     * 【魔法逻辑】自动定位 @SpringBootApplication 启动类
     * (逻辑保持不变，适配 JUnit 6 上下文)
     */
    private Class<?> locateMainConfigClass(Class<?> testClass) {
        // 1. 优先检查 @SpringBootTest 注解中是否显式指定了 classes
        // 注意：这里需要反射获取注解，因为 SpringRunner 不直接依赖注解类（解耦）
        if (testClass.isAnnotationPresent(SpringBootTest.class)) {
            SpringBootTest testConfig = testClass.getAnnotation(SpringBootTest.class);
            if (testConfig.classes().length > 0) {
                return testConfig.classes()[0];
            }
        }

        // 2. 自动向上扫描包路径
        String packageName = testClass.getPackage().getName();

        // 循环向上查找，直到根包
        while (packageName.contains(".")) {
            // 使用 Reflections 扫描当前包下的 @SpringBootApplication
            try {
                Reflections reflections = new Reflections(packageName);
                Set<Class<?>> mainClasses = reflections.getTypesAnnotatedWith(SpringBootApplication.class);

                if (!mainClasses.isEmpty()) {
                    // 找到了！
                    Class<?> found = mainClasses.iterator().next();
                    RcsLog.consoleLog.debug("[SpringRunner] 自动定位到主配置类: {}", found.getName());
                    return found;
                }
            } catch (Exception e) {
                // 忽略扫描异常，继续向上
            }

            // 截断最后一级包名 (com.ruinap.test -> com.ruinap)
            int lastDotIndex = packageName.lastIndexOf('.');
            if (lastDotIndex != -1) {
                packageName = packageName.substring(0, lastDotIndex);
            } else {
                break;
            }
        }

        throw new RuntimeException("\n[SpringRunner 错误] 无法找到 @SpringBootApplication 主配置类！\n" +
                "解决方案：\n" +
                "1. 确保你的项目中有一个类标记了 @SpringBootApplication\n" +
                "2. 确保测试类在启动类的子包下\n" +
                "3. 或者在 @SpringBootTest(classes = RcsApplication.class) 中显式指定");
    }
}
