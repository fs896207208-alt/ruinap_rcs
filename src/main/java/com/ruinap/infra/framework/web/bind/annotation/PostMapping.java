package com.ruinap.infra.framework.web.bind.annotation;

import java.lang.annotation.*;

/**
 * 【Web注解】POST 请求映射
 * <p>
 * 作用：这是 @RequestMapping(method = RequestMethod.POST) 的快捷方式。
 * 专门用于处理 HTTP POST 请求（通常用于新增或修改数据）。
 *
 * @author qianye
 * @create 2025-12-15 17:36
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PostMapping {
    /**
     * 映射路径
     */
    String value() default "";
}
