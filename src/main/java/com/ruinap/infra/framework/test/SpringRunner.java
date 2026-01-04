package com.ruinap.infra.framework.test;

import com.ruinap.infra.framework.boot.SpringBootApplication;
import com.ruinap.infra.framework.core.AnnotationConfigApplicationContext;
import com.ruinap.infra.log.RcsLog;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.reflections.Reflections;

import java.util.Set;

/**
 * 【仿真运行器】集成 JUnit 4 与 LCLM 容器
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
public class SpringRunner extends BlockJUnit4ClassRunner {

    /**
     * 全局静态容器，模拟 Spring TestContext 的缓存机制，避免每个 Test 方法都重启容器
     */
    private static AnnotationConfigApplicationContext context;

    public SpringRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
        try {
            // 1. 获取测试配置
            SpringBootTest testConfig = testClass.getAnnotation(SpringBootTest.class);
            Class<?>[] configClasses = null;

            // 2. 决策配置来源
            if (testConfig != null && testConfig.classes().length > 0) {
                // A. 如果用户显式指定了 classes，直接用用户的
                configClasses = testConfig.classes();
            } else {
                // B. 用户没指定，启动“向上查找逻辑”
                Class<?> mainClass = findMainApplicationClass(testClass);
                configClasses = new Class<?>[]{mainClass};
            }

            // 3. 启动容器 (单例模式)
            if (context == null) {
                context = new AnnotationConfigApplicationContext(configClasses);
            }
        } catch (Throwable e) {
            // 🛑 核心修复：JUnit 构造函数中的异常容易被吞掉或只显示 "Test ignored"
            // 必须显式打印堆栈，帮助排查容器启动失败的原因 (如依赖缺失、扫描不到)
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("!!! SpringRunner 初始化失败，请检查配置 !!!");
            e.printStackTrace();
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            // 重新抛出，通知 JUnit 停止运行
            throw new InitializationError(e);
        }
    }

    /**
     * 【核心算法】向上递归查找 @RuinapApplication
     * 模拟 Spring Boot Test 的 ContextLoader 逻辑
     */
    private Class<?> findMainApplicationClass(Class<?> currentClass) {
        String packageName = currentClass.getPackageName();

        // 循环向上遍历包路径
        while (packageName.contains(".")) {
            // 在当前包下扫描带有 @SpringBootApplication 的类
            // 注意：Reflections 默认会扫描子包，为了性能和准确性，我们这里只关心当前层级
            // 但为了简化实现，直接扫包及其子包通常也是可以的，只是要找到最近的一个
            Reflections reflections = new Reflections(packageName);
            Set<Class<?>> mainClasses = reflections.getTypesAnnotatedWith(SpringBootApplication.class);

            if (!mainClasses.isEmpty()) {
                // 找到了！返回第一个找到的（通常项目里只有一个）
                Class<?> found = mainClasses.iterator().next();
                RcsLog.consoleLog.debug("[SpringRunner] 自动定位到主配置类: {}", found.getName());
                return found;
            }

            // 没找到，将包名截断一级 (例如 com.ruinap.test -> com.ruinap)
            int lastDotIndex = packageName.lastIndexOf('.');
            if (lastDotIndex != -1) {
                packageName = packageName.substring(0, lastDotIndex);
            } else {
                break;
            }
        }

        throw new RuntimeException("无法找到 @SpringBootApplication 主配置类！\n请在你的根包下创建一个带有 @SpringBootApplication 的类，或者在测试类上显式指定 @ComponentScan。");
    }

    /**
     * 【魔法核心】拦截测试实例创建
     * <p>
     * JUnit 在运行每个 @Test 方法前，会调用此方法创建测试类实例。<br>
     * 我们重写它，在实例创建后，立刻调用容器的 autowireBean 进行注入。
     * </p>
     */
    @Override
    protected Object createTest() throws Exception {
        // 1. 让 JUnit 正常 new 出测试对象 (此时字段都是 null)
        Object testInstance = super.createTest();

        // 2. 让容器把 @Autowired 的字段填满
        // 如果没有这一步，测试类里的 private Service service 就会是 NPE
        context.autowireBean(testInstance);
        return testInstance;
    }
}
