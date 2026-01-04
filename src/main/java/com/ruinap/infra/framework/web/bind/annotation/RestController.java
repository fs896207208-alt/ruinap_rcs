package com.ruinap.infra.framework.web.bind.annotation;

import com.ruinap.infra.framework.annotation.Component;

import java.lang.annotation.*;

/**
 * @author qianye
 * @create 2025-12-15 18:04
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component // 具备组件功能，能被 IOC 扫描
public @interface RestController {
    String value() default "";
}
