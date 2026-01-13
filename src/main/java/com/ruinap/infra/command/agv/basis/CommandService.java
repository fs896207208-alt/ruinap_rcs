package com.ruinap.infra.command.agv.basis;

import com.slamopto.command.agv.basis.factory.AgvCommandFactory;
import com.slamopto.command.agv.basis.registry.CommandFactoryRegistry;
import com.slamopto.task.domain.TaskPath;

/**
 * 指令服务类
 *
 * @author qianye
 * @create 2025-02-24 18:55
 */
public class CommandService {
    /**
     * 生成地图检查指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param agvId    AGV编号
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getMapCheckCommand(String brand, String protocol, String agvId, Long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getMapCheckStrategy().getCommand(agvId, mark);
    }

    /**
     * 生成状态查询指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param agvId    AGV编号
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getStateCommand(String brand, String protocol, String agvId, Long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getStateStrategy().getCommand(agvId, mark);
    }

    /**
     * 生成AGV任务指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param agvId    AGV编号
     * @param mark     数据戳
     * @return
     */
    public static String getAgvTask(String brand, String protocol, String agvId, Long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getTaskStrategy().getCommand(agvId, mark);
    }

    /**
     * 生成AGV移动指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getAgvMove(String brand, String protocol, TaskPath taskPath, Long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getMoveStrategy().getCommand(taskPath, mark);
    }

    /**
     * 生成AGV启动指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getAgvStart(String brand, String protocol, TaskPath taskPath, Long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getStartStrategy().getCommand(taskPath, mark);
    }

    /**
     * 生成AGV暂停指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getAgvPause(String brand, String protocol, TaskPath taskPath, long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getPauseStrategy().getCommand(taskPath, mark);
    }

    /**
     * 生成AGV恢复指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param mark     数据戳
     * @return
     */
    public static String getAgvResume(String brand, String protocol, long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getResumeStrategy().getCommand(null, mark);
    }

    /**
     * 生成AGV取消指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getAgvCancel(String brand, String protocol, TaskPath taskPath, long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getCancelStrategy().getCommand(taskPath, mark);
    }

    /**
     * 生成AGV中断指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param taskPath 任务路径
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getAgvInterrupt(String brand, String protocol, TaskPath taskPath, long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getInterruptStrategy().getCommand(taskPath, mark);
    }

    /**
     * 生成AGV重置指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param agvId    AGV编号
     * @param pointId  点位编号
     * @param yaw      AGV角度
     * @param battery  AGV电量
     * @param load     AGV负载
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getMoveReset(String brand, String protocol, String agvId, Integer pointId, Integer yaw, Integer battery, Integer load, long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getMoveResetStrategy().getCommand(agvId, pointId, yaw, battery, load, mark);
    }

    /**
     * 生成AGV重定位指令
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param agvId    AGV编号
     * @param mapId    地图号
     * @param pointId  点位编号
     * @param angle    朝向角度
     * @param mark     数据戳
     * @return JSONObject
     */
    public static String getReLocation(String brand, String protocol, String agvId, Integer mapId, Integer pointId, Integer angle, Long mark) {
        AgvCommandFactory factory = CommandFactoryRegistry.getFactory(brand, protocol);
        return factory.getReLocationStrategy().getCommand(agvId, mapId, pointId, angle, mark);
    }
}
