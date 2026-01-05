package com.ruinap.infra.aop;


import org.junit.jupiter.api.Test;

/**
 * @author qianye
 * @create 2025-12-04 16:01
 */
public class AspectLogTest {
    private final MockService mockService = new MockService();

    @Test
    public void testExceptionCaughtByCaller() {
        System.out.println("========= 测试开始：验证 Aspect 是否拦截已捕获的异常 =========");

        try {
            // 调用业务方法
            mockService.executeTask();

        } catch (Exception e) {
            // 这里模拟上层调用者（如 Controller）捕获了异常
            // 此时，Aspect 应该已经执行完毕了
            System.out.println("\n3. [调用者] 我捕获到了异常，程序没有崩溃！");
            System.out.println("   捕获内容: " + e.getMessage());
        }

        System.out.println("========= 测试结束 =========");
    }

    @Test
    public void testRuntimeException() {
        System.out.println("\n========= 测试开始：验证 RuntimeException =========");
        try {
            mockService.calculatePath();
        } catch (Exception e) {
            System.out.println("\n3. [调用者] 已捕获运行时异常");
        }
        System.out.println("========= 测试结束 =========");
    }
}
