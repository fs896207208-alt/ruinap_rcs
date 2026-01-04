package com.ruinap.infra.config.common;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径工具类
 * 1. 使用 java.nio.file.Path 处理跨平台分隔符。
 * 2. 所有字段均为 static final，确保线程安全（不可变性）。
 * 3. 支持从系统属性或环境变量覆盖根路径（容器化友好）。
 *
 * @author qianye
 * @create 2025-06-20 17:07
 */
public class PathUtils {

    /**
     * 全局路径字符串
     */
    private static final String APP_HOME = System.getProperty("app.home", System.getenv().getOrDefault("APP_HOME", "doc"));
    /**
     * 全局文件根路径
     */
    public static final Path ROOT_DIR = Paths.get(APP_HOME);

    /**
     * 配置文件路径
     */
    public static final Path CONFIG_DIR = ROOT_DIR.resolve("config");
    /**
     * 临时文件路径
     */
    public static final Path TEMP_DIR = ROOT_DIR.resolve("temp");
    /**
     * 地图图片文件路径
     */
    public static final Path PNG_DIR = ROOT_DIR.resolve("png");
    /**
     * 全局 WebApps 路径
     */
    public static final Path WEBAPPS_DIR = ROOT_DIR.resolve("webapps");

    /**
     * H2DB 文件路径
     */
    public static final Path H2DB_DIR = ROOT_DIR.resolve("h2db");

    /**
     * 核心配置文件路径
     */
    public static final Path CORE_CONFIG_FILE = CONFIG_DIR.resolve("rcs_core.yaml");
    /**
     * DB配置文件路径
     */
    public static final Path DB_CONFIG_FILE = CONFIG_DIR.resolve("rcs_db.setting");
    /**
     * Map配置文件路径
     */
    public static final Path MAP_CONFIG_FILE = CONFIG_DIR.resolve("rcs_map.yaml");
    /**
     * Link配置文件路径
     */
    public static final Path LINK_CONFIG_FILE = CONFIG_DIR.resolve("rcs_link.yaml");
    /**
     * Task配置文件路径
     */
    public static final Path TASK_CONFIG_FILE = CONFIG_DIR.resolve("rcs_task.yaml");
    /**
     * Interactive配置文件路径
     */
    public static final Path INTERACTIVE_CONFIG_FILE = CONFIG_DIR.resolve("rcs_interactive.yaml");
    /**
     * Simulation配置文件路径
     */
    public static final Path SIMULATION_CONFIG_FILE = CONFIG_DIR.resolve("rcs_simulation.yaml");
    /**
     * Temp配置文件路径
     */
    public static final Path TEMP_CONFIG_FILE = TEMP_DIR.resolve("temp.rcs");
    /**
     * 应用程序锁文件路径
     */
    public static final Path LOCK_TEMP_FILE = TEMP_DIR.resolve("rcs-application.lock");

    /**
     * 私有构造，防止实例化
     */
    private PathUtils() {
    }
}
