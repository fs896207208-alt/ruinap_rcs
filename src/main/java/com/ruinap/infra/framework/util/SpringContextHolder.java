package com.ruinap.infra.framework.util;

import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.ApplicationContextAware;
import com.ruinap.infra.framework.core.event.ApplicationEvent;
import com.ruinap.infra.log.RcsLog;

/**
 * 【核心桥梁】Spring 上下文静态持有者
 * <p>
 * <strong>设计目的：</strong><br>
 * 用于在【非 Spring 管理的类】（如 Log4j Appender、Utils 工具类、Filter、Listener）中获取容器中的 Bean。
 * </p>
 *
 * <p>
 * <strong>工作原理：</strong><br>
 * 1. 本类也是一个 @Component，会被容器扫描并实例化。<br>
 * 2. 实现了 ApplicationContextAware 接口，容器启动到特定阶段时，会自动调用 setApplicationContext。<br>
 * 3. 我们将拿到的 Context 赋值给静态变量，从而打通了“容器内”和“容器外”的围墙。
 * </p>
 *
 * @author qianye
 * @create 2025-12-11 13:51
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {
    /**
     * 全局唯一的容器引用
     * volatile 确保多线程环境下的可见性
     */
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextHolder.context = applicationContext;
        // 打印一下日志，证明桥梁搭好了
        RcsLog.consoleLog.debug("SpringContextHolder 已成功获取容器引用");
    }

    /**
     * 获取容器实例
     *
     * @return 容器实例，如果容器未启动完成，可能返回 null
     */
    public static ApplicationContext getApplicationContext() {
        return context;
    }

    /**
     * 静态方法：通过类型获取 Bean
     *
     * <h3>🛑 致命警告 (CRITICAL WARNING) - 必须阅读！</h3>
     * <p>
     * <strong>1. 严禁在类加载/静态初始化阶段调用：</strong><br>
     * 绝不要将此方法直接赋值给成员变量或静态变量！<br>
     * 因为类加载（Class Loading）通常早于 Spring 容器启动，此时 {@code context} 仍为 {@code null}。
     * </p>
     *
     * <pre>
     * // ❌ 错误示范 (100% 会导致 NullPointerException)：
     * public class MyUtil {
     *      // 此时容器还没启动，getBean 返回 null，后续调用 pool.submit() 直接崩盘
     *      private static VthreadPool pool = SpringContextHolder.getBean(VthreadPool.class);
     * }
     *
     * // ✅ 正确示范 (懒加载 / 延迟获取)：
     * public class MyUtil1 {
     *      public static void doWork() {
     *          // 只有在方法真正被调用时（此时容器通常已启动完毕）才去获取
     *          VthreadPool pool = SpringContextHolder.getBean(VthreadPool.class);
     *          if (pool != null) {
     *              pool.submit(...);
     *          }
     *      }
     * }
     *
     * public class MyUtil2 {
     *      private VthreadPool vthreadPool;
     *      // 懒加载 / 延迟获取，所有用到vthreadPool的方法都调用此方法
     *      public VthreadPool getVthreadPool() {
     *          if (this.vthreadPool == null) {
     *             this.vthreadPool = SpringContextHolder.getBean(VthreadPool.class);
     *         }
     *         // 依然需要判空（因为当前类可能比容器启动得早）
     *         if (this.vthreadPool == null) {
     *             return;
     *         }
     *      }
     * }
     *
     * </pre>
     *
     * @param requiredType 想要获取的 Bean 的 Class 对象
     * @param <T>          泛型类型
     * @return 对应的 Bean 实例；<br>
     * <strong>如果容器尚未启动完成，或者 Bean 不存在，将返回 {@code null}。</strong><br>
     * 调用方<strong>必须</strong>进行非空判断 (Null Check)。
     */
    public static <T> T getBean(Class<T> requiredType) {
        if (context == null) {
            // 容器还没启动好时，返回 null，调用方需要做防空判断
            return null;
        }
        return context.getBean(requiredType);
    }

    /**
     * 发布事件
     * <p>
     * 允许 POJO（如 RcsPointOccupy）、工具类等非 Bean 对象直接向 Spring 容器发布事件。
     * </p>
     *
     * @param event 事件对象 (必须继承 ApplicationEvent)
     */
    public static void publishEvent(ApplicationEvent event) {
        if (context == null) {
            // 防御性编程：容器未就绪时，记录警告日志，避免抛出空指针中断业务流程
            // 在地图加载初期或单元测试中，这种情况可能发生
            RcsLog.consoleLog.warn("SpringContextHolder 容器未初始化，无法发布事件: {}", event);
            return;
        }
        context.publishEvent(event);
    }
}
