package com.ruinap.infra.framework.core;

import com.ruinap.infra.framework.core.event.ApplicationEventPublisher;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 【核心接口】应用上下文
 * <p>
 * 它是框架的“大管家”，负责管理所有的 Bean（组件）。
 * 外部代码可以通过它获取对象，或者控制容器的启动和关闭。
 *
 * @author qianye
 * @create 2025-12-10 15:59
 */
public interface ApplicationContext extends ApplicationEventPublisher {
    /**
     * 根据类型获取 Bean 实例
     *
     * @param requiredType Bean 的 Class 类型
     * @return Bean 实例
     */
    <T> T getBean(Class<T> requiredType);

    /**
     * 根据类型获取所有 Bean
     *
     * @param type 接口或父类类型
     * @return Map<BeanName, BeanInstance>
     */
    <T> Map<String, T> getBeansOfType(Class<T> type);

    /**
     * 根据注解获取所有 Bean 实例
     *
     * @param annotationType 注解类型
     * @return Bean 实例
     */
    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType);

    /**
     * 启动/刷新容器
     * 触发扫描、实例化、注入、初始化的全过程
     */
    void refresh();

    /**
     * 关闭容器
     * 触发所有 Bean 的销毁逻辑
     */
    void close();

    /**
     * 注册 JVM 关闭钩子
     * 确保程序被 kill 或 Ctrl+C 关闭时，能优雅地释放资源
     */
    void registerShutdownHook();
}
