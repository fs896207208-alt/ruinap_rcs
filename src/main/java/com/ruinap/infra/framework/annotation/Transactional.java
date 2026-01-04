package com.ruinap.infra.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事务注解
 * 定义一个SQL的事务管理的注解，用于标记在业务逻辑中需要进行事务控制的方法
 *
 * @author qianye
 * @create 2024-10-12 23:51
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transactional {
}
