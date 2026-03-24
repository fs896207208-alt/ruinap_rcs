package com.ruinap.core.task.event;

import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.framework.core.event.ApplicationEvent;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

/**
 * AGV 统一控制指令下发事件 (全能融合版)
 * <p>
 * 架构职责：
 * 1. 彻底解耦业务调度层与底层网络通信层。
 * 2. 支持 MOVE(移动), PAUSE(暂停), CANCEL(取消) 等全生命周期控制。
 * 3. 自带 ackFuture 桥接器，支持 RESTful API 的同步阻塞返回 (基于虚拟线程)。
 *
 * @author qianye
 * @create 2026-03-02 14:46
 */
@Getter
public class AgvCommandEvent extends ApplicationEvent {
    /**
     * 指令类型枚举 (拒绝魔法字符串，保证强类型安全)
     */
    public enum CommandType {
        /**
         * 移动
         */
        MOVE,
        /**
         * 暂停
         */
        PAUSE,
        /**
         * 恢复
         */
        RESUME,
        /**
         * 取消
         */
        CANCEL,
        INTERRUPT
    }

    private final CommandType commandType;
    private final TaskPath taskPath;
    private final RcsAgv agv;

    /**
     * 【架构黑科技】：异步结果桥接器
     * - Web 控制台 (如 Controller) 发送指令时，可调用 future.get() 阻塞等待网关回执。
     * - 内部状态机 (如 PathNewState) 发送指令时，可直接忽略此 future，走纯异步状态流转。
     */
    private final CompletableFuture<Boolean> ackFuture;

    public AgvCommandEvent(Object source, CommandType commandType, TaskPath taskPath, RcsAgv agv) {
        super(source);
        this.commandType = commandType;
        this.taskPath = taskPath;
        this.agv = agv;
        this.ackFuture = new CompletableFuture<>();
    }
}
