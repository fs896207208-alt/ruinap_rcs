package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【配置注解】组件扫描
 * <p>
 * 1. 类似 Spring 的 @ComponentScan。
 * 2. 如果 value 和 basePackages 为空，默认扫描当前类所在的包。
 *
 * @author qianye
 * @create 2025-12-11 10:08
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ComponentScan {
    /**
     * 扫描路径 (value 和 basePackages 互为别名)
     */
    String[] value() default {};

    /**
     * 扫描路径
     */
    String[] basePackages() default {};
}
