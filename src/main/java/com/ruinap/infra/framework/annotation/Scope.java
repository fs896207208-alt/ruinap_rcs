package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * * 【核心注解】Bean 作用域标识，写在类上
 * * <p>
 * * 用于标记 Bean 是单例还是多例。
 * * 可选值参考 {@link com.ruinap.infra.framework.config.ConfigurableBeanFactory}
 * * </p>
 *
 * @author qianye
 * @create 2026-01-30 15:39
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scope {
    /**
     * 作用域名称，默认为单例 (singleton)
     */
    String value() default "singleton";
}
