package com.ruinap.infra.command.agv.basis.factory;

import com.slamopto.command.agv.basis.strategy.*;

/**
 * AGV命令工厂，提供获取具体策略的方法
 *
 * @author qianye
 * @create 2025-02-24 18:37
 */
public interface AgvCommandFactory {
    /**
     * 获取地图检查策略
     *
     * @return StateStrategy
     */
    MapCheckStrategy getMapCheckStrategy();

    /**
     * 获取AGV状态策略
     *
     * @return StateStrategy
     */
    StateStrategy getStateStrategy();

    /**
     * 获取AGV任务策略
     *
     * @return StateStrategy
     */
    TaskStrategy getTaskStrategy();

    /**
     * 获取AGV移动策略
     *
     * @return MoveStrategy
     */
    MoveStrategy getMoveStrategy();

    /**
     * 获取AGV开始策略
     *
     * @return MoveStrategy
     */
    StartStrategy getStartStrategy();

    /**
     * 获取AGV暂停策略
     *
     * @return PauseStrategy
     */
    PauseStrategy getPauseStrategy();

    /**
     * 获取AGV恢复策略
     *
     * @return ResumeStrategy
     */
    ResumeStrategy getResumeStrategy();

    /**
     * 获取AGV取消策略
     *
     * @return CancelStrategy
     */
    CancelStrategy getCancelStrategy();

    /**
     * 获取AGV中断策略
     *
     * @return CancelStrategy
     */
    InterruptStrategy getInterruptStrategy();

    /**
     * 获取AGV仿真重置策略
     *
     * @return MoveResetStrategy
     */
    MoveResetStrategy getMoveResetStrategy();

    /**
     * 获取AGV重定位策略
     *
     * @return MoveResetStrategy
     */
    ReLocationStrategy getReLocationStrategy();
}
