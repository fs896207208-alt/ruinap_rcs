package com.ruinap.adapter.communicate.event;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import com.slamopto.command.system.ConsoleCommand;
import com.slamopto.common.VthreadPool;
import com.slamopto.common.config.CoreYaml;
import com.slamopto.common.system.RcsUtils;
import com.slamopto.communicate.base.event.AbstractServerEvent;
import com.slamopto.communicate.server.NettyServer;
import com.slamopto.communicate.server.handler.impl.ConsoleWebSocketHandler;
import com.slamopto.db.DbCache;
import com.slamopto.db.database.ConfigDB;
import com.slamopto.db.database.TaskDB;
import com.slamopto.equipment.agv.RcsAgvCache;
import com.slamopto.equipment.domain.RcsAgv;
import com.slamopto.log.RcsLog;
import com.slamopto.map.RcsPointCache;
import com.slamopto.map.domain.point.RcsPoint;
import com.slamopto.map.domain.point.RcsPointOccupy;
import com.slamopto.map.enums.PointOccupyTypeEnum;
import com.slamopto.task.TaskPathCache;
import com.slamopto.task.domain.RcsTask;
import com.slamopto.task.domain.TaskPath;

import java.sql.SQLException;
import java.util.List;

/**
 * 控制台服务器事件
 *
 * @author qianye
 * @create 2024-07-31 3:43
 */
public class ConsoleWebSocketServerEvent extends AbstractServerEvent {

    /**
     * 接收消息
     *
     * @param serverId 客户端id
     * @param frame    数据帧
     */
    public static void receiveMessage(String serverId, Object frame) {
        String message = frame.toString();
        String[] messages = message.trim().split(" ");
        switch (messages[0]) {
            case "1":
                //查询AGV
                getAgvState(serverId, messages);
                break;
            case "2":
                //查询点位
                getPoint(serverId, messages);
                break;
            case "3":
                //查询任务
                getTask(serverId, messages);
                break;
            case "4":
                //创建任务
                createTask(serverId, messages);
                break;
            case "5":
                //暂停任务
                pauseTask(serverId, messages);
                break;
            case "6":
                //恢复任务
                recoverTask(serverId, messages);
                break;
            case "7":
                //AGV重定位
                agvReLocation(serverId, messages);
                break;
            case "8":
                //取消新任务
                cancelNewTask(serverId, messages);
                break;
            case "9":
                //取消AGV任务
                cancelAGVTask(serverId, messages);
                break;
            case "10":
                //占用点修改
                updatePointOccupy(serverId, messages);
                break;
            case "11":
                //AGV换地图
                changeAgvMap(serverId, messages);
                break;
            case "12":
                //仿真操作
                operateSimulation(serverId, messages);
                break;
            case "96":
                //查看服务器参数
                getServerConfig(serverId);
                break;
            case "97":
                //查看线程
                getThread(serverId);
                break;
            case "98":
                //查看缓存
                getCache(serverId);
                break;
            case "99":
                //查看版本
                getVersion(serverId);
                break;
            case "100":
                // 使用帮助
                getHelp(serverId);
                break;
            default:
                // 发送菜单
                NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(serverId, ConsoleCommand.getMenu());
                break;
        }
    }

    /**
     * 仿真操作
     *
     * @param serverId id
     * @param messages 信息数组
     */
    private static void operateSimulation(String serverId, String[] messages) {
        String result = ConsoleCommand.operateSimulation(serverId, messages);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, messages, result));
    }

    private static void changeAgvMap(String serverId, String[] messages) {
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, messages, null));
    }

    /**
     * 查看服务器参数
     *
     * @param id id
     */
    private static void getServerConfig(String id) {
        String result = ConsoleCommand.getServerConfig(id);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, "96", result));
    }

    /**
     * 恢复任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void recoverTask(String id, String[] messages) {
        String result = ConsoleCommand.recoverTask(id, messages);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * 暂停任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void pauseTask(String id, String[] messages) {
        String result = ConsoleCommand.pauseTask(id, messages);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * 查看线程
     *
     * @param id id
     */
    private static void getThread(String id) {
        String thread = VthreadPool.monitorThreadPool();
        // 发送版本号
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, thread);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, "97", thread));
    }

    /**
     * 查看缓存
     *
     * @param id id
     */
    private static void getCache(String id) {
        String result = ConsoleCommand.getCache(id);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, "98", result));
    }

    /**
     * 占用点修改
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void updatePointOccupy(String id, String[] messages) {
        String result = ConsoleCommand.updatePointOccupy(messages);
        if (result != null && !result.isEmpty()) {
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            return;
        }

        //获取点位
        RcsPoint point = RcsUtils.commonPointParse(messages[1]);
        if (point == null) {
            String message = "点位[" + messages[1] + "]不存在";
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, message);
            return;
        }
        //获取传入的占用类型
        String rcsOccupyStr = messages[2];
        if (rcsOccupyStr == null || rcsOccupyStr.isEmpty()) {
            //3手动占用
            rcsOccupyStr = "3";
        }
        //获取点位占用类型
        PointOccupyTypeEnum pointOccupyTypeEnum = PointOccupyTypeEnum.fromEnum(Integer.valueOf(rcsOccupyStr));
        if (PointOccupyTypeEnum.isEnumByCode(PointOccupyTypeEnum.NULL, pointOccupyTypeEnum.code)) {
            result = "点位[" + messages[1] + "]的占用类型未知";
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            return;
        }

        //获取点位占用情况
        RcsPointOccupy rcsOccupy = RcsPointCache.getRcsOccupy(point);
        if (rcsOccupy == null) {
            return;
        }
        //判断该占用类似是否存在
        boolean occupyType = rcsOccupy.getOccupyType(Integer.parseInt(rcsOccupyStr));
        if (occupyType) {
            if (messages.length > 3 && ConsoleCommand.DIRECT_INSTRUCTION.equals(messages[3])) {
                //遍历
                rcsOccupy.getOccupyMap().forEach((key, value) -> {
                    if (value.contains(pointOccupyTypeEnum)) {
                        //取消占用
                        rcsOccupy.removeOccupyType(point, key, pointOccupyTypeEnum);
                    }
                });
            } else if (messages.length > 3) {
                result = "AGV[" + messages[1] + "]使用了无效的直发指令，取消本次操作";
                NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
                return;
            }

            //取消占用
            rcsOccupy.removeOccupyType(point, "sys", pointOccupyTypeEnum);
        } else {
            //设置占用
            rcsOccupy.addOccupyType(point, pointOccupyTypeEnum, "sys");
        }
        result = "占用点位修改成功";
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * 取消新任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void cancelNewTask(String id, String[] messages) {
        String result = ConsoleCommand.cancelNewTask(messages);
        if (result != null && !result.isEmpty()) {
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            return;
        }

        try {
            String taskCode = messages[1];
            //查询任务
            Condition agvIdWhere = new Condition("task_code", "=", taskCode);
            Condition taskStateWhere = new Condition("task_state", "=", 2);
            Condition interruptStateWhere = new Condition("interrupt_state", "=", 0);
            List<Entity> entityList = TaskDB.queryTaskList(agvIdWhere, taskStateWhere, interruptStateWhere);
            if (entityList.isEmpty()) {
                result = "任务[" + taskCode + "]查询不到数据，可能任务不是新任务或已取消";
                NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            } else {
                for (Entity entity : entityList) {
                    RcsTask rcsTask = DbCache.RCS_TASK_MAP.get(entity.getStr("task_code"));
                    rcsTask.setTaskState(-1);
                }
                result = "取消任务成功";
                NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            }
        } catch (Exception e) {
            result = "取消任务失败，" + e.getMessage();
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
        }
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * 取消AGV任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void cancelAGVTask(String id, String[] messages) {
        String result = ConsoleCommand.cancelAGVTask(id, messages);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * AGV重定位
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void agvReLocation(String id, String[] messages) {
        String result = ConsoleCommand.agvReLocation(id, messages);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * 创建任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void createTask(String id, String[] messages) {
        String result = ConsoleCommand.createTask(messages);
        if (result != null && !result.isEmpty()) {
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            return;
        }

        // 初始化变量，与上一个方法保持一致
        String taskType = null;
        String origin;
        String destin;
        String type = null;
        String agvCode = null;

        // 获取AGV
        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(messages[1]);
        if (agv != null) {
            agvCode = agv.getAgvId();
        }

        // 根据参数长度解析
        if (messages.length == 4) {
            origin = messages[2];
            destin = messages[3];
        } else if (messages.length == 5) {
            if (NumberUtil.isInteger(messages[2])) {
                taskType = messages[2];
                origin = messages[3];
                destin = messages[4];
            } else {
                origin = messages[2];
                destin = messages[3];
                type = messages[4];
            }
        } else {
            taskType = messages[2];
            origin = messages[3];
            destin = messages[4];
            type = messages[5];
        }

        // 创建任务数据
        String response;
        try {
            Entity entity = new Entity();
            String taskGroup = ConfigDB.taskGroupKey();
            entity.set("task_group", taskGroup);
            String taskCode = ConfigDB.taskCodeKey();
            entity.set("task_code", taskCode);

            // 任务类型：默认0(搬运任务)，如果提供了则使用提供的值
            entity.set("task_type", taskType == null ? 0 : Integer.parseInt(taskType));

            // 托盘类型：仅在提供时设置
            if (type != null && !type.isEmpty()) {
                entity.set("pallet_type", Integer.parseInt(type));
            }

            if (agvCode != null && !agvCode.isEmpty()) {
                entity.set("equipment_code", Integer.parseInt(agvCode));
            }
            entity.set("origin", origin);
            entity.set("destin", destin);
            entity.set("task_source", "console");

            Integer count = TaskDB.createTask(entity);
            response = count > 0
                    ? "创建任务[" + taskCode + "]成功"
                    : "创建任务[" + taskCode + "]失败，请检查数据库原因";
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, response);
        } catch (NumberFormatException e) {
            response = "创建任务失败，参数格式错误: " + e.getMessage();
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, response);
        } catch (SQLException e) {
            response = "创建任务失败，数据库错误: " + e.getMessage();
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, response);
        }
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, response));
    }

    /**
     * 查询任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void getTask(String id, String[] messages) {
        String result = ConsoleCommand.getTask(messages);
        if (result != null && !result.isEmpty()) {
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            // 记录操作日志
            RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
            return;
        }

        //获取任务
        List<TaskPath> taskPaths = TaskPathCache.get(messages[1]);
        if (taskPaths.isEmpty()) {
            result = "AGV[" + messages[1] + "]没有查询到任务";
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            // 记录操作日志
            RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
            return;
        }
        result = new JSONObject(taskPaths.getFirst(), false).toStringPretty();
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, result));
    }

    /**
     * 查询点位信息
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void getPoint(String id, String[] messages) {
        String result = ConsoleCommand.getPoint(messages);
        if (result != null && !result.isEmpty()) {
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            return;
        }

        //获取点位信息
        RcsPoint point = RcsUtils.commonPointParse(messages[1]);
        if (point == null) {
            String message = "点位[" + messages[1] + "]不存在";
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, message);
            return;
        }

        //获取点位占用情况
        RcsPointOccupy rcsOccupy = RcsPointCache.getRcsOccupy(point);
        JSONObject entries = new JSONObject(JSONConfig.create().setIgnoreNullValue(false));
        entries.set("point", point);
        entries.set("occupy", rcsOccupy);

        //发送AGV状态
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, entries.toStringPretty());
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, entries.toString()));
    }

    /**
     * 获取AGV状态
     *
     * @param id       id
     * @param messages 信息数组
     */
    private static void getAgvState(String id, String[] messages) {
        String result = ConsoleCommand.getAgvState(messages);
        if (result != null && !result.isEmpty()) {
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, result);
            return;
        }
        //获取AGV
        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(messages[1]);
        if (agv == null) {
            String message = "AGV[" + messages[1] + "]不存在或离线";
            NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, message);
            return;
        }
        //发送AGV状态
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, new JSONObject(agv, false).toStringPretty());
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, messages, new JSONObject(agv, false).toString()));
    }

    /**
     * 获取帮助
     *
     * @param id id
     */
    private static void getHelp(String id) {
        String help = ConsoleCommand.getHelp();
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, help);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, "100", help));
    }

    /**
     * 获取版本号
     *
     * @param id id
     */
    public static void getVersion(String id) {
        // 获取版本号
        String version = CoreYaml.getVersion();
        // 发送版本号
        NettyServer.getServer(ConsoleWebSocketHandler.getProtocol()).sendMessage(id, version);
        // 记录操作日志
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(id, "99", version));
    }
}
