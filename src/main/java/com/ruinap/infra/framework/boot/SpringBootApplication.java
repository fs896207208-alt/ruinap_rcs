package com.ruinap.infra.framework.boot;

import com.ruinap.infra.framework.annotation.ComponentScan;

import java.lang.annotation.*;

/**
 * 【核心注解】应用主入口
 * <p>
 * 作用：
 * 1. 标识这是主启动类。
 * 2. 隐式包含 @ComponentScan，默认扫描该类所在包及子包。
 * 3. 单元测试会向上查找此注解，自动确定扫描范围。
 *
 * @author qianye
 * @create 2025-12-11 13:26
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
// 核心：继承扫描能力
@ComponentScan
public @interface SpringBootApplication {
}
