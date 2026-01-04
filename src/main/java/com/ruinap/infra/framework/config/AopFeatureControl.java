package com.ruinap.infra.framework.config;

/**
 * 【核心配置】AOP 功能全局开关
 * <p>
 * 作用：统一管控 AOP 切面是否生效。
 * 配合 @EnableAsync, @EnableTransaction 等注解使用。
 *
 * @author qianye
 * @create 2025-12-12 11:09
 */
public class AopFeatureControl {

    private static volatile boolean enableAsync = false;
    private static volatile boolean enableTransaction = false;

    /**
     * 启用异步功能
     * 该方法将enableAsync标志设置为true，表示启用异步处理功能
     */
    public static void enableAsync() {
        enableAsync = true;
    }

    /**
     * 检查异步功能是否已启用
     *
     * @return boolean 异步功能启用状态，true表示已启用，false表示未启用
     */
    public static boolean isAsyncEnabled() {
        return enableAsync;
    }


    /**
     * 启用事务功能
     * 该方法将enableTransaction标志设置为true，表示启用事务处理功能
     */
    public static void enableTransaction() {
        enableTransaction = true;
    }

    /**
     * 检查事务功能是否已启用
     *
     * @return boolean 事务功能启用状态，true表示已启用，false表示未启用
     */
    public static boolean isTransactionEnabled() {
        return enableTransaction;
    }
}
