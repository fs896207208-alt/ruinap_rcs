package com.ruinap.infra.framework.config;

/**
 * Bean 工厂配置常量
 *
 * @author qianye
 * @create 2026-01-30 15:40
 */
public class ConfigurableBeanFactory {
    /**
     * 单例模式 (默认)：容器启动时创建，全局唯一
     */
    public static final String SCOPE_SINGLETON = "singleton";

    /**
     * 原型模式 (多例)：每次获取 Bean 时创建一个新实例
     */
    public static final String SCOPE_PROTOTYPE = "prototype";
}
