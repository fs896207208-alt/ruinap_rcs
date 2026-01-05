package com.ruinap.infra.activation;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*** ActivationSystem 核心逻辑单元测试
 * <p>
 * 用于验证加密/解密算法的一致性，并辅助生成开发环境的激活码。
 * @author qianye
 * @create 2025-12-02 16:20
 */
@SpringBootTest // 组合注解，替代 @RunWith
@DisplayName("激活系统(ActivationSystem)测试")
class ActivationSystemTest {

    @Autowired
    private ActivationSystem activationSystem;

    /**
     * 测试核心流程：生成机器码 -> 计算激活码 -> 验证激活码
     * <p>
     * 注意：由于目标方法是 private static 的，这里使用 Hutool 的 ReflectUtil 进行反射调用。
     */
    @Test
    @DisplayName("测试：激活码生成与校验")
    void testActivationLogic() {
        // 获取当前环境的机器码
        String machineCode = activationSystem.getMachineCode();

        // JUnit 6: assertNotNull(actual, message)
        Assertions.assertNotNull(machineCode, "机器码不应为空");
        // JUnit 6: assertEquals(expected, actual, message)
        Assertions.assertEquals(6, machineCode.length(), "机器码长度应为6位");

        System.out.println("========================================");
        System.out.println("【当前环境机器码】: " + machineCode);

        // 根据机器码算出理论上的激活码
        String activationCode = activationSystem.getActivationCode(machineCode);

        Assertions.assertNotNull(activationCode, "激活码不应为空");
        Assertions.assertEquals(6, activationCode.length(), "激活码长度应为6位");

        System.out.println("【计算出的激活码】: " + activationCode);
        System.out.println("========================================");

        // 验证正向逻辑：正确的激活码应该返回 true
        boolean isValid = activationSystem.isActivation(machineCode, activationCode);

        // JUnit 6: assertTrue(condition, message)
        Assertions.assertTrue(isValid, "生成的激活码校验应当通过");
    }
}