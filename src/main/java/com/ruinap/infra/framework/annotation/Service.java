package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【语义注解】业务逻辑层组件
 * <p>
 * 作用：从技术角度看，它就是 @Component。
 * 但使用 @Service 可以让代码语义更清晰，表示这是处理业务逻辑（如 AGV 调度算法）的类。
 *
 * @author qianye
 * @create 2025-12-10 15:42
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component // 元注解：拥有此注解意味着它也是一个 Component
public @interface Service {
    String value() default "";
}
