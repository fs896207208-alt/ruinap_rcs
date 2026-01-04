package com.ruinap.infra.lock;

import com.ruinap.infra.config.common.PathUtils;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.log.RcsLog;
import lombok.Getter;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * 应用程序锁工具类
 * <p>
 * 用于防止应用程序重复启动，基于文件锁机制实现进程间互斥。
 * 该类使用Java NIO的FileLock实现跨进程锁，确保同一时间只有一个实例运行。
 * </p>
 *
 * @author qianye
 * @since 2025-01-20
 */
@Component
public class ApplicationLock {

    /**
     * 文件锁对象，用于跨进程同步
     */
    private FileLock appLock;
    /**
     * 文件通道，用于操作锁文件
     */
    private FileChannel lockChannel;
    /**
     * 锁状态标识，标记是否已获取锁 true-已获取锁，false-未获取锁
     */
    @Getter
    private volatile boolean isLockAcquired = false;

    /**
     * 启动方法
     *
     * @throws Exception
     */
    @PostConstruct
    public void run() throws Exception {
        // 这必须在程序运行时第一个执行，如果锁获取失败，根本不需要浪费资源去扫描包
        if (!tryAcquire()) {
            RcsLog.consoleLog.error("应用程序启动失败：检测到已有同一个程序实例在运行中");
            System.exit(1);
        }
    }

    /**
     * 尝试获取应用程序锁
     * <p>
     * 该方法会尝试在临时目录下创建一个唯一的锁文件，并尝试获取文件锁。
     * 如果获取成功，表示当前没有其他实例在运行；如果获取失败，则说明已有实例在运行。
     * </p>
     *
     * @return true-获取成功，false-获取失败（已有实例在运行）
     */
    public boolean tryAcquire() {
        try {
            // 1. 直接获取 PathUtils 中定义的锁文件路径
            var lockFilePath = PathUtils.LOCK_TEMP_FILE;

            // 2. 确保父目录存在 (例如 doc/temp)
            var lockDir = lockFilePath.getParent();
            if (lockDir != null && !Files.exists(lockDir)) {
                Files.createDirectories(lockDir);
            }

            // 3. 打开文件通道
            // CREATE + WRITE: 创建并写入
            // DELETE_ON_CLOSE: 正常退出时自动删除文件
            lockChannel = FileChannel.open(lockFilePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.DELETE_ON_CLOSE);

            // 6. 尝试获取文件锁 (tryLock 是非阻塞的)
            try {
                appLock = lockChannel.tryLock();
            } catch (OverlappingFileLockException e) {
                return false;
            }

            if (appLock != null) {
                // 获取锁成功
                isLockAcquired = true;
                RcsLog.consoleLog.info("ApplicationLock 应用程序锁获取成功: {}", lockFilePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            RcsLog.consoleLog.error("获取应用程序锁时发生IO异常: " + e.getMessage(), e);
            cleanup();
            return false;
        } catch (Exception e) {
            RcsLog.consoleLog.error("获取应用程序锁时发生未知异常: " + e.getMessage(), e);
            cleanup();
            return false;
        }
    }

    /**
     * 释放应用程序锁
     * <p>
     * 该方法用于释放已获取的文件锁，并关闭相关资源。
     * 通常在程序正常退出时调用，也可通过关闭钩子自动调用。
     * </p>
     */
    @PreDestroy
    public void destroy() {
        if (isLockAcquired) {
            cleanup();
        }
    }

    /**
     * 清理资源
     * <p>
     * 该方法负责释放文件锁、关闭文件通道等资源清理工作。
     * </p>
     */
    private void cleanup() {
        try {
            if (appLock != null && appLock.isValid()) {
                appLock.release();
            }
        } catch (IOException e) {
            RcsLog.consoleLog.error("释放文件锁时发生异常: " + e.getMessage(), e);
        } finally {
            appLock = null;
        }

        try {
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
            }
        } catch (IOException e) {
            RcsLog.consoleLog.error("关闭文件通道时发生异常: " + e.getMessage(), e);
        } finally {
            lockChannel = null;
        }
        isLockAcquired = false;
    }
}
