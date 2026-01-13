package com.ruinap.adapter.communicate.base;

import lombok.Data;

import java.util.concurrent.CompletableFuture;

/**
 * 类型化Future类
 * TypedFuture 类用于封装一个具有特定类型的 CompletableFuture 对象
 * 它允许在不进行类型转换的情况下，处理异步计算的结果
 *
 * @author qianye
 * @create 2025-05-14 16:06
 */
@Data
public class TypedFuture<T> {

    /**
     * 定义一个最终的 CompletableFuture 对象，用于表示异步计算
     */
    public final CompletableFuture<T> future;

    /**
     * 定义一个最终的 Class 对象，用于表示泛型类型
     */
    public final Class<T> type;

    /**
     * 构造函数用于初始化 TypedFuture 对象
     *
     * @param future CompletableFuture 对象，表示异步计算
     * @param type   泛型类型的 Class 对象，表示预期的结果类型
     */
    public TypedFuture(CompletableFuture<T> future, Class<T> type) {
        this.future = future;
        this.type = type;
    }
}