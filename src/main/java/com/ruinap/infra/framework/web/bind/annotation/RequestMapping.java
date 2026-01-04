package com.ruinap.infra.framework.web.bind.annotation;

import java.lang.annotation.*;

/**
 * 【Web注解】请求路径映射
 * <p>
 * 作用：建立 URL 路径与 Java 类或方法的映射关系。
 * 1. 用在类上：定义该控制器所有接口的“根路径” (Root Path)。
 * 2. 用在方法上：定义具体接口的“子路径”。
 * <p>
 *
 * @author qianye
 * @create 2025-12-15 17:35
 */
// 既可以用在类上，也可以用在方法上
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
    /**
     * 映射路径 (例如: "/user") 默认为空字符串
     */
    String value() default "";
}
