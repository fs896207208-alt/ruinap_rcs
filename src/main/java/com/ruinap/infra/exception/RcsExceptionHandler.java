package com.ruinap.infra.exception;


import cn.hutool.core.util.StrUtil;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.log.RcsLog;

/**
 * 调度系统异常处理器
 * <p>
 * 上层方法没有捕获异常，那么异常会向上抛出，最终被 RcsExceptionHandler 捕获，并记录到日志中。
 * 线程临死前记录遗言，像飞机的黑匣子。它能记录飞机是因为什么原因坠毁的，但它无法阻止飞机坠毁，也无法让飞机在半空中重新起飞。
 * <p>
 * 这样在后续任何组件启动过程中如果抛出未捕获异常，都能被它接管。
 *
 * @author qianye
 * @create 2024-05-21 14:31
 */
@Component
public class RcsExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * 实现 Runner 接口，在容器启动初期自动执行
     */
    @PostConstruct
    public void run() throws Exception {
        // 设置全局捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
        RcsLog.consoleLog.info("RcsExceptionHandler 全局异常处理器已激活");
    }

    /**
     * 处理未捕获的异常
     *
     * @param t the thread
     * @param e the exception
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // 直接将 Throwable 传递给日志框架，由 Log4j2 处理堆栈打印
        String errorMsg = StrUtil.format("{} 系统抛出未捕获异常", t.getName());
        // 严重：未捕获异常通常意味着线程即将终止，属于 Critical 级别
        RcsLog.sysLog.error(errorMsg, e);
    }
}
