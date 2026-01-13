package com.ruinap.adapter.communicate.base;

import java.util.concurrent.CompletableFuture;

/**
 * 服务端通信基础接口
 *
 * @author qianye
 * @create 2025-04-24 14:16
 */
public interface IBaseServer {

    /**
     * 启动服务端
     */
    CompletableFuture<Void> start();

    /**
     * 停止服务端
     */
    void shutdown();

    /**
     * 发送消息
     *
     * @param serverId 服务器ID
     * @param message  消息
     */
    <T> void sendMessage(String serverId, T message);

    /**
     * 发送消息并等待响应
     *
     * @param serverId     服务器ID
     * @param requestId    请求ID
     * @param responseType 响应类型
     * @return 一个 CompletableFuture，将在收到响应或超时时完成
     */
    <T> CompletableFuture<T> sendMessage(String serverId, Long requestId, T message, Class<T> responseType);
}
