package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 开启声明式事务管理
 *
 * @author qianye
 * @create 2025-12-12 13:46
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableTransaction {
}
