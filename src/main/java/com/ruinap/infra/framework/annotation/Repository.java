package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【语义注解】数据访问层组件
 * <p>
 * 作用：标识这是一个与数据库交互的类（DAO）。
 * 等同于 Spring 的 @Repository。
 *
 * @author qianye
 * @create 2025-12-10 15:46
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Repository {
    String value() default "";
}
