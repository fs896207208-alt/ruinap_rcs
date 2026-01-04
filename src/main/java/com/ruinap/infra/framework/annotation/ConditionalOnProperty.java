package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【条件注解】基于配置属性控制 Bean 是否加载
 * <p>
 * 只有当 Environment 中存在指定属性且值匹配时，才实例化该 Bean。
 *
 * @author qianye
 * @create 2025-12-24
 */
@Retention(RetentionPolicy.RUNTIME)
// 支持类和方法
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
public @interface ConditionalOnProperty {

    /**
     * 属性名 (支持扁平化路径)
     * 例: "server.port" 或 "rcs_db.db_ip" 或 "security.whitelist[0]"
     */
    String name();

    /**
     * 期望值
     * 1. 如果为空 (默认): 只要属性存在且不等于 "false"，即视为匹配。
     * 2. 如果指定了值: 必须全等匹配 (忽略大小写)。
     */
    String havingValue() default "";

    /**
     * 当属性不存在时，是否匹配
     * false: 属性必须存在才能加载 (默认)
     * true: 属性不存在也加载 (缺失容忍)
     */
    boolean matchIfMissing() default false;
}
