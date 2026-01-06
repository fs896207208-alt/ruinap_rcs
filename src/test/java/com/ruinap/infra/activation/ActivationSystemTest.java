package com.ruinap.infra.activation;

import cn.hutool.core.codec.Base32;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.db.Entity;
import com.ruinap.persistence.repository.ActivationDB;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ActivationSystem 核心激活逻辑测试
 * <p>
 * 覆盖目标：
 * 1. 机器码生成 (getMachineCode)
 * 2. 激活算法验证 (isActivation)
 * 3. 自动校验流程 (DB已有记录)
 * 4. 手动激活流程 (System.in模拟输入)
 * </p>
 *
 * @author qianye
 * @create 2025-12-02 16:20
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ActivationSystemTest {

    @InjectMocks
    private ActivationSystem activationSystem;

    @Mock
    private ActivationDB activationDB;

    // 备份标准输入流，测试完还原
    private final InputStream originalSystemIn = System.in;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

    }

    @AfterEach
    void tearDown() {
        // 还原 System.in，避免影响其他测试
        System.setIn(originalSystemIn);
    }

    // ==========================================
    // 1. 基础算法测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("机器码生成测试")
    void testGetMachineCode() {
        String machineCode = activationSystem.getMachineCode();
        System.out.println("   [INFO] 当前环境机器码: " + machineCode);

        assertNotNull(machineCode);
        // 根据代码逻辑: SecureUtil.md5(...).substring(4, 10) -> 长度应为 6
        assertEquals(6, machineCode.length(), "机器码长度应为6位");
    }

    @Test
    @Order(2)
    @DisplayName("激活码生成与校验算法")
    void testActivationAlgorithm() {
        String machineCode = activationSystem.getMachineCode();
        String activationCode = activationSystem.getActivationCode(machineCode);

        System.out.println("   [INFO] 生成激活码: " + activationCode);
        assertNotNull(activationCode);

        // 验证自洽性：自己生成的码必须能通过校验
        boolean isValid = activationSystem.isActivation(machineCode, activationCode);
        assertTrue(isValid, "生成的激活码无法通过校验算法");

        // 验证非法码
        boolean isInvalid = activationSystem.isActivation(machineCode, "INVALID");
        assertFalse(isInvalid, "错误的激活码不应通过校验");
    }

    // ==========================================
    // 2. 业务流程测试：已有合法激活
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("启动校验 - 已激活且未过期")
    void testVerify_AlreadyActive() throws SQLException {
        System.out.println("\n--- 测试：已有合法激活记录 ---");

        // 1. 准备数据
        String machineCode = activationSystem.getMachineCode();
        String activationCode = activationSystem.getActivationCode(machineCode);
        Date futureDate = DateUtil.offsetDay(new Date(), 365); // 一年后过期
        String secretKey = (String) ReflectUtil.getFieldValue(activationSystem, "SECRET_KEY");

        // 计算 SecureCode (MD5校验位)
        // 逻辑: md5(activationCode, expiredDate, SECRET_KEY)
        String secureStr = StrUtil.format("{},{}{}", activationCode, DateUtil.format(futureDate, DatePattern.NORM_DATETIME_PATTERN), secretKey);
        String secureCode = SecureUtil.md5(secureStr);

        Entity entity = Entity.create("RCS_ACTIVATION")
                .set("machine_code", machineCode)
                .set("activation_code", activationCode)
                .set("secure_code", secureCode)
                .set("expired_date", futureDate);

        // 2. Mock DB 返回该记录
        when(activationDB.query(any(Entity.class))).thenReturn(Collections.singletonList(entity));

        // 3. 执行校验
        activationSystem.activationVerify();

        // 4. 验证：不应触发用户输入，也不应插入新记录
        verify(activationDB, never()).insert(any(Entity.class));
        System.out.println("   [PASS] 校验通过，无需重新激活");
    }

    // ==========================================
    // 3. 业务流程测试：未激活 -> 手动输入激活
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("启动校验 - 未激活，模拟输入激活码")
    void testVerify_NewActivation() throws SQLException {
        System.out.println("\n--- 测试：未激活，手动输入流程 ---");

        // 1. Mock DB 返回空 (未激活状态)
        when(activationDB.query(any(Entity.class))).thenReturn(Collections.emptyList());
        // Mock Insert 成功
        when(activationDB.insert(any(Entity.class))).thenReturn(1);

        // 2. 生成合法的激活输入串
        // 格式: Base32( activationCode + "," + expireDate + "," + md5 )
        String machineCode = activationSystem.getMachineCode();
        String activationCode = activationSystem.getActivationCode(machineCode);
        String expireDateStr = DateUtil.format(DateUtil.offsetDay(new Date(), 30), DatePattern.NORM_DATETIME_PATTERN);
        String secretKey = (String) ReflectUtil.getFieldValue(activationSystem, "SECRET_KEY");

        // 计算输入串的 MD5 签名
        // 逻辑: md5(activationCode, expireDateStr, SECRET_KEY)
        String inputMd5 = SecureUtil.md5(StrUtil.format("{},{}{}", activationCode, expireDateStr, secretKey));

        String rawInput = activationCode + "," + expireDateStr + "," + inputMd5;
        String base32Input = Base32.encode(rawInput);

        System.out.println("   [SETUP] 构造模拟输入(Base32): " + base32Input);

        // 3. 注入 System.in
        // 模拟用户输入了正确的字符串，然后回车
        ByteArrayInputStream in = new ByteArrayInputStream(base32Input.getBytes());
        System.setIn(in);

        // 4. 执行校验 (会进入 while 循环，读取 System.in，校验成功后 break)
        activationSystem.activationVerify();

        // 5. 验证是否执行了插入操作
        verify(activationDB, times(1)).insert(any(Entity.class));
        System.out.println("   [PASS] 成功读取输入并激活入库");
    }
}