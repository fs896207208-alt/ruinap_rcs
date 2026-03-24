package com.ruinap.core.algorithm.domain;

/**
 * 任务状态接口
 * 状态模式允许对象在其内部状态改变时改变它的行为。这种模式下，对象看起来似乎改变了它的类
 * 对于AGV任务管理，每个状态都可以定义一个具体的状态类，所有状态类实现同一个接口或继承自同一个抽象类
 *
 * @author qianye
 * @create 2024-05-19 14:29
 */
public interface PathState {
    /**
     * 执行具体的推演逻辑
     */
    void handle(PathContext context);
}
