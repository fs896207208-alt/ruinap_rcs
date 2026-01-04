package com.ruinap.infra.aop;

import com.ruinap.infra.log.RcsLog;

/*** 模拟业务服务
 * 用于触发 AspectJ 切面
 * @author qianye
 * @create 2025-12-04 16:01
 */
public class MockService {
    /**
     * 模拟一个抛出受检异常的方法
     */
    public void executeTask() throws Exception {
        RcsLog.consoleLog.info("1. [业务层] 正在执行任务...");
        // 模拟逻辑错误
        throw new Exception("AGV路径计算超时 (模拟业务异常)");
    }

    /**
     * 模拟一个抛出运行时异常的方法 (RuntimeException)
     */
    public void calculatePath() {
        RcsLog.consoleLog.info("1. [业务层] 开始计算路径...");
        // 模拟除零异常
        int i = 1 / 0;
    }
}
