package com.ruinap.adapter.communicate.base;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * Netty 全局资源池
 * <p>
 * 核心原则：
 * 1. IO 线程必须使用平台线程 (Platform Thread)，严禁使用虚拟线程。
 * 2. 全局复用 EventLoopGroup，避免多协议导致线程爆炸。
 *
 * @author qianye
 * @create 2026-01-22 10:13
 */
public class NettyGlobalResources {
    /**
     * Boss 线程组
     * 1. 数量：设置 1-2 即可，因为 Boss 线程仅负责 Accept 连接，不处理业务，负载极低。
     * 2. 线程类型：false (用户线程)，确保 Netty 运行时 JVM 不会意外退出。
     */
    private static final EventLoopGroup BOSS_GROUP = new NioEventLoopGroup(2, new DefaultThreadFactory("netty-boss", false));

    /**
     * Worker 线程组
     * 1. 数量：传入 0，让 Netty 根据服务器 CPU 核数自动计算 (通常是 CPU核数 * 2)。
     * 例如：8核服务器 -> 自动创建 16 个线程。
     * 2. 作用：所有协议 (TCP, WebSocket, MQTT) 的数据读写、编解码都复用这组线程。
     * 3. 警告：绝对禁止在此线程中执行耗时操作 (如查数据库)，否则会阻塞该线程下的所有连接。
     */
    private static final EventLoopGroup WORKER_GROUP = new NioEventLoopGroup(0, new DefaultThreadFactory("netty-worker", false));

    /**
     * 获取全局 Boss 组
     */
    public static EventLoopGroup getBossGroup() {
        return BOSS_GROUP;
    }

    /**
     * 获取全局 Worker 组
     */
    public static EventLoopGroup getWorkerGroup() {
        return WORKER_GROUP;
    }

    /**
     * 优雅关闭所有资源
     * 在系统停止时调用
     */
    public static void shutdown() {
        BOSS_GROUP.shutdownGracefully();
        WORKER_GROUP.shutdownGracefully();
    }
}
