package com.ruinap.infra.command.agv.factory;

import com.ruinap.core.task.domain.TaskPath;
import io.netty.buffer.ByteBuf;

/**
 * AGV指令工厂接口 (High Performance / Zero GC)
 * 职责：将业务对象直接转换为协议字节流写入 ByteBuf
 *
 * @author qianye
 * @create 2026-01-13 17:18
 */
public interface AgvCommandFactory {

    /**
     * 获取支持的协议 (用于自动注册)
     *
     * @return 协议字符串，如 "WEBSOCKET_CLIENT"
     */
    String getProtocol();

    // ==================== 1. 核心移动与控制 ====================

    /**
     * 写入移动指令 (REQUEST_AGV_MOVE)
     */
    void writeMoveCommand(ByteBuf out, TaskPath taskPath, Long mark);

    /**
     * 写入开始指令 (REQUEST_AGV_MOVE_START)
     */
    void writeStartCommand(ByteBuf out, TaskPath taskPath, Long mark);

    /**
     * 写入暂停指令 (REQUEST_AGV_MOVE_PAUSE)
     */
    void writePauseCommand(ByteBuf out, TaskPath taskPath, Long mark);

    /**
     * 写入恢复指令 (REQUEST_AGV_MOVE_RESUME)
     */
    void writeResumeCommand(ByteBuf out, TaskPath taskPath, Long mark);

    /**
     * 写入取消指令 (REQUEST_AGV_MOVE_INTERRUPT - Cancel)
     */
    void writeCancelCommand(ByteBuf out, TaskPath taskPath, Long mark);

    /**
     * 写入中断指令 (REQUEST_AGV_MOVE_INTERRUPT - Interrupt)
     */
    void writeInterruptCommand(ByteBuf out, TaskPath taskPath, Long mark);

    // ==================== 2. 状态与查询 ====================

    /**
     * 写入状态查询指令 (REQUEST_Dispatch_STATE)
     */
    void writeStateCommand(ByteBuf out, String agvId, Long mark);

    /**
     * 写入任务查询指令 (REQUEST_AGV_TASK)
     */
    void writeTaskCommand(ByteBuf out, String agvId, Long mark);

    /**
     * 写入地图检查指令 (REQUEST_AGV_MAP)
     */
    void writeMapCheckCommand(ByteBuf out, String agvId, Long mark);

    // ==================== 3. 高级操作 ====================

    /**
     * 写入重置/仿真指令 (REQUEST_AGV_MOVE_RESET)
     * 用于仿真环境修改AGV状态
     */
    void writeMoveResetCommand(ByteBuf out, String agvId, Integer pointId, Integer yaw, Integer battery, Integer load, Long mark);

    /**
     * 写入重定位指令 (REQUEST_AGV_RELOC)
     */
    void writeReLocationCommand(ByteBuf out, String agvId, Integer mapId, Integer pointId, Integer angle, Long mark);
}