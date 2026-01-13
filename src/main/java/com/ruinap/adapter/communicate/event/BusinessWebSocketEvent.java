package com.ruinap.adapter.communicate.event;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.slamopto.algorithm.domain.PlanStateEnum;
import com.slamopto.command.system.BusinessCommand;
import com.slamopto.communicate.base.event.AbstractServerEvent;
import com.slamopto.communicate.server.NettyServer;
import com.slamopto.communicate.server.handler.impl.BusinessWebSocketHandler;
import com.slamopto.db.DbCache;
import com.slamopto.db.database.AgvDB;
import com.slamopto.db.database.ConfigDB;
import com.slamopto.db.database.TaskDB;
import com.slamopto.equipment.agv.RcsAgvCache;
import com.slamopto.equipment.domain.RcsAgv;
import com.slamopto.log.RcsLog;
import com.slamopto.task.TaskPathCache;
import com.slamopto.task.domain.RcsTask;
import com.slamopto.task.domain.TaskPath;
import com.slamopto.task.enums.TaskStateEnum;
import com.slamopto.task.enums.TaskTypeEnum;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 调度业务事件
 *
 * @author qianye
 * @create 2025-05-19 19:27
 */
public class BusinessWebSocketEvent extends AbstractServerEvent {

    /**
     * 处理事件
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    public static void receiveMessage(String serverId, JSONObject jsonObject) {
        String event = jsonObject.getStr("event");
        switch (event.toLowerCase()) {
            case "task_create":
                // 创建任务
                taskCreate(serverId, jsonObject);
                break;
            case "tasks_create":
                // 创建任务
                tasksCreate(serverId, jsonObject);
                break;
            case "task_cancel":
                // 取消任务
                taskCancel(serverId, jsonObject);
                break;
            case "agv_state":
                // AGV状态查询
                agvState(serverId, jsonObject);
                break;
            case "task_list":
                // 任务数据查询
                taskList(serverId, jsonObject);
                break;
            case "to_top_task":
                // 置顶任务
                topTask(serverId, jsonObject);
                break;
            case "do_charge":
                // 请求充电事件
                doCharge(serverId, jsonObject);
                break;
            case "do_move":
                // 请求移动事件
                doMove(serverId, jsonObject);
                break;
            case "agv_info":
                // 请求AGV信息
                agvInfo(serverId, jsonObject);
                break;
            default:
                RcsLog.consoleLog.error("Business 未知事件：" + event);
                break;
        }
    }

    /**
     * 请求AGV信息
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void agvInfo(String serverId, JSONObject jsonObject) {

        ArrayList<JSONObject> arrayList = new ArrayList<>();
        RcsAgvCache.getRcsAgvMap().forEach((key, agv) -> {
            arrayList.add(new JSONObject(agv));
        });
        JSONObject entries = BusinessCommand.agvInfo(200, "成功", new JSONArray(arrayList, false));
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * 请求移动事件
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void doMove(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "doMove - 收到中转系统数据", jsonObject.toString()));

        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || data.isEmpty()) {
            JSONObject entries = BusinessCommand.doMove(500, "传入的 data 数据不能为空", new JSONObject());
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        //返回的data
        JSONObject dataJson = new JSONObject();
        String agvCode = data.getStr("id");
        dataJson.set("id", agvCode);
        if (StrUtil.hasEmpty(agvCode)) {
            JSONObject entries = BusinessCommand.doMove(500, "传入的AGV编号不能为空", dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        String origin = data.getStr("origin_id");
        if (StrUtil.hasEmpty(origin)) {
            JSONObject entries = BusinessCommand.doMove(500, "传入的任务起点不能为空", dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        String destin = data.getStr("destin_id");
        if (StrUtil.hasEmpty(destin)) {
            JSONObject entries = BusinessCommand.doMove(500, "传入的任务终点不能为空", dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        try {
            Entity entity = new Entity();
            String taskGroup = ConfigDB.taskGroupKey();
            entity.set("task_group", taskGroup);
            String taskCode = ConfigDB.taskCodeKey();
            entity.set("task_code", taskCode);
            // 任务类型 0搬运任务 1充电任务 2停靠任务 3临时任务 4避让任务
            entity.set("task_type", 2);
            entity.set("equipment_code", agvCode);

            entity.set("origin", origin);
            entity.set("destin", destin);
            entity.set("task_source", "中转");

            int count = TaskDB.createTask(entity);
            String response = count > 0
                    ? "创建任务[" + taskCode + "]成功"
                    : "创建任务[" + taskCode + "]失败，请检查数据库原因";

            if (count > 0) {
                JSONObject entries = BusinessCommand.doMove(200, response, dataJson);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            } else {
                JSONObject entries = BusinessCommand.doMove(500, response, dataJson);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            }
        } catch (SQLException e) {
            JSONObject entries = BusinessCommand.doMove(500, "创建失败，发生异常：" + e.getMessage(), dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
        }
    }

    /**
     * 请求充电事件
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void doCharge(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "doCharge - 收到中转系统数据", jsonObject.toString()));
        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || data.isEmpty()) {
            JSONObject entries = BusinessCommand.doCharge(500, "传入的 data 数据不能为空", new JSONObject());
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        JSONObject dataJson = new JSONObject();
        String id = data.getStr("id");
        dataJson.set("id", id);
        if (StrUtil.hasEmpty(id)) {
            JSONObject entries = BusinessCommand.doCharge(500, "传入的AGV编号不能为空", dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        // 优先级：0低优先级 1高优先级
        Integer priority = data.getInt("priority");
        if (priority == null) {
            JSONObject entries = BusinessCommand.doCharge(500, "传入的优先级不能为空", dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        //获取AGV信息
        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(id);
        //遍历任务集合
        for (RcsTask task : DbCache.RCS_TASK_MAP.values()) {
            //任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 97取货运行中 98卸货运行中 99运行中
            Integer taskState = task.getTaskState();
            // 设备编码
            String equipmentCode = task.getEquipmentCode();
            if (taskState > 0 && agv.getAgvId().equalsIgnoreCase(equipmentCode)) {
                //如果有充电任务直接跳过
                if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CHARGE, task.getTaskType())) {
                    JSONObject entries = BusinessCommand.doCharge(500, "当前AGV已经有充电任务，请勿重复操作", dataJson);
                    NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                    return;
                }
            }
        }

        if (agv != null) {
            //充电信号 0正常 1高优先级信号 2低优先级信号
            Integer chargeSignal = agv.getChargeSignal();
            if (chargeSignal.equals(0)) {
                try {
                    Integer count = AgvDB.updateAgv(new Entity().set("charge_signal", priority.equals(1) ? 1 : 2), new Entity().set("agv_id", agv.getAgvId()));
                    if (count > 0) {
                        JSONObject entries = BusinessCommand.doCharge(200, "成功", dataJson);
                        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                    }
                } catch (SQLException e) {
                    JSONObject entries = BusinessCommand.doCharge(500, "操作异常：" + e.getMessage(), dataJson);
                    NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                }
            } else {
                JSONObject entries = BusinessCommand.doCharge(500, "已设置充电信号，请勿重复操作", dataJson);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            }
        } else {
            dataJson.set("id", id);
            JSONObject entries = BusinessCommand.doCharge(500, "传入的AGV编号 " + id + " 查询不到AGV", dataJson);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
        }
    }

    /**
     * 置顶任务
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void topTask(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "topTask - 收到中转系统数据", jsonObject.toString()));

        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || data.isEmpty()) {
            JSONObject entries = BusinessCommand.topTask(500, "传入的 data 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        // 任务组号
        String taskGroup = data.getStr("task_group");
        if (StrUtil.hasEmpty(taskGroup)) {
            throw new RuntimeException("任务组号不能为空");
        }

        int count = 0;
        try {
            Entity whereEntity = new Entity();
            whereEntity.set("task_group", taskGroup);

            Entity entity = new Entity();
            entity.set("task_priority", 10);
            entity.set("priority_time", new Date());

            count += TaskDB.updateTask(entity, whereEntity);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        JSONObject entries;
        if (count > 0) {
            entries = BusinessCommand.topTask(200, "成功", null);
        } else {
            entries = BusinessCommand.topTask(500, "任务置顶失败，无有效任务置顶", null);
        }
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * 任务数据查询
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void taskList(String serverId, JSONObject jsonObject) {

        List<RcsTask> entityList = DbCache.RCS_TASK_MAP.values();
        // 复制一份新的数据
        CopyOnWriteArrayList<RcsTask> filteredTaskList = new CopyOnWriteArrayList<>(entityList);

        JSONObject entries = BusinessCommand.getTaskList(200, "成功", filteredTaskList);
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * AGV状态查询
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void agvState(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "agvState - 收到中转系统数据", jsonObject.toString()));

        JSONObject entries = BusinessCommand.getAgvState(200, "成功", DbCache.RCS_AGV_MAP.values().stream().toList());
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * 取消任务
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    public static void taskCancel(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "taskCancel - 收到中转系统数据", jsonObject.toString()));

        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || data.isEmpty()) {
            JSONObject entries = BusinessCommand.taskCancel(500, "传入的 data 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        // 任务组号
        String taskGroup = data.getStr("task_group");
        if (StrUtil.hasEmpty(taskGroup)) {
            throw new RuntimeException("任务组号不能为空");
        }

        Condition taskGroupCondition = new Condition("task_group", "=", taskGroup);
        Condition taskStateWhere = new Condition("task_state", ">", 0);
        Condition interruptStateWhere = new Condition("interrupt_state", "=", 0);

        int count = 0;
        try {
            List<Entity> entities = TaskDB.queryTaskList(taskGroupCondition, taskStateWhere, interruptStateWhere);
            if (entities.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("【" + taskGroup + "】查询不到任务数据"));
            }
            for (Entity entitySql : entities) {
                String taskCode = entitySql.getStr("task_code");
                //从缓存中获取任务
                RcsTask rcsTask = DbCache.RCS_TASK_MAP.get(taskCode);
                if (rcsTask != null) {
                    if (rcsTask.getEquipmentCode() != null) {
                        //获取AGV
                        RcsAgv agv = RcsAgvCache.getRcsAgvByCode(rcsTask.getEquipmentCode());
                        if (agv.getTaskId().contains(taskCode)) {
                            boolean hasTask = TaskPathCache.hasTask(rcsTask.getEquipmentCode());
                            if (hasTask) {
                                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("从缓存中获取到任务，取消任务并向AGV发送取消指令"));
                                Integer taskState = rcsTask.getTaskState();
                                // 判断任务是否已取消
                                if (taskState < TaskStateEnum.FINISH.code) {
                                    throw new RuntimeException("任务编号【" + taskGroup + "-" + taskCode + "】已取消");
                                } else if (TaskStateEnum.isEnumByCode(TaskStateEnum.NEW, taskState)) {
                                    RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("任务未下发，直接取消任务"));
                                    rcsTask.setTaskState(-1);
                                } else {
                                    //更新缓存
                                    TaskPath first = TaskPathCache.getFirst(rcsTask.getEquipmentCode());
                                    if (first != null) {
                                        first.setState(PlanStateEnum.CANCEL.code);
                                        //设置取消类型 0正常取消 1强制取消
                                        first.setCancelType(1);
                                        count += 1;
                                    }
                                }
                            }
                        } else {
                            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(agv.getAgvId(), "非车体任务，直接取消任务"));
                            rcsTask.setTaskState(-1);
                        }

                    } else {
                        RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("任务未下发，直接取消任务"));
                        rcsTask.setTaskState(-1);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        JSONObject entries;
        if (count > 0) {
            entries = BusinessCommand.taskCancel(200, "成功", null);
        } else {
            entries = BusinessCommand.taskCancel(500, "任务取消失败，无有效任务取消", null);
        }
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * 创建任务
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void taskCreate(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "taskCreate - 收到中转系统数据", jsonObject.toString()));
        JSONObject data = jsonObject.getJSONObject("data");
        if (data == null || data.isEmpty()) {
            JSONObject entries = BusinessCommand.taskCreate(500, "传入的 data 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        // 任务组
        String taskGroup = data.getStr("task_group");
        if (StrUtil.hasEmpty(taskGroup)) {
            JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务组 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        // 任务来源
        String taskSource = data.getStr("task_source");
        if (StrUtil.hasEmpty(taskSource)) {
            JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务来源 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        // 指定AGV，不填则系统推荐
        String equipmentCode = data.getStr("equipment_code");
        // 托盘类型
        Integer palletType = data.getInt("pallet_type");
        // 送料任务起点
        String taskOrigin = data.getStr("task_origin");
        // 送料任务终点
        String taskDestin = data.getStr("task_destin");
        // 备注
        String remark = data.getStr("remark");

        List<Entity> entityList = new ArrayList<>(2);
        // 任务类型 1送料 2退料 3送料+退料
        int taskType = data.getInt("task_type");
        if (StrUtil.hasEmpty(taskOrigin)) {
            JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务起点 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        if (StrUtil.hasEmpty(taskDestin)) {
            JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务终点 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        // 送料
        entityList.add(addTaskEntity(taskGroup, equipmentCode, palletType, taskOrigin, taskDestin, 1, 0, taskSource, remark));

        int count = 0;
        try {
            for (Entity taskEntity : entityList) {
                count += TaskDB.createTask(taskEntity);
            }
        } catch (SQLException e) {
            RcsLog.consoleLog.error("任务创建失败: " + e.getMessage());
            JSONObject entries = BusinessCommand.taskCreate(500, "任务创建失败: " + e.getMessage(), null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        JSONObject entries;
        if (count > 0) {
            entries = BusinessCommand.taskCreate(200, "成功", remark);
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("任务创建成功", entries));
        } else {
            entries = BusinessCommand.taskCreate(500, "任务创建失败，无有效任务新增", null);
        }
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * 创建组任务
     *
     * @param serverId   服务端id
     * @param jsonObject 数据
     */
    private static void tasksCreate(String serverId, JSONObject jsonObject) {
        RcsLog.operateLog.info(RcsLog.formatTemplateRandom(serverId, "tasksCreate - 收到中转系统数据", jsonObject.toString()));

        JSONArray dataArray = jsonObject.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            JSONObject entries = BusinessCommand.taskCreate(500, "传入的 data 数据不能为空", null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }

        List<Entity> entityList = new ArrayList<>();
        String remark = "";
        int rank = 1;
        for (Object obj : dataArray) {
            JSONObject data = (JSONObject) obj;
            // 任务组
            String taskGroup = data.getStr("task_group");
            if (StrUtil.hasEmpty(taskGroup)) {
                JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务组 数据不能为空", null);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                return;
            }
            // 任务来源
            String taskSource = data.getStr("task_source");
            if (StrUtil.hasEmpty(taskSource)) {
                JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务来源 数据不能为空", null);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                return;
            }

            // 指定AGV，不填则系统推荐
            String equipmentCode = data.getStr("equipment_code");
            // 托盘类型
            Integer palletType = data.getInt("pallet_type");
            // 送料任务起点
            String taskOrigin = data.getStr("task_origin");
            // 送料任务终点
            String taskDestin = data.getStr("task_destin");
            // 备注
            remark = data.getStr("remark");

            // 任务类型 1送料 2退料 3送料+退料
            int taskType = data.getInt("task_type");
            if (StrUtil.hasEmpty(taskOrigin)) {
                JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务起点 数据不能为空", null);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                return;
            }
            if (StrUtil.hasEmpty(taskDestin)) {
                JSONObject entries = BusinessCommand.taskCreate(500, "传入的 任务终点 数据不能为空", null);
                NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
                return;
            }
            // 送料
            entityList.add(addTaskEntity(taskGroup, equipmentCode, palletType, taskOrigin, taskDestin, rank, 0, taskSource, remark));
            rank++;
        }

        int count = 0;
        try {
            for (Entity taskEntity : entityList) {
                count += TaskDB.createTask(taskEntity);
            }
        } catch (SQLException e) {
            RcsLog.consoleLog.error("任务创建失败: " + e.getMessage());
            JSONObject entries = BusinessCommand.taskCreate(500, "任务创建失败: " + e.getMessage(), null);
            NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
            return;
        }
        JSONObject entries;
        if (count > 0) {
            entries = BusinessCommand.taskCreate(200, "成功", remark);
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("任务创建成功", entries));
        } else {
            entries = BusinessCommand.taskCreate(500, "任务创建失败，无有效任务新增", null);
        }
        NettyServer.getServer(BusinessWebSocketHandler.getProtocol()).sendMessage(serverId, entries.toStringPretty());
    }

    /**
     * 添加任务
     *
     * @param taskGroup     任务组
     * @param equipmentCode 设备编号
     * @param palletType    托盘类型
     * @param taskOrigin    任务起点
     * @param taskDestin    任务终点
     * @param rank          顺序
     * @param finallyTask   是否是最终任务
     * @param taskSource    任务来源
     * @param remark        备注
     * @return 任务实体
     */
    private static Entity addTaskEntity(String taskGroup, String equipmentCode, Integer palletType, String taskOrigin, String taskDestin, int rank, int finallyTask, String taskSource, String remark) {
        Entity taskEntity = new Entity();
        taskEntity.set("task_group", taskGroup);
        if (equipmentCode != null && !equipmentCode.isEmpty() && !"0".equals(equipmentCode)) {
            taskEntity.set("equipment_code", equipmentCode);
        }
        if (palletType != null && palletType > 0) {
            taskEntity.set("pallet_type", palletType);
        }
        taskEntity.set("origin", taskOrigin);
        taskEntity.set("destin", taskDestin);
        taskEntity.set("task_rank", rank);
        taskEntity.set("finally_task", finallyTask);
        taskEntity.set("task_source", taskSource);
        if (remark != null && !remark.isEmpty()) {
            taskEntity.set("remark", remark);
        }
        return taskEntity;
    }
}
