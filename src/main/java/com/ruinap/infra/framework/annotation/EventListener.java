package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【核心注解】事件监听器
 * <p>
 * 作用：标记在方法上，表示该方法是一个事件处理器。
 * 框架会自动识别参数类型，当对应类型的事件发布时，自动调用此方法。
 *
 * @author qianye
 * @create 2025-12-12 09:50
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
}
