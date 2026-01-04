package com.ruinap.infra.activation;

import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.test.SpringBootTest;
import com.ruinap.infra.framework.test.SpringRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/*** ActivationSystem 核心逻辑单元测试
 * <p>
 * 用于验证加密/解密算法的一致性，并辅助生成开发环境的激活码。
 * @author qianye
 * @create 2025-12-02 16:20
 */
// 1. 指定 Runner，确保容器启动
@RunWith(SpringRunner.class)
// 2. 指定这是个集成测试
@SpringBootTest
public class ActivationSystemTest {
    @Autowired
    private ActivationSystem activationSystem;

    /**
     * 测试核心流程：生成机器码 -> 计算激活码 -> 验证激活码
     * <p>
     * 注意：由于目标方法是 private static 的，这里使用 Hutool 的 ReflectUtil 进行反射调用。
     */
    @Test
    public void testActivationLogic() {
        // 获取当前环境的机器码
        String machineCode = activationSystem.getMachineCode();
        Assert.assertNotNull("机器码不应为空", machineCode);
        Assert.assertEquals("机器码长度应为6位", 6, machineCode.length());

        System.out.println("========================================");
        System.out.println("【当前环境机器码】: " + machineCode);

        // 根据机器码算出理论上的激活码
        String activationCode = activationSystem.getActivationCode(machineCode);
        Assert.assertNotNull("激活码不应为空", activationCode);
        Assert.assertEquals("激活码长度应为6位", 6, activationCode.length());

        System.out.println("【计算出的激活码】: " + activationCode);
        System.out.println("========================================");

        // 验证正向逻辑：正确的激活码应该返回 true
        boolean isValid = activationSystem.isActivation(machineCode, activationCode);
        Assert.assertTrue("生成的激活码校验应当通过", isValid);
        System.out.println("【生成的激活码校验应当通过】: " + isValid);

        boolean isInvalid = activationSystem.isActivation(machineCode, "ddddd");
        Assert.assertFalse("错误的激活码校验应当失败", isInvalid);
        System.out.println("【错误的激活码校验应当失败】: " + isInvalid);
    }
}
