package com.ruinap.infra.framework.web.bind.annotation;

import java.lang.annotation.*;

/**
 * @author qianye
 * @create 2025-12-15 18:01
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    String value() default "";

    boolean required() default true;
}
