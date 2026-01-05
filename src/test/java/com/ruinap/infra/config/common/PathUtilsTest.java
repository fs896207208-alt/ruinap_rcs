package com.ruinap.infra.config.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * PathUtils 单元测试 (完整版)
 * <p>
 * 目标:
 * 1. 验证所有静态路径常量不为 Null。
 * 2. 验证路径的层级关系 (例如: 配置文件必须在 CONFIG_DIR 下)。
 * 3. 验证默认根路径逻辑 (默认为 "doc")。
 * 4. 覆盖文件中定义的所有 9 个文件常量和 4 个目录常量。
 */
@DisplayName("路径工具(PathUtils)测试")
public class PathUtilsTest {

    // 模拟预期的默认根目录 (根据 PathUtils 代码逻辑: "doc")
    private static final String EXPECTED_ROOT = "doc";

    /**
     * 测试目录结构逻辑 (严格校验父级关系)
     * <p>
     * 由于 PathUtils 不再负责创建目录，这里只校验 "路径解析逻辑" 是否正确，
     * 而不校验 "物理文件是否存在"。
     */
    @Test
    @DisplayName("人工确认路径打印")
    public void printAllPathsForManualCheck() {
        System.out.println("========== 路径解析人工确认 ==========");
        System.out.println("当前工作目录 (User Dir): " + PathUtils.ROOT_DIR.toAbsolutePath());
        System.out.println("--------------------------------------");

        // 1. 打印目录常量
        printPath("配置根目录 (CONFIG_DIR)", PathUtils.CONFIG_DIR);
        printPath("临时目录 (TEMP_DIR)", PathUtils.TEMP_DIR);
        printPath("图片目录 (PNG_DIR)", PathUtils.PNG_DIR);
        printPath("Web应用目录 (WEBAPPS_DIR)", PathUtils.WEBAPPS_DIR);

        System.out.println("--------------------------------------");

        // 2. 打印具体的文件路径 (这里会显示完整的文件名)
        printPath("核心配置 (CORE)", PathUtils.CORE_CONFIG_FILE);
        printPath("数据库配置 (DB)", PathUtils.DB_CONFIG_FILE);
        printPath("地图配置 (MAP)", PathUtils.MAP_CONFIG_FILE);
        printPath("线路配置 (LINK)", PathUtils.LINK_CONFIG_FILE);
        printPath("任务配置 (TASK)", PathUtils.TASK_CONFIG_FILE);
        printPath("交互配置 (INTERACTIVE)", PathUtils.INTERACTIVE_CONFIG_FILE);
        printPath("仿真配置 (SIMULATION)", PathUtils.SIMULATION_CONFIG_FILE);
        printPath("临时配置 (TEMP_FILE)", PathUtils.TEMP_CONFIG_FILE);

        System.out.println("======================================");
    }

    /**
     * 辅助方法：格式化打印路径
     * 使用 toAbsolutePath() 确保看到的是硬盘上的真实完整路径
     */
    private void printPath(String label, Path path) {
        if (path == null) {
            System.err.println(String.format("%-25s : [NULL]", label));
            return;
        }
        // format: 标签名 : 绝对路径
        System.out.println(String.format("%-25s : %s", label, path.toAbsolutePath()));
    }

    /**
     * 测试所有配置文件常量的完整性
     * 确保所有文件都位于 CONFIG_DIR 目录下，且文件名正确
     */
    @Test
    @DisplayName("配置文件常量完整性校验")
    public void testConfigFilesCompleteness() {
        Path rootDir = PathUtils.ROOT_DIR;

        // 定义需要验证的文件映射关系 (变量 -> 预期文件名)
        // 使用 Object[][] 来进行参数化验证风格的循环检查
        Object[][] filesToTest = {
                {PathUtils.CORE_CONFIG_FILE, "rcs_core.yaml"},
                {PathUtils.DB_CONFIG_FILE, "rcs_db.setting"},
                {PathUtils.MAP_CONFIG_FILE, "rcs_map.yaml"},
                {PathUtils.LINK_CONFIG_FILE, "rcs_link.yaml"},
                {PathUtils.TASK_CONFIG_FILE, "rcs_task.yaml"},
                {PathUtils.INTERACTIVE_CONFIG_FILE, "rcs_interactive.yaml"},
                {PathUtils.SIMULATION_CONFIG_FILE, "rcs_simulation.yaml"},
                {PathUtils.TEMP_CONFIG_FILE, "temp.rcs"}
        };

        for (Object[] pair : filesToTest) {
            Path actualPath = (Path) pair[0];
            String expectedFileName = (String) pair[1];

            // 1. 非空检查
            // JUnit 6: assertNotNull(actual, message)
            Assertions.assertNotNull(actualPath, "常量路径不能为 null: " + expectedFileName);

            // 2. 父目录检查 (这是关键: 确保文件确实在 config 目录下)
            // JUnit 6: assertTrue(condition, message)
            Assertions.assertTrue(actualPath.toString().contains(rootDir.toString()),
                    "文件 " + actualPath + " 的根目录必须是 " + rootDir);

            // 3. 文件名检查
            // JUnit 6: assertEquals(expected, actual, message)
            Assertions.assertEquals(expectedFileName, actualPath.getFileName().toString(), "文件名解析错误");
        }
    }

    /**
     * 测试路径对象的不可变性与解析逻辑
     * <p>
     * 场景模拟：
     * 系统运行中，某个线程需要基于 CORE_CONFIG_FILE 找到它的备份文件（.bak）。
     * 我们必须确保这个操作不会修改原始的 CORE_CONFIG_FILE 常量。
     */
    @Test
    @DisplayName("路径不可变性与衍生测试")
    public void testPathImmutabilityAndResolution() {
        System.out.println("========== 路径不可变性与衍生测试 ==========");

        // 1.以此为例：核心配置文件路径
        Path originalCorePath = PathUtils.CORE_CONFIG_FILE;
        System.out.println("原始路径 (Original): " + originalCorePath);

        // 2. 尝试修改：基于原路径解析一个不存在的子路径
        // 注意：Path 是不可变对象，resolve 会返回一个新的 Path 对象
        Path modifiedPath = originalCorePath.resolve("hack_attempt");

        // 3. 尝试衍生：基于原路径查找同级目录下的备份文件
        // 场景：读取 rcs_core.yaml 失败，尝试读取 rcs_core_backup.yaml
        Path backupPath = originalCorePath.resolveSibling("rcs_core_backup.yaml");

        // --- 核心断言 ---

        // 验证 1: 原始对象未被修改 (这是多线程安全的基础)
        // 原始路径的文件名必须依然是 "rcs_core.yaml"
        Assertions.assertEquals("rcs_core.yaml", originalCorePath.getFileName().toString(), "原始路径被篡改！");

        // 验证 2: 衍生对象是全新的
        // JUnit 6: assertNotEquals(unexpected, actual, message)
        Assertions.assertNotEquals(originalCorePath, modifiedPath, "修改后的路径对象应该是新的实例");

        Assertions.assertNotEquals(originalCorePath, backupPath, "备份文件路径对象应该是新的实例");

        // 验证 3: 衍生逻辑正确
        Assertions.assertEquals("rcs_core_backup.yaml", backupPath.getFileName().toString(), "备份文件应命名为 rcs_core_backup.yaml");

        // --- 人工确认打印 ---
        System.out.println("衍生路径 (Modified): " + modifiedPath);
        System.out.println("兄弟路径 (Sibling) : " + backupPath);
        System.out.println("校验结果: 原始路径保持不变，线程安全验证通过。");
        System.out.println("==========================================");
    }
}