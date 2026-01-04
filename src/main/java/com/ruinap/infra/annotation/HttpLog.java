package com.ruinap.infra.annotation;

import java.lang.annotation.*;

/**
 * Http日志注解
 *
 * @author qianye
 * @create 2024-08-10 3:26
 */
@Target(ElementType.METHOD)//注解作用范围(这里使用方法级别)
@Retention(RetentionPolicy.RUNTIME)//注解在方法运行阶段执行
@Documented//指明修饰的注解，可以被例如javadoc此类的工具文档化，只负责标记，没有成员取值
public @interface HttpLog {
}
