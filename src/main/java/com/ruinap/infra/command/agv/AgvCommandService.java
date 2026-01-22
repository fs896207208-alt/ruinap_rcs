package com.ruinap.infra.command.agv;

import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.command.agv.registry.CommandFactoryRegistry;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import io.netty.buffer.ByteBuf;

/**
 * AGV 指令服务类
 *
 * @author qianye
 * @create 2026-01-13 18:02
 */
@Service
public class AgvCommandService {
    /**
     * 指令工厂类
     */
    @Autowired
    private CommandFactoryRegistry registry;

    /**
     * 生成地图检查指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param agvId    AGV编号
     * @param mark     数据戳
     */
    public void getMapCheckCommand(String protocol, ByteBuf out, String agvId, Long mark) {
        registry.getFactory(protocol).writeMapCheckCommand(out, agvId, mark);
    }

    /**
     * 生成状态查询指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param agvId    AGV编号
     * @param mark     数据戳
     */
    public void getStateCommand(String protocol, ByteBuf out, String agvId, Long mark) {
        registry.getFactory(protocol).writeStateCommand(out, agvId, mark);
    }

    /**
     * 生成AGV任务指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param agvId    AGV编号
     * @param mark     数据戳
     */
    public void getAgvTask(String protocol, ByteBuf out, String agvId, Long mark) {
        registry.getFactory(protocol).writeTaskCommand(out, agvId, mark);
    }

    /**
     * 生成AGV移动指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    public void getAgvMove(String protocol, ByteBuf out, TaskPath taskPath, Long mark) {
        registry.getFactory(protocol).writeMoveCommand(out, taskPath, mark);
    }

    /**
     * 生成AGV启动指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    public void getAgvStart(String protocol, ByteBuf out, TaskPath taskPath, Long mark) {
        registry.getFactory(protocol).writeStartCommand(out, taskPath, mark);
    }

    /**
     * 生成AGV暂停指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    public void getAgvPause(String protocol, ByteBuf out, TaskPath taskPath, long mark) {
        registry.getFactory(protocol).writePauseCommand(out, taskPath, mark);
    }

    /**
     * 生成AGV恢复指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param mark     数据戳
     */
    public void getAgvResume(String protocol, ByteBuf out, long mark) {
        registry.getFactory(protocol).writeResumeCommand(out, null, mark);
    }

    /**
     * 生成AGV取消指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    public void getAgvCancel(String protocol, ByteBuf out, TaskPath taskPath, long mark) {
        registry.getFactory(protocol).writeCancelCommand(out, taskPath, mark);
    }

    /**
     * 生成AGV中断指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    public void getAgvInterrupt(String protocol, ByteBuf out, TaskPath taskPath, long mark) {
        registry.getFactory(protocol).writeInterruptCommand(out, taskPath, mark);
    }

    /**
     * 生成AGV重置指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param agvId    AGV编号
     * @param pointId  点位编号
     * @param yaw      AGV角度
     * @param battery  AGV电量
     * @param load     AGV负载
     * @param mark     数据戳
     */
    public void getMoveReset(String protocol, ByteBuf out, String agvId, Integer pointId, Integer yaw, Integer battery, Integer load, long mark) {
        registry.getFactory(protocol).writeMoveResetCommand(out, agvId, pointId, yaw, battery, load, mark);
    }

    /**
     * 生成AGV重定位指令
     *
     * @param protocol 协议
     * @param out      指令字节流
     * @param agvId    AGV编号
     * @param mapId    地图号
     * @param pointId  点位编号
     * @param angle    朝向角度
     * @param mark     数据戳
     */
    public void getReLocation(String protocol, ByteBuf out, String agvId, Integer mapId, Integer pointId, Integer angle, Long mark) {
        registry.getFactory(protocol).writeReLocationCommand(out, agvId, mapId, pointId, angle, mark);
    }
}
