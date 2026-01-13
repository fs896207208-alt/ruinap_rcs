package com.ruinap.infra.command.system;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.system.oshi.OshiUtil;
import com.slamopto.algorithm.domain.PlanStateEnum;
import com.slamopto.algorithm.strategy.PauseState;
import com.slamopto.algorithm.strategy.ResumeState;
import com.slamopto.command.agv.basis.CommandService;
import com.slamopto.common.config.LinkYaml;
import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.common.system.RcsUtils;
import com.slamopto.communicate.client.NettyClient;
import com.slamopto.communicate.server.NettyServer;
import com.slamopto.db.database.TaskDB;
import com.slamopto.equipment.agv.RcsAgvCache;
import com.slamopto.equipment.agv.enums.AgvStateEnum;
import com.slamopto.equipment.domain.AgvTask;
import com.slamopto.equipment.domain.RcsAgv;
import com.slamopto.log.RcsLog;
import com.slamopto.map.RcsPointCache;
import com.slamopto.map.domain.point.RcsPoint;
import com.slamopto.simulation.AirShowerSimulation;
import com.slamopto.simulation.AutoDoorSimulation;
import com.slamopto.simulation.ConveyorLineSimulation;
import com.slamopto.simulation.ElevatorSimulation;
import com.slamopto.task.TaskPathCache;
import com.slamopto.task.domain.TaskPath;
import com.slamopto.task.structure.TaskSectionManage;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;
import oshi.util.Util;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 控制台指令库
 *
 * @author qianye
 * @create 2024-07-31 4:03
 */
public class ConsoleCommand {
    /**
     * 直发指令常量
     */
    public static final String DIRECT_INSTRUCTION = "6";

    /**
     * 请求菜单指令
     *
     * @return 指令
     */
    public static String getMenu() {
        StringBuilder builder = StrUtil.builder();
        builder.append("请输入操作指令(数字)：\n");
        builder.append(" 1\t查询AGV\n");
        builder.append(" 2\t查询点位\n");
        builder.append(" 3\t查询任务\n");
        builder.append(" 4\t创建任务\n");
        builder.append(" 5\t暂停任务\n");
        builder.append(" 6\t恢复任务\n");
        builder.append(" 7\tAGV重定位\n");
        builder.append(" 8\t取消新任务\n");
        builder.append(" 9\t取消车任务\n");
        builder.append(" 10\t占用点修改\n");
        builder.append(" 11\tAGV换地图（开发中）\n");
        builder.append(" 12\t仿真操作\n");
        builder.append(" 96\t查看硬件\n");
        builder.append(" 97\t查看线程\n");
        builder.append(" 98\t查看缓存\n");
        builder.append(" 99\t查看版本\n");
        builder.append(" 100\t使用帮助");
        return builder.toString();
    }

    /**
     * 获取AGV状态
     *
     * @param messages 信息数组
     */
    public static String getAgvState(String[] messages) {
        String message = null;
        if (messages.length != 2) {
            message = "输入的格式不正确，正确指令为(括号里的参数)：[1] [空格] [AGV编号]";
            return message;
        }
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            message = "输入的 [AGV编号] 不正确，允许的参数为数字";
            return message;
        }
        return message;
    }

    /**
     * 查询点位信息
     *
     * @param messages 信息数组
     */
    public static String getPoint(String[] messages) {
        String message = null;
        if (messages.length != 2) {
            message = "输入的格式不正确，正确指令为(括号里的参数)：[2] [空格] [地图编号-点位编号]";
            return message;
        }

        // 获取点位信息
        RcsPoint rcsPoint = RcsUtils.commonPointParse(messages[1]);
        if (rcsPoint == null) {
            message = "输入的点位[" + messages[1] + "]不存在";
            return message;
        }
        return message;
    }

    /**
     * 查询任务
     *
     * @param messages 信息数组
     */
    public static String getTask(String[] messages) {
        String message = null;
        if (messages.length != 2) {
            message = "输入的格式不正确，正确指令为(括号里的参数)：[3] [空格] [AGV编号]";
            return message;
        }
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            message = "输入的参数[AGV编号]不正确，允许的参数为数字";
            return message;
        }
        return message;
    }

    /**
     * 创建任务
     *
     * @param messages 信息数组
     */
    public static String createTask(String[] messages) {
        String message = null;

        // 检查基本长度，最少需要3个参数([4] [AGV编号] [起点] [终点])
        if (messages.length < 4 || messages.length > 6) {
            StringBuilder builder = StrUtil.builder();
            builder.append("输入的格式不正确，正确指令为(括号里的参数)：\n");
            builder.append("[4] [空格] [AGV编号] [空格] [任务起点] [空格] [任务终点]\n");
            builder.append("[4] [空格] [AGV编号] [空格] [任务起点] [空格] [任务终点] [空格] [托盘类型]\n");
            builder.append("[4] [空格] [AGV编号] [空格] [任务类型] [空格] [任务起点] [空格] [任务终点]\n");
            builder.append("[4] [空格] [AGV编号] [空格] [任务类型] [空格] [任务起点] [空格] [任务终点] [空格] [托盘类型]\n\n");
            builder.append("★任务类型：0搬运任务 1充电任务 2停靠任务 3临时任务 4避让任务 (可选)\n");
            builder.append("★托盘类型：0通用 >0其他托盘(根据项目需求而定) (可选)\n");
            builder.append("★AGV编号：如果您希望不填AGV编号请在AGV编号栏填入0\n");
            builder.append("★使用示例：\n");
            builder.append("\t4 1 A1 B2\n");
            builder.append("\t4 1 A1 B2 0\n");
            builder.append("\t4 1 1 A1 B2\n");
            builder.append("\t4 1 1 A1 B2 0\n\n");
            return builder.toString();
        }

        // 验证AGV编号
        String agvCode = messages[1];
        if (!NumberUtil.isInteger(agvCode)) {
            return "输入的AGV编号[" + agvCode + "]类型不正确，允许的类型为数字组成";
        }

        // 初始化变量
        String taskType = null;
        String origin;
        String destin;
        String type = null;

        // 根据参数长度处理不同情况
        if (messages.length == 4) {
            // 只有起点和终点
            origin = messages[2];
            destin = messages[3];
        } else if (messages.length == 5) {
            // 有5个参数，可能是任务类型+起点+终点 或 起点+终点+托盘类型
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
            // 6个参数
            taskType = messages[2];
            origin = messages[3];
            destin = messages[4];
            type = messages[5];
        }

//        // 验证起点
//        if (!origin.matches("^(?!-)[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$")) {
//            return "输入的起点参数[" + origin + "]类型不正确，允许的类型为[字母数字符号-](符号-只能出现在字符串中间)组成";
//        }
//
//        // 验证终点
//        if (!destin.matches("^(?!-)[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$")) {
//            return "输入的终点参数[" + destin + "]类型不正确，允许的类型为[字母数字符号-](符号-只能出现在字符串中间)组成";
//        }

        // 验证任务类型（如果提供了）
        if (taskType != null && !taskType.matches("^\\d+$")) {
            return "输入的任务类型参数[" + taskType + "]类型不正确，必须由数字组成";
        }

        // 验证托盘类型（如果提供了）
        if (type != null && !type.matches("^\\d+$")) {
            return "输入的托盘类型参数[" + type + "]类型不正确，必须由数字组成";
        }

        // 返回null表示成功
        return message;
    }

    /**
     * AGV重定位
     *
     * @param id       id
     * @param messages 信息数组
     */
    public static String agvReLocation(String id, String[] messages) {
        String result = null;
        if (messages.length != 4) {
            StringBuilder builder = StrUtil.builder();
            builder.append("输入的格式不正确，正确指令为(括号里的参数)：\n");
            builder.append("[7] [空格] [AGV编号] [空格] [地图号] [空格] [点位编号] [空格] [朝向角度]\n");
            builder.append("★朝向角度：角度需要*100，如想让AGV角度为-90度，那么填入-9000\n\n");
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, builder.toString());
            return builder.toString();
        }
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            String message = "输入的AGV编号[" + messages[1] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        isInteger = NumberUtil.isInteger(messages[2]);
        if (!isInteger) {
            String message = "输入的地图号[" + messages[2] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        isInteger = NumberUtil.isInteger(messages[3]);
        if (!isInteger) {
            String message = "输入的点位编号[" + messages[3] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        isInteger = NumberUtil.isInteger(messages[4]);
        if (!isInteger) {
            String message = "输入的朝向角度[" + messages[4] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        //获取AGV
        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(messages[1]);
        if (agv == null) {
            String message = "AGV[" + messages[1] + "]不存在或离线";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        int mapId = Integer.parseInt(messages[2]);
        int pointId = Integer.parseInt(messages[3]);
        int angle = Integer.parseInt(messages[4]);
        boolean sendFlag = sendReLocationCommand(agv, mapId, pointId, angle);
        if (sendFlag) {
            result = "重定位AGV【" + messages[2] + "】数据成功";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, result);
        }
        return result;
    }

    /**
     * 发送重定位指令
     *
     * @param rcsAgv  AGV
     * @param mapId   地图号
     * @param pointId 目标点位
     * @param angle   朝向角度
     * @return 状态 false:失败 true:成功
     */
    public static boolean sendReLocationCommand(RcsAgv rcsAgv, Integer mapId, Integer pointId, int angle) {
        boolean flag = false;
        String agvId = rcsAgv.getAgvId();

        //获取AGV当前点位
        RcsPoint currentPoint = RcsPointCache.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
        if (currentPoint == null) {
            return flag;
        }

        //获取AGV配置
        Map<String, String> agvLink = LinkYaml.getAgvLink(rcsAgv.getAgvId());
        //品牌
        String brand = agvLink.get("brand");
        //通信协议
        String pact = agvLink.get("pact");
        //设备种类
        String equipmentType = agvLink.get("equipment_type");
        // 检查WebSocket客户端是否在线
        boolean socketOnline = NettyClient.isClientOnline(equipmentType, agvId);
        if (socketOnline) {
            //获取AGV的标识
            long mark = NettyClient.getClientCounter(equipmentType, agvId);
            //获取指令
            String moveResetCommand = CommandService.getReLocation(brand, pact, agvId, mapId, pointId, angle, mark);
            // 发送数据
            CompletableFuture<String> future = NettyClient.getClient(equipmentType, agvId).sendMessage(mark, moveResetCommand, String.class);
            try {
                // 获取返回结果
                String resultStr = future.get(3, TimeUnit.SECONDS);
                if (JSONUtil.isTypeJSON(resultStr)) {
                    JSONObject resultJsonn = JSONUtil.parseObj(resultStr);
                    String errorMessage = resultJsonn.getStr("ErrorMessage");
                    if (StrUtil.hasEmpty(errorMessage)) {
                        flag = true;
                    }
                } else {
                    RcsLog.consoleLog.error(RcsLog.formatTemplate(agvId, "重定位AGV返回的指令数据不格式不正确：" + resultStr));
                }
            } catch (Exception e) {
                e.printStackTrace();
                RcsLog.consoleLog.error(RcsLog.formatTemplate(agvId, "重定位AGV返回的指令异常：" + e.getMessage()));
            }
        }

        return flag;
    }

    /**
     * 占用点修改
     *
     * @param messages 信息数组
     */
    public static String updatePointOccupy(String[] messages) {
        StringBuilder builder = StrUtil.builder();
        if (messages.length < 2 || messages.length > 5) {
            builder.append("输入的格式不正确，正确指令为(括号里的参数)：\n");
            builder.append("[10] [空格] [地图号-点位]\n");
            builder.append("[10] [空格] [地图号-点位] [空格] [占用类型]\n\n");
            builder.append("[10] [空格] [地图号-点位] [空格] [占用类型] [空格] [直发指令]\n\n");
            builder.append("★地图号-点位：如果是单地图可以忽略地图号\n");
            builder.append("★占用类型：-1无 0任务占用 1车距占用 2管制区占用 3手动占用 4停车占用 5设备占用 6选择占用 7离线占用 8配置占用 9管制点占用 10阻断占用 (可选)\n");
            builder.append("★请注意：如果点位的占用类型已存在则取消占用，否则设置占用，不输入占用类型则默认使用【手动占用】，如果使用【直发指令】则可以取消其他设备占用的类型\n");
            builder.append("★使用示例：\n");
            builder.append("\t10 1-20 \n");
            builder.append("\t10 19 3\n");
            return builder.toString();
        }
        String point = messages[1];
        //正则判断是否是字母数字符号组成的字符串
        if (!point.matches("^(?!-)[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$")) {
            builder.append("输入的地图号-点位[" + point + "]类型不正确，允许的类型为[字母数字符号-](符号-只能出现在字符串中间)组成");
            return builder.toString();
        }
        boolean isInteger = NumberUtil.isInteger(messages[2]);
        if (!isInteger) {
            builder.append("输入的占用类型[" + messages[2] + "]类型不正确，允许的类型为数字组成");
            return builder.toString();
        }
        return builder.toString();
    }

    /**
     * 取消新任务
     *
     * @param messages 信息数组
     */
    public static String cancelNewTask(String[] messages) {
        String message = null;
        if (messages.length != 2) {
            message = "输入的格式不正确，正确指令为(括号里的参数)：[8] [空格] [任务编号]";
            return message;
        }
        String taskCode = messages[1];
        //正则判断是否是字母数字符号组成的字符串
        if (!taskCode.matches("^(?!-)[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$")) {
            message = "输入的任务编号[" + taskCode + "]类型不正确，允许的类型为[字母数字符号-](符号-只能出现在字符串中间)组成";
            return message;
        }
        return message;
    }

    /**
     * 取消AGV任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    public static String cancelAGVTask(String id, String[] messages) {
        String result = null;
        if (messages.length != 2) {
            String message = "输入的格式不正确，正确指令为(括号里的参数)：[9] [空格] [AGV编号]";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            String message = "输入的AGV编号[" + messages[1] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        //获取AGV
        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(messages[1]);
        if (agv == null) {
            String message = "AGV[" + messages[1] + "]不存在或离线";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        if (AgvStateEnum.isEnumByCode(AgvStateEnum.ACTION, agv.getAgvState())) {
            String message = "AGV[" + messages[1] + "]当前状态为[" + AgvStateEnum.ACTION.name + "]，禁止取消任务，请等待AGV做完动作再执行指令";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        //获取任务
        TaskPath taskPath = TaskPathCache.get(agv.getAgvId()).getFirst();
        if (taskPath == null) {
            String message = "AGV[" + messages[1] + "]不存在任务";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        //查询任务数据
        try {
            Condition agvIdWhere = new Condition("equipment_code", "=", agv.getAgvId());
            Condition taskStateWhere = new Condition("task_state", ">", 0);
            Condition interruptStateWhere = new Condition("interrupt_state", "=", 0);
            List<Entity> entityList = TaskDB.queryTaskList(agvIdWhere, taskStateWhere, interruptStateWhere);
            if (entityList.isEmpty()) {
                result = "取消AGV[" + agv.getAgvId() + "]的任务失败，查询不到数据";
                NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, result);
            } else {
                result = "已向AGV[" + messages[1] + "]下发取消任务指令";
                // 取消任务
                taskPath.setState(PlanStateEnum.CANCEL.code);
                NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, result);
            }
        } catch (Exception e) {
            result = "取消任务失败，" + e.getMessage();
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, result);
        }
        return result;
    }

    /**
     * 查看缓存
     *
     * @param id id
     */
    public static String getCache(String id) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("Task_Path_Cache", TaskPathCache.getAll());
        jsonObject.set("Task_Section_Cache", TaskSectionManage.getTASK_SECTION_CACHE());
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, jsonObject.toStringPretty());
        return jsonObject.toStringPretty();
    }

    /**
     * 暂停任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    public static String pauseTask(String id, String[] messages) {
        if (messages.length < 2) {
            String message = "输入的格式不正确，正确指令为(括号里的参数)：\n" +
                    "[5] [空格] [AGV编号]\n" +
                    "[5] [空格] [AGV编号] [空格] [直发指令]";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            String message = "输入的AGV编号[" + messages[1] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        //获取AGV
        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(messages[1]);
        if (agv == null) {
            String message = "AGV[" + messages[1] + "]不存在或离线";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        TaskPath taskPath = null;

        // 先尝试从缓存中获取任务路径
        List<TaskPath> taskPaths = TaskPathCache.get(agv.getAgvId());
        if (!taskPaths.isEmpty()) {
            // 获取第一条任务路径
            taskPath = taskPaths.getFirst();
        }

        // 如果缓存中没有任务路径，并且满足直发指令的条件，则尝试创建直发指令的任务路径
        if (taskPath == null && messages.length > 2 && DIRECT_INSTRUCTION.equals(messages[2])) {
            String agvId = agv.getAgvId();
            // 获取AGV任务信息
            AgvTask agvTask = RcsAgvCache.getAGV_TASK_CACHE().getOrDefault(agvId, null);
            if (agvTask != null) {
                RcsPoint currentPlanOrigin = RcsPointCache.getRcsPoint(agv.getMapId(), agvTask.getTaskStartId());
                RcsPoint currentPlanDestin = RcsPointCache.getRcsPoint(agv.getMapId(), agvTask.getTaskEndId());

                String taskId = agvTask.getTaskId();
                String[] split = taskId.split("-");
                if (split.length < 2) {
                    String message = "AGV[" + messages[1] + "]车体没有任务号，直发指令失败";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
                    return message;
                }

                // 创建新的任务路径
                taskPath = new TaskPath();
                taskPath.setAgvId(agvId);
                taskPath.setTaskCode(split[0]);
                taskPath.setSubTaskNo(Integer.parseInt(split[1]));
                taskPath.setPathCode(1);
                taskPath.setCurrentPlanOrigin(currentPlanOrigin);
                taskPath.setCurrentPlanDestin(currentPlanDestin);

                //发送暂停指令
                String command = PauseState.sendCommand(taskPath);
                if (command != null && !command.isEmpty()) {
                    String message = "向AGV[" + messages[1] + "]发送PAUSE指令成功：" + command;
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
                    // 直发指令成功发送，直接返回
                    return message;
                } else {
                    String message = "向AGV[" + messages[1] + "]发送PAUSE指令失败";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
                    // 直发指令发送失败，直接返回
                    return message;
                }
            } else {
                String message = "AGV[" + messages[1] + "]车体没有任务，无法发送暂停指令";
                NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
                return message;
            }
        } else if (messages.length > 2 && !DIRECT_INSTRUCTION.equals(messages[2])) {
            String message = "AGV[" + messages[1] + "]使用了无效的直发指令，取消本次操作";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        // 如果 taskPath 不为空，则设置其状态为 PAUSE
        if (taskPath != null) {
            taskPath.setState(PlanStateEnum.PAUSE.code);
            String message = "AGV[" + messages[1] + "]已成功设置暂停状态";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        } else {
            String message = "AGV[" + messages[1] + "]不存在任务，如需直接给AGV发送暂停指令，请传入直发指令";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
    }

    /**
     * 恢复任务
     *
     * @param id       id
     * @param messages 信息数组
     */
    public static String recoverTask(String id, String[] messages) {
        if (messages.length < 2) {
            String message = "输入的格式不正确，正确指令为(括号里的参数)：\n" +
                    "[6] [空格] [AGV编号]";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            String message = "输入的AGV编号[" + messages[1] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
        //获取AGV
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(messages[1]);
        if (rcsAgv == null) {
            String message = "AGV[" + messages[1] + "]不存在或离线";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }

        TaskPath taskPath = null;

        String agvId = rcsAgv.getAgvId();
        // 先尝试从缓存中获取任务路径
        List<TaskPath> taskPaths = TaskPathCache.get(agvId);
        if (!taskPaths.isEmpty()) {
            // 获取第一条任务路径
            taskPath = taskPaths.getFirst();
        }

        // 如果缓存中没有任务路径，并且满足直发指令的条件
        if (taskPath != null) {
            //发送恢复指令
            String command = ResumeState.sendCommand(taskPath);
            if (command != null && !command.isEmpty()) {
                String message = "请求向AGV[" + messages[1] + "]发送恢复指令成功";
                NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
                // 直发指令成功发送，直接返回
                return message;
            } else {
                String message = "向AGV[" + messages[1] + "]发送恢复指令失败";
                NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
                // 直发指令发送失败，直接返回
                return message;
            }
        } else {
            String message = "AGV[" + messages[1] + "]不存在任务，无法发送恢复指令";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, message);
            return message;
        }
    }

    /**
     * 仿真操作
     *
     * @param serverId id
     * @param messages 信息数组
     */
    public static String operateSimulation(String serverId, String[] messages) {
        if (messages.length < 4) {
            StringBuilder builder = StrUtil.builder();
            builder.append("输入的格式不正确，正确指令为(括号里的参数)：\n");
            builder.append("==================== 其  他 ====================\n");
            builder.append("[12] [空格] [设备类型] [空格] [设备编号] [空格] [新数据]\n");
            builder.append("[12] [空格] [设备类型] [空格] [设备编号] [空格] [新数据] [空格] [修改字段]\n");
            builder.append("==================== 输送线 ====================\n");
            builder.append("[12] [空格] [设备类型] [空格] [设备编号] [空格] [对接点编号] [空格] [新数据]\n");
            builder.append("[12] [空格] [设备类型] [空格] [设备编号] [空格] [对接点编号] [空格] [新数据] [空格] [修改字段]\n");
            builder.append("[12] [空格] [0] [空格] [AGV编号] [空格] [新点位] [空格] [新角度] [空格] [新电量]\n");
            builder.append("★设备类型：0AGV 1自动门 2输送线 3风淋室 4电梯\n");
            builder.append("★默认修改state数据，除非指定字段\n");
            builder.append("\n");
            builder.append("==================== 自动门 ====================\n");
            LinkedHashMap<String, LinkedHashMap<String, Integer>> autoDoor = AutoDoorSimulation.getAutoDoor();
            for (Map.Entry<String, LinkedHashMap<String, Integer>> entrys : autoDoor.entrySet()) {
                LinkedHashMap<String, Integer> values = entrys.getValue();
                builder.append(entrys.getKey() + "\n");
                for (Map.Entry<String, Integer> fieldEntry : values.entrySet()) {
                    builder.append("\t" + fieldEntry.getKey() + "：" + fieldEntry.getValue() + "\n");
                }
                builder.append("\n");
            }

            builder.append("==================== 输送线 ====================\n");
            LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Integer>>> conveyorLine = ConveyorLineSimulation.getConveyorLine();
            for (Map.Entry<String, LinkedHashMap<String, LinkedHashMap<String, Integer>>> entry : conveyorLine.entrySet()) {
                // 输出输送线主编号
                builder.append(entry.getKey() + "\n");
                for (Map.Entry<String, LinkedHashMap<String, Integer>> entry1 : entry.getValue().entrySet()) {
                    // 输出子设备编号
                    builder.append("\t" + entry1.getKey() + ":\n");
                    for (Map.Entry<String, Integer> fieldEntry : entry1.getValue().entrySet()) {
                        builder.append("\t\t" + fieldEntry.getKey() + "：" + fieldEntry.getValue() + "\n");
                    }
                }
                builder.append("\n");
            }

            builder.append("\n==================== 风淋室 ====================\n");
            LinkedHashMap<String, LinkedHashMap<String, Integer>> airShower = AirShowerSimulation.getAirShower();
            for (Map.Entry<String, LinkedHashMap<String, Integer>> entry : airShower.entrySet()) {
                // 输出风淋室编号
                builder.append(entry.getKey() + "\n");
                for (Map.Entry<String, Integer> fieldEntry : entry.getValue().entrySet()) {
                    builder.append("\t" + fieldEntry.getKey() + "：" + fieldEntry.getValue() + "\n");
                }
                builder.append("\n");
            }

            builder.append("\n==================== 电  梯 ====================\n");
            LinkedHashMap<String, LinkedHashMap<String, Integer>> elevator = ElevatorSimulation.getElevator();
            for (Map.Entry<String, LinkedHashMap<String, Integer>> entry : elevator.entrySet()) {
                // 输出电梯编号
                builder.append(entry.getKey() + "\n");
                for (Map.Entry<String, Integer> fieldEntry : entry.getValue().entrySet()) {
                    builder.append("\t" + fieldEntry.getKey() + "：" + fieldEntry.getValue() + "\n");
                }
                builder.append("\n");
            }
            builder.append("==============================================\n\n");

            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, builder.toString());
            return builder.toString();
        }

        String message = null;
        boolean isInteger = NumberUtil.isInteger(messages[1]);
        if (!isInteger) {
            message = "输入的设备类型[" + messages[1] + "]类型不正确，允许的类型为数字组成";
            NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
            return message;
        }

        //设备类型：0AGV 1自动门 2输送线 3风淋室 4电梯
        switch (Integer.parseInt(messages[1])) {
            case 0:
                //获取AGV数据
                RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(messages[2]);
                if (rcsAgv == null) {
                    message = "AGV【" + messages[2] + "】查询不到数据";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    return message;
                }
                //获取AGV配置
                Map<String, String> agvLink = LinkYaml.getAgvLink(messages[2]);

                //判断如果长度是6，则是修改发送重置指令
                if (messages.length == 6) {
                    boolean pointIdf = NumberUtil.isInteger(messages[3]);
                    if (!pointIdf) {
                        message = "输入的新点位[" + messages[3] + "]类型不正确，允许的类型为数字组成";
                        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                        return message;
                    }
                    boolean pointIdy = NumberUtil.isInteger(messages[4]);
                    if (!pointIdy) {
                        message = "输入的新角度[" + messages[4] + "]类型不正确，允许的类型为数字组成";
                        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                        return message;
                    }
                    boolean pointIdb = NumberUtil.isInteger(messages[5]);
                    if (!pointIdb) {
                        message = "输入的新电量[" + messages[5] + "]类型不正确，允许的类型为数字组成";
                        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                        return message;
                    }

                    int pointId = Integer.parseInt(messages[3]);
                    int yaw = Integer.parseInt(messages[4]);
                    int battery = Integer.parseInt(messages[5]);
                    int load = 0;
                    boolean sendFlag = sendMoveResetCommand(rcsAgv, agvLink, pointId, yaw, battery, load);
                    if (sendFlag) {
                        message = "重置仿真AGV【" + messages[2] + "】数据成功";
                        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    }
                } else {
                    message = "重置仿真AGV【" + messages[2] + "】，参数数量校验失败请检查输入参数";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    return message;
                }
                break;
            case 1:
                //获取自动门数据
                LinkedHashMap<String, Integer> autoDoorMap = AutoDoorSimulation.getAutoDoor(messages[2]);
                if (autoDoorMap.isEmpty()) {
                    message = "自动门【" + messages[2] + "】查询不到数据";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    return message;
                }
                //判断如果长度是5，则是修改指定字段的值
                if (messages.length >= 5) {
                    autoDoorMap.put(messages[4], Integer.parseInt(messages[3]));
                    message = "修改自动门字段【" + messages[4] + "】数据成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                } else {
                    autoDoorMap.put("state", Integer.parseInt(messages[3]));
                    message = "修改自动门【" + messages[2] + "】状态成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                }
                break;
            case 2:
                //获取输送线数据
                LinkedHashMap<String, Integer> conveyorLine = ConveyorLineSimulation.getConveyorLine(messages[2], messages[3]);
                if (conveyorLine.isEmpty()) {
                    message = "输送线【" + messages[2] + "】查询不到数据";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    return message;
                }
                //判断如果长度是5，则是修改指定字段的值
                if (messages.length >= 6) {
                    conveyorLine.put(messages[5], Integer.parseInt(messages[4]));
                    message = "修改输送线字段【" + messages[3] + "】数据成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                } else {
                    conveyorLine.put("state", Integer.parseInt(messages[4]));
                    message = "修改输送线【" + messages[3] + "】状态成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                }
                break;
            case 3:
                //获取风淋室数据
                LinkedHashMap<String, Integer> airShower = AirShowerSimulation.getAirShower(messages[2]);
                if (airShower == null || airShower.isEmpty()) {
                    message = "风淋室【" + messages[2] + "】查询不到数据";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    return message;
                }
                //判断如果长度是5，则是修改指定字段的值
                if (messages.length >= 5) {
                    airShower.put(messages[4], Integer.parseInt(messages[3]));
                    message = "修改自动门字段【" + messages[4] + "】数据成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                } else {
                    airShower.put("state", Integer.parseInt(messages[3]));
                    message = "修改自动门【" + messages[2] + "】状态成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                }
                break;
            case 4:
                //获取电梯数据
                LinkedHashMap<String, Integer> elevator = ElevatorSimulation.getElevator(messages[2]);
                if (elevator == null || elevator.isEmpty()) {
                    message = "电梯【" + messages[2] + "】查询不到数据";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                    return message;
                }
                //判断如果长度是5，则是修改指定字段的值
                if (messages.length >= 5) {
                    elevator.put(messages[4], Integer.parseInt(messages[3]));
                    message = "修改电梯字段【" + messages[4] + "】数据成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                } else {
                    elevator.put("state", Integer.parseInt(messages[3]));
                    message = "修改电梯【" + messages[2] + "】状态成功";
                    NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, message);
                }
                break;
            default:
                break;
        }
        return message;
    }

    /**
     * 发送仿真重置指令
     *
     * @param rcsAgv  AGV
     * @param agvLink AGV配置
     * @param pointId 目标点位
     * @param yaw     朝向角度
     * @param battery 电池电量
     * @param load    装载状态
     * @return 状态 false:失败 true:成功
     */
    public static boolean sendMoveResetCommand(RcsAgv rcsAgv, Map<String, String> agvLink, int pointId, int yaw, int battery, int load) {
        boolean flag = false;
        String agvId = rcsAgv.getAgvId();

        //获取AGV当前点位
        RcsPoint currentPoint = RcsPointCache.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
        if (currentPoint == null) {
            return flag;
        }
        //品牌
        String brand = agvLink.get("brand");
        //通信协议
        String pact = agvLink.get("pact");
        //设备种类
        String equipmentType = agvLink.get("equipment_type");
        // 检查WebSocket客户端是否在线
        boolean socketOnline = NettyClient.isClientOnline(equipmentType, agvId);
        if (socketOnline) {
            //获取AGV的标识
            long mark = NettyClient.getClientCounter(equipmentType, agvId);
            //获取指令
            String moveResetCommand = CommandService.getMoveReset(brand, pact, agvId, pointId, yaw, battery, load, mark);
            // 发送数据
            CompletableFuture<String> future = NettyClient.getClient(equipmentType, agvId).sendMessage(mark, moveResetCommand, String.class);
            try {
                // 获取返回结果
                String resultStr = future.get(3, TimeUnit.SECONDS);
                if (JSONUtil.isTypeJSON(resultStr)) {
                    JSONObject resultJsonn = JSONUtil.parseObj(resultStr);
                    String errorMessage = resultJsonn.getStr("ErrorMessage");
                    if (StrUtil.hasEmpty(errorMessage)) {
                        flag = true;
                    }
                } else {
                    RcsLog.consoleLog.error(RcsLog.formatTemplate(agvId, "重置仿真AGV返回的指令数据不格式不正确：" + resultStr));
                }
            } catch (Exception e) {
                e.printStackTrace();
                RcsLog.consoleLog.error(RcsLog.formatTemplate(agvId, "重置仿真AGV返回的指令异常：" + e.getMessage()));
            }
        }

        return flag;
    }

    /**
     * 获取帮助
     */
    public static String getHelp() {
        StringBuilder builder = StrUtil.builder();
        builder.append("您当前在帮助页：(括号里的参数)\n");
        builder.append(" 查询AGV：\t[1] [空格] [AGV编号]\n");
        builder.append(" 查询点位：\t[2] [空格] [点位编号]\n");
        builder.append(" 查询任务：\t[3] [空格] [AGV编号]\n");
        builder.append(" 创建任务：\t[4] [空格] [AGV编号] [空格] [任务起点] [空格] [任务终点]\n");
        builder.append(" 暂停任务：\t[5] [空格] [AGV编号]\n");
        builder.append(" 恢复任务：\t[6] [空格] [AGV编号]\n");
        builder.append(" AGV重定位：\t[7] [空格] [AGV编号] [空格] [地图号] [空格] [点位编号] [空格] [朝向角度]\n");
        builder.append(" 取消新任务：\t[8] [空格] [任务编号]\n");
        builder.append(" 取消车任务：\t[9] [空格] [AGV编号]\n");
        builder.append(" 占用点修改：\t[10] [空格] [点位编号]\n");
        builder.append(" AGV换地图：\t[11] [空格] [点位编号]\n");
        builder.append(" 仿真操作：\t[12] [空格] [0] [空格] [AGV编号] [空格] [新点位] [空格] [新角度] [空格] [新电量]\n");
        builder.append(" 查看硬件：\t[96]\n");
        builder.append(" 查看线程：\t[97]\n");
        builder.append(" 查看缓存：\t[98]\n");
        builder.append(" 查看版本：\t[99]\n");
        builder.append(" 使用帮助：\t[100]\n");
        builder.append(" ★请注意：\t以上指令均是最简单的指令，如需查看完全的指令请发送单独的【指令号】获取完整指令");
        return builder.toString();
    }

    /**
     * 查看服务器参数
     *
     * @param id id
     */
    public static String getServerConfig(String id) {
        StringBuilder sb = new StringBuilder();
        sb.append("您的服务器配置参数: \n");
        sb.append("\n");

        // 获取操作系统信息
        OperatingSystem os = OshiUtil.getOs();
        // 获取操作系统名称
        String osName = os.toString();
        sb.append("操作系统: ").append(osName).append("\n");

        // 获取操作系统版本信息
        String versionInfo = os.getFamily() + " " + os.getVersionInfo().getVersion();
        sb.append("系统版本: ").append(versionInfo).append("\n");

        // 获取系统信息
        SystemInfo systemInfo = new SystemInfo();
        // 获取硬件信息
        HardwareAbstractionLayer hal = systemInfo.getHardware();

        sb.append("\n");
        // 获取CPU信息
        CentralProcessor processor = hal.getProcessor();
        sb.append("CPU型号信息: ").append(processor.getProcessorIdentifier().getName()).append("\n");
        sb.append("物理CPU核心数: ").append(processor.getPhysicalProcessorCount()).append("\n");
        sb.append("逻辑CPU核心数: ").append(processor.getLogicalProcessorCount()).append("\n");

        // 获取系统的整体CPU负载
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        // 等待1秒钟
        Util.sleep(1000);
        double systemCpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);

        // 创建DecimalFormat以格式化百分比
        DecimalFormat df = new DecimalFormat("0.00%");
        sb.append("系统CPU负载: ").append(df.format(systemCpuLoad)).append("\n");

        // 获取每个CPU核心的负载百分比
        long[][] prevCpuTicks = processor.getProcessorCpuLoadTicks();
        // 这里需要等待一段时间以计算CPU负载，例如等待1秒钟
        Util.sleep(1000);
        // 获取当前的CPU负载百分比（基于前后的tick差异）
        double[] loadTicks = processor.getProcessorCpuLoadBetweenTicks(prevCpuTicks);
        sb.append("每个CPU负载: ");
        for (int i = 0; i < loadTicks.length; i++) {
            sb.append("\n");
            sb.append("\t核心 ").append(i).append(": ").append(String.format("%.2f%%", loadTicks[i] * 100)).append(" ");
        }
        sb.append("\n");


        sb.append("\n");
        // 获取内存信息
        GlobalMemory memory = hal.getMemory();
        sb.append("总物理内存: ").append(FormatUtil.formatBytes(memory.getTotal())).append("\n");
        sb.append("已使用物理内存: ").append(FormatUtil.formatBytes(memory.getTotal() - memory.getAvailable())).append("\n");
        sb.append("可用物理内存: ").append(FormatUtil.formatBytes(memory.getAvailable())).append("\n");
        sb.append("\n");

        // 获取磁盘信息
        sb.append("磁盘信息:\n");
        for (OSFileStore fs : os.getFileSystem().getFileStores()) {
            sb.append("\t- 挂载点: ").append(fs.getMount()).append("\n")
                    .append("\t\t文件系统类型: ").append(fs.getType()).append("\n")
                    .append("\t\t已使用空间: ").append(FormatUtil.formatBytes(fs.getTotalSpace() - fs.getUsableSpace())).append("\n")
                    .append("\t\t可用空间: ").append(FormatUtil.formatBytes(fs.getUsableSpace())).append("\n")
                    .append("\t\t总空间: ").append(FormatUtil.formatBytes(fs.getTotalSpace())).append("\n");
        }
        sb.append("\n");

        // 获取网络接口信息
        sb.append("网络接口:\n");
        for (NetworkIF net : hal.getNetworkIFs()) {
            String name = net.getName();
            String[] ipv4Addr = net.getIPv4addr();

            // 只显示以太网（eth）和WiFi（wir）接口，并且需要有IP地址
            if ((name.startsWith("eth") || name.startsWith("wir")) && ipv4Addr.length > 0) {
                sb.append("\t- 接口名称: ").append(name).append("\n");
                sb.append("\t\tIP地址: ").append(ipv4Addr[0]).append("\n");
                sb.append("\t\t收到的数据: ").append(FormatUtil.formatBytes(net.getBytesRecv())).append("\n");
                sb.append("\t\t发送的数据: ").append(FormatUtil.formatBytes(net.getBytesSent())).append("\n");
            }
        }
        sb.append("\n");

        // 获取电池信息（如果有）
        for (PowerSource powerSource : hal.getPowerSources()) {
            sb.append("电池名称: ").append(powerSource.getName()).append("\n");
            // 获取当前电量百分比
            sb.append("当前电量: ").append(powerSource.getRemainingCapacityPercent() * 100).append("%\n");
            // 判断是否正在充电
            String timeRemainingStr;
            if (powerSource.isCharging()) {
                // 如果正在充电，显示“正在充电”
                timeRemainingStr = "正在充电";
            } else {
                // 如果未充电，显示“未充电”
                timeRemainingStr = "未充电";
            }
            sb.append("充电状态: ").append(timeRemainingStr).append("\n");

        }

        // 获取USB设备信息
        sb.append("USB设备:\n");
        hal.getUsbDevices(true).forEach(usb -> {
            sb.append("\t- 设备: ").append(usb.getName()).append("\n");
        });
        sb.append("\n");

        // 将收集到的信息通过WebSocket发送给客户端
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(id, sb.toString());
        return sb.toString();
    }
}
