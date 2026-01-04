package com.ruinap.infra.framework.schedule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 基于ScheduledExecutorService的定时任务注解
 * 支持更多时间单位，最小单位为纳秒，最大单位为天
 *
 * @author qianye
 * @create 2024-09-09 16:13
 */
// 运行时保留
@Retention(RetentionPolicy.RUNTIME)
// 只能用于方法
@Target(ElementType.METHOD)
public @interface RcsScheduled {

    /**
     * 使用定时器配置，如果配置了则使用定时器的配置，否则使用注解的定时器
     *
     * @return 定时器配置
     */
    String configKey() default "";

    /**
     * 是否开启异步
     * <p>
     * false 同步执行 true 异步执行
     */
    boolean async() default true;

    /**
     * 初始延迟时间
     *
     * @return 延迟时间
     */
    long delay() default 0;

    /**
     * 上次执行终止和下次执行开始的间隔
     *
     * @return 间隔时间
     */
    long period() default 1;

    /**
     * 时间单位（默认秒）
     * <p>
     * 支持以下时间单位：
     * <p>
     * NANOSECONDS 纳秒
     * <p>
     * MICROSECONDS 微秒
     * <p>
     * MILLISECONDS 毫秒
     * <p>
     * SECONDS 秒
     * <p>
     * MINUTES 分钟
     * <p>
     * HOURS 小时
     * <p>
     * DAYS 天
     *
     * @return 单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
