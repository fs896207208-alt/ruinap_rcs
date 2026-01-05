package com.ruinap.infra.framework.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * @author qianye
 * @create 2025-12-11 13:13
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(SpringRunner.class)
public @interface SpringBootTest {
    /**
     * 指定启动类或配置类
     */
    Class<?>[] classes() default {};
}
