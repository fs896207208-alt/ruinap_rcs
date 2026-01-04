package com.ruinap.infra.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * HTTP Get请求
 * 用于标记方法，指定操作路径
 *
 * @author qianye
 * @create 2024-08-10 2:42
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpGet {
    String value();
}
