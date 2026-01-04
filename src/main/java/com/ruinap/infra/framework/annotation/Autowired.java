package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【核心注解】依赖自动注入
 * <p>
 * 作用：告诉框架，“请帮我找到这个类型的对象，并赋值给这个变量”。
 * 免去了手动写 `new XxxService()` 或 `Instance.getInstance()` 的麻烦。
 *
 * @author qianye
 * @create 2025-12-10 15:32
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {
    /**
     * 是否必须注入成功。
     * 如果为 true（默认），找不到依赖时会抛出异常，防止程序带病运行。
     */
    boolean required() default true;
}
