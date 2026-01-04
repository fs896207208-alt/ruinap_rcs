package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【生命周期】初始化回调
 * <p>
 * 作用：当对象被创建，并且所有 @Autowired 依赖都注入完成后，自动执行此方法。
 * 适合做一些初始化操作（如：打开数据库连接、加载缓存）。
 *
 * @author qianye
 * @create 2025-12-10 15:33
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface PostConstruct {
}
