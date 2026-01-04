package com.ruinap.infra.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * HTTP地址注解
 * 用于标记类，指定根路径
 *
 * @author qianye
 * @create 2024-08-10 2:41
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpURL {
    String value();
}
