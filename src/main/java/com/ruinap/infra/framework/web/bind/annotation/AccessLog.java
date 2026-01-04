package com.ruinap.infra.framework.web.bind.annotation;

import java.lang.annotation.*;

/**
 *
 * 【Web注解】访问日志记录 (Access Log)
 * <p>
 * 作用：标记在 Controller 方法上。
 * 当接口被调用时，框架会自动打印访问详情（路径、方法、耗时等）。
 * 这是一个典型的 AOP（面向切面编程）应用场景。
 *
 * @author qianye
 * @create 2024-08-10 3:26
 */
@Target(ElementType.METHOD)//注解作用范围(这里使用方法级别)
@Retention(RetentionPolicy.RUNTIME)//注解在方法运行阶段执行
@Documented//指明修饰的注解，可以被例如javadoc此类的工具文档化，只负责标记，没有成员取值
public @interface AccessLog {
    /**
     * 接口描述/业务名称 (例如: "用户登录", "查询详情")
     * 用于在日志中让人类更容易读懂。
     */
    String value() default "";
}
