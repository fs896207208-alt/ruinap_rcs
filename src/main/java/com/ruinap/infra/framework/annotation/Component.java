package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【核心注解】通用组件标识
 * <p>
 * 作用：告诉框架，这个类需要被管理，请把它实例化并放入容器中。
 * 等同于 Spring 的 @Component。
 *
 * @author qianye
 * @create 2025-12-10 15:30
 */
@Target(ElementType.TYPE) // 只能用在类上
@Retention(RetentionPolicy.RUNTIME) // 运行时通过反射可见
@Documented
public @interface Component {
    /**
     * 组件名称（目前框架暂未使用，预留字段）
     */
    String value() default "";
}
