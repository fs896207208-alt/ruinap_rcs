package com.ruinap.infra.framework.annotation;

import java.lang.annotation.*;

/**
 * 【生命周期】销毁回调
 * <p>
 * 作用：当容器关闭（程序退出）前，自动执行此方法。
 * 适合做资源释放（如：断开连接、停止线程池）。
 *
 * @author qianye
 * @create 2025-12-10 15:33
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface PreDestroy {
}
