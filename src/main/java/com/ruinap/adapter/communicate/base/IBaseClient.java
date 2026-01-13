package com.ruinap.adapter.communicate.base;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端通信基础接口
 *
 * @author qianye
 * @create 2025-04-24 14:16
 */
public interface IBaseClient {

    /**
     * 启动服务端
     */
    CompletableFuture<Boolean> start();

    /**
     * 停止服务端
     */
    void shutdown();

    /**
     * 发送消息
     *
     * @param message 消息
     */
    <T> void sendMessage(T message);

    /**
     * 发送消息并等待响应
     *
     * @param requestId    请求ID
     * @param message      消息
     * @param responseType 响应类型
     * @param <T>          响应类型
     * @return 一个 CompletableFuture，将在收到响应或超时时完成
     */
    <T> CompletableFuture<T> sendMessage(Long requestId, Object message, Class<T> responseType);
}
