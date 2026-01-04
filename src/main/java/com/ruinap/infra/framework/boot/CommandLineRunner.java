package com.ruinap.infra.framework.boot;

/**
 * 【启动接口】命令行启动器
 * <p>
 * 作用：这不仅是一个接口，更是一个“约定”。
 * 任何实现了此接口的 Bean，在容器启动完成（所有组件都准备好）后，
 * 会被自动调用 `run` 方法。
 * <p>
 * 它是替代 `public static void main` 中那些乱七八糟初始化代码的最佳位置。
 *
 * @author qianye
 * @create 2025-12-10 15:59
 */
@FunctionalInterface
public interface CommandLineRunner {
    /**
     * 容器启动后执行的业务逻辑
     *
     * @param args 启动参数
     * @throws Exception 允许抛出异常，框架会捕获并记录
     */
    void run(String... args) throws Exception;
}
