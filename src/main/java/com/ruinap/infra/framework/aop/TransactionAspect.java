package com.ruinap.infra.framework.aop;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.config.AopFeatureControl;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.factory.RcsDSFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 【核心切面】事务管理器 (AspectJ CTW 模式)
 * <p>
 * <strong>架构说明：</strong><br>
 * 1. <strong>编译期织入：</strong> 本类由 ajc 编译器直接织入到业务代码中，性能极高。<br>
 * 2. <strong>IoC 托管：</strong> 虽然是 AspectJ 切面，但通过 {@code @Component} 和容器的 {@code aspectOf} 接管机制，
 * 它完全融入了 Spring 生命周期，支持 {@code @Autowired} 注入。<br>
 * 3. <strong>可插拔：</strong> 通过 {@code AopFeatureControl} 检查开关，配合 {@code @EnableTransaction} 使用。
 * </p>
 *
 * @author qianye
 * @create 2025-12-12 11:09
 */
@Aspect
@Component
public class TransactionAspect {

    @Autowired
    private RcsDSFactory rcsDSFactory;

    /**
     * 环绕通知：拦截所有带 @Transactional 注解的方法
     */
    @Around("@annotation(com.ruinap.infra.framework.annotation.Transactional)")
    public Object manageTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 全局开关检查 (Feature Flag)
        if (!AopFeatureControl.isTransactionEnabled()) {
            return joinPoint.proceed();
        }

        // 获取方法名
        String methodName = joinPoint.getSignature().getName();

        // 2. 依赖可用性检查
        // 由于容器已接管，rcsDSFactory 会被自动注入。
        if (rcsDSFactory == null || rcsDSFactory.db == null) {
            RcsLog.consoleLog.warn(">>> 事务切面: 数据库未就绪，方法 [{}] 跳过事务", methodName);
            return joinPoint.proceed();
        }

        RcsLog.consoleLog.info(">>> 事务切面: 开启事务管理 -> [{}]", methodName);

        // 3. 执行事务模板逻辑
        final Object[] resultHolder = new Object[1];
        try {
            rcsDSFactory.db.tx(db -> {
                try {
                    resultHolder[0] = joinPoint.proceed();
                } catch (Throwable e) {
                    RcsLog.consoleLog.error(">>> 事务切面: 捕获异常，触发回滚 -> [{}] : {}", methodName, e);
                    // 必须抛出 RuntimeException 以触发 Hutool 的回滚
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            }
            throw e;
        }

        return resultHolder[0];
    }
}