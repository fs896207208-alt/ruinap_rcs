package com.ruinap.infra.framework.aop;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.config.AopFeatureControl;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * 【核心切面】通用异步执行器
 * 生命周期：IoC 托管模式
 *
 * @author qianye
 * @create 2025-12-12 13:51
 */
@Aspect
@Component
public class AsyncExecutionAspect {

    @Autowired
    private VthreadPool vthreadPool;

    @Around("@annotation(com.ruinap.infra.framework.annotation.Async)")
    public Object interceptAsync(ProceedingJoinPoint joinPoint) throws Throwable {

        // 1. 全局开关检查 (配合 @EnableAsync)
        if (!AopFeatureControl.isAsyncEnabled()) {
            return joinPoint.proceed();
        }

        // 2. 依赖检查
        if (vthreadPool == null || vthreadPool.getExecutor() == null) {
            RcsLog.sysLog.warn("VthreadPool 未就绪，降级为同步执行");
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();
        Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();

        // === 场景 A: void ===
        if (returnType == void.class) {
            vthreadPool.execute(() -> {
                try {
                    joinPoint.proceed();
                } catch (Throwable e) {
                    RcsLog.sysLog.error("异步方法 [{}] 异常", methodName, e);
                }
            });
            return null;
        }

        // === 场景 B: CompletableFuture ===
        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            return vthreadPool.supplyAsync(() -> {
                try {
                    Object result = joinPoint.proceed();
                    if (result instanceof CompletableFuture) {
                        return ((CompletableFuture<?>) result).join();
                    }
                    return result;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // === 场景 C: Future ===
        if (Future.class.isAssignableFrom(returnType)) {
            return vthreadPool.submit(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        }

        RcsLog.sysLog.error("异步方法必须返回 void 或 Future: {}", methodName);
        return null;
    }
}
