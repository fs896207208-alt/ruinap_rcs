package com.ruinap.infra.framework.core;

/**
 * 【Spring 标准接口】上下文感知接口
 * <p>
 * 作用：
 * 任何实现了此接口的 Bean，在容器启动时，
 * 框架会自动调用 setApplicationContext 方法，将容器本身注入进去。
 * <p>
 * 这是获取容器引用的标准“后门”。
 *
 * @author qianye
 * @create 2025-12-11 13:49
 */
public interface ApplicationContextAware {
    void setApplicationContext(ApplicationContext applicationContext);
}
