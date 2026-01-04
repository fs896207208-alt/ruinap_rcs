package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 开启异步执行能力
 *
 * @author qianye
 * @create 2025-12-12 13:44
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableAsync {
}
