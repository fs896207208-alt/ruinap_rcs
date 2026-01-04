package com.ruinap.infra.framework.aop;

import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 全局异常日志切面
 * <p>
 * 作用：拦截所有未捕获的运行时异常并打印到系统日志。
 * 注意：排除了 AOP 框架层和注解层的代码，防止循环拦截。
 * </p>
 *
 * @author qianye
 * @create 2025-12-04 15:42
 */
@Aspect
@Component
public class RcsLogAspect {

    /**
     * 切点定义：
     * 1. 拦截 com.ruinap 包下所有方法
     * 2. 【关键】排除 framework.aop 包 (防止拦截 事务切面 和 异步切面)
     * 3. 排除 framework.annotation 包
     */
    @Pointcut("execution(* com.ruinap..*.*(..)) " +
            "&& !within(com.ruinap.infra.framework.aop..*) " +
            "&& !within(com.ruinap.infra.framework.aop.annotation..*)")
    public void anyRcsMethod() {
    }

    /**
     * 当抛出异常时触发
     */
    @AfterThrowing(pointcut = "anyRcsMethod()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Throwable e) {
        // 过滤掉我们预期的控制流异常（如果需要）
        String methodName = joinPoint.getSignature().toShortString();

        System.err.println("{} Aspect捕获 方法 [{}] 执行异常 " + methodName + e.getMessage());
        // 记录异常到日志
        RcsLog.sysLog.error("{} Aspect捕获 方法 [{}] 执行异常: {}", RcsLog.randomInt(), methodName, e.getMessage(), e);
    }
}
