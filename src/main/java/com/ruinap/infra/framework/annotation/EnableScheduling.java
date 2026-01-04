package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 开启定时任务调度功能
 * 配合 @RcsCron 和 @RcsScheduled 使用
 *
 * @author qianye
 * @create 2025-12-12 16:48
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableScheduling {
}
