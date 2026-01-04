package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【核心注解】异步执行标记
 * <p>
 * 作用：
 * 1. 配合 @EventListener 使用时，表示该监听器将在 VthreadPool 中异步执行。
 * 2. 避免耗时操作（如发邮件、写大文件）阻塞主业务线程。
 *
 * @author qianye
 * @create 2025-12-12 10:17
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Async {
}
