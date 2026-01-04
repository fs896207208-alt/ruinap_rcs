package com.ruinap.infra.framework.test;

import java.lang.annotation.*;

/**
 * @author qianye
 * @create 2025-12-11 13:13
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface SpringBootTest {
    /**
     * 指定启动类或配置类
     */
    Class<?>[] classes() default {};
}
