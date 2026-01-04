package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【控制注解】启动顺序控制
 * <p>
 * 作用：决定组件的加载或执行顺序。
 * 规则：数值越小，优先级越高（越先启动）。
 * <p>
 * 示例：
 * DatabaseService @Order(0)  -> 最先启动
 * NettyServer    @Order(100) -> 稍后启动
 *
 * @author qianye
 * @create 2025-12-10 15:32
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Order {
    int value() default Integer.MAX_VALUE;
}
