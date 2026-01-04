package com.ruinap.infra.framework.schedule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基于Cron表达式的定时任务注解
 * <p>
 * 定时任务是一种时间表达式，用于指定定期执行任务的时间规则
 * <p>
 * 最小时间单位：秒
 * <p>
 * 定时任务采用的Quartz Cron表达式，详解请看：<a href="https://zhuanlan.zhihu.com/p/624552208">https://zhuanlan.zhihu.com/p/624552208</a>
 * <p>
 * 在线Cron表达式生成器：<a href="https://www.bejson.com/othertools/cron/">https://www.bejson.com/othertools/cron/</a>
 * <p>
 * Cron表达式校验工具：<a href="https://www.bejson.com/othertools/cronvalidate/">https://www.bejson.com/othertools/cronvalidate/</a>
 *
 * @author qianye
 * @create 2024-08-27 4:05
 */
// 运行时保留
@Retention(RetentionPolicy.RUNTIME)
// 只能用于方法
@Target(ElementType.METHOD)
public @interface RcsCron {

    /**
     * 是否开启异步
     * <p>
     * false 同步执行 true 异步执行
     */
    boolean async() default true;

    /**
     * Cron表达式，默认值为1秒执行一次的表达式
     *
     * @return
     */
    String value() default "0/1 * * * * ?";
}
