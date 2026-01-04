package com.ruinap.infra.framework.config;

/**
 * 【核心配置】定时任务功能开关
 * <p>
 * 作用：管控 @EnableScheduling 是否生效。
 *
 * @author qianye
 * @create 2025-12-12 16:52
 */
public class SchedulingFeatureControl {

    private static volatile boolean enableScheduling = false;

    /**
     * 开启调度器
     */
    public static void enable() {
        enableScheduling = true;
    }

    /**
     * 是否已开启
     */
    public static boolean isEnabled() {
        return enableScheduling;
    }
}
