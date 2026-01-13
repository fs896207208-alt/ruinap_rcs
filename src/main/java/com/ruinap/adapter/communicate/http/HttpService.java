package com.ruinap.adapter.communicate.http;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.slamopto.algorithm.strategy.PauseState;
import com.slamopto.algorithm.strategy.WaitStartState;
import com.slamopto.common.annotation.db.Transactional;
import com.slamopto.common.annotation.http.HttpLog;
import com.slamopto.common.annotation.http.HttpPost;
import com.slamopto.common.annotation.http.HttpURL;
import com.slamopto.common.http.AjaxResult;
import com.slamopto.db.DbCache;
import com.slamopto.db.database.*;
import com.slamopto.equipment.agv.RcsAgvCache;
import com.slamopto.equipment.agv.enums.AgvControlEnum;
import com.slamopto.equipment.agv.enums.AgvIsolationStateEnum;
import com.slamopto.equipment.domain.AgvTask;
import com.slamopto.equipment.domain.RcsAgv;
import com.slamopto.equipment.domain.RcsChargePile;
import com.slamopto.log.RcsLog;
import com.slamopto.map.RcsMapCache;
import com.slamopto.map.RcsPointCache;
import com.slamopto.map.domain.point.RcsPoint;
import com.slamopto.map.domain.point.RcsPointOccupy;
import com.slamopto.map.enums.PointOccupyTypeEnum;
import com.slamopto.task.TaskPathCache;
import com.slamopto.task.domain.RcsTask;
import com.slamopto.task.domain.TaskPath;
import com.slamopto.task.enums.TaskStateEnum;

import java.sql.SQLException;
import java.util.*;

/**
 * HTTP接口
 *
 * @author qianye
 * @create 2024-08-10 2:43
 */
@HttpURL("/openapi/")
public class HttpService {

    /**
     * JSON配置
     */
    static JSONConfig jsonConfig = JSONConfig.create().setIgnoreNullValue(false);


    /**
     * 统计信息查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("rcs/production/info")
    @HttpLog
    public JSONObject productionInfo(String jsonStr) throws RuntimeException, SQLException {
        if (StrUtil.isEmpty(jsonStr) || !JSONUtil.isTypeJSON(jsonStr)) {
            throw new RuntimeException("传入的参数不是json格式，" + jsonStr);
        }
        JSONObject jsonObject = new JSONObject(jsonStr);
        String startTime = jsonObject.getStr("startDate");
        if (StrUtil.hasEmpty(startTime)) {
            throw new RuntimeException("开始时间不能为空");
        }
        String endTime = jsonObject.getStr("endDate");
        if (StrUtil.hasEmpty(endTime)) {
            throw new RuntimeException("结束时间不能为空");
        }

        //传入的参数为yyyy-MM-dd数据，需要将数据转换
        Date startDateTime = DateUtil.parse(DateUtil.format(DateUtil.parse(startTime), "yyyy-MM-dd") + " 07:30:00");
        // 将endTime数据偏移到第二天并转换成 yyyy-MM-dd 07:29:59
        Date endDateTime = DateUtil.parse(DateUtil.format(DateUtil.offsetDay(DateUtil.parse(endTime), 1), "yyyy-MM-dd") + " 07:29:59");
        Condition beginWhere = new Condition("begin", ">=", startDateTime);
        Condition finishWhere = new Condition("finish", "<=", endDateTime);
        List<Entity> entities = StatisticsDB.queryStatisticsList(beginWhere, finishWhere);
        if (entities.isEmpty()) {
            return AjaxResult.error("传入的时间范围 " + startTime + "-" + endTime + " 查询不到数据");
        } else {
            JSONArray jsonArray = new JSONArray(jsonConfig);
            for (Entity entity : entities) {
                JSONObject reJson = new JSONObject(entity);
                reJson.set("date", DateUtil.format(DateUtil.parse(entity.getStr("begin")), "yyyy-MM-dd"));
                Date finish = entity.getDate("finish");
                if (finish != null && DateUtil.hour(finish, true) == 19) {
                    reJson.set("shift", "白班");
                } else {
                    // 否则（通常是次日 07:29:59）为晚班
                    reJson.set("shift", "晚班");
                }
                jsonArray.add(reJson);
            }

            jsonArray.sort((o1, o2) -> {
                JSONObject j1 = (JSONObject) o1;
                JSONObject j2 = (JSONObject) o2;

                // 修正1：使用 getStr 获取，因为之前 set 的是格式化后的 String
                Date date1 = j1.getDate("begin");
                Date date2 = j2.getDate("begin");

                // 排序逻辑1：日期从新到旧 (降序)，所以用 date2 对比 date1
                int dateCompare = date2.compareTo(date1);

                // 如果日期不相同，直接返回日期的比较结果
                if (dateCompare != 0) {
                    return dateCompare;
                }

//                // 修正2：shift 存的是 "白班"/"晚班" 字符串，不能用 getDate
//                String shift1 = j1.getStr("shift");
//                String shift2 = j2.getStr("shift");
//
//                // 排序逻辑2：日期相同时，白班在前，晚班在后
//                // 如果 shift1 是白班，它应该排在前面 (返回 -1)
//                if ("白班".equals(shift1) && "晚班".equals(shift2)) {
//                    return -1;
//                }
//                // 如果 shift1 是晚班，它应该排在后面 (返回 1)
//                if ("晚班".equals(shift1) && "白班".equals(shift2)) {
//                    return 1;
//                }

                // 如果班次相同（理论上同日期同班次不会重复，视业务而定），返回0
                return 0;
            });
            return AjaxResult.success(jsonArray);
        }
    }

    /**
     * 任务创建
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("task/create")
    @HttpLog
    @Transactional
    public JSONObject createTask(String jsonStr) throws RuntimeException, SQLException {
        if (StrUtil.isEmpty(jsonStr) || !JSONUtil.isTypeJSONArray(jsonStr)) {
            throw new RuntimeException("传入的参数不是json格式，" + jsonStr);
        }

        int count = 0;
        JSONArray jsonArray = new JSONArray(jsonStr);
        for (Object object : jsonArray) {
            JSONObject jsonObject = new JSONObject(object);

            // 任务组
            String taskGroup = jsonObject.getStr("task_group");
            // 任务编号
            String taskCode = jsonObject.getStr("task_code");
            // 设备编号
            String equipmentCode = jsonObject.getStr("equipment_code");
            // 托盘类型
            String palletType = jsonObject.getStr("pallet_type");
            // 起点（必填）
            String origin = jsonObject.getStr("origin");
            // 终点（必填）
            String destin = jsonObject.getStr("destin");
            // 优先级
            String taskPriority = jsonObject.getStr("task_priority");
            // 来源（必填）
            String taskSource = jsonObject.getStr("task_source");

            if (StrUtil.hasEmpty(origin)) {
                throw new RuntimeException("任务起点不能为空");
            }
            if (StrUtil.hasEmpty(destin)) {
                throw new RuntimeException("任务终点不能为空");
            }
            if (StrUtil.hasEmpty(taskSource)) {
                throw new RuntimeException("任务来源不能为空");
            }

            // 创建任务
            //查询是否存在预设线路
            List<Entity> entityList = RoutePresetDB.selectRoutePresetList(origin, destin);
            if (!entityList.isEmpty()) {
                RcsLog.consoleLog.info(RcsLog.formatTemplate("查询到线路预设数据，线路编号：" + entityList.getFirst().getStr("rp_code")));
                for (Entity entity : entityList) {
                    //创建任务
                    Entity taskEntity = new Entity();
                    taskEntity.set("task_group", taskGroup);
                    taskEntity.set("task_code", taskCode);
                    //任务类型 0搬运任务 1充电任务 2停靠任务 3避让任务 4临时任务
                    taskEntity.set("task_type", entity.getInt("rp_type"));
                    taskEntity.set("is_control", entity.getInt("rd_is_control"));
                    taskEntity.set("task_control", entity.getStr("rd_task_control"));
                    taskEntity.set("equipment_type", entity.getStr("rd_equipment_type"));
                    taskEntity.set("equipment_code", equipmentCode);
                    taskEntity.set("pallet_type", palletType);
                    taskEntity.set("origin_floor", entity.getStr("rd_origin_floor"));
                    taskEntity.set("origin_area", entity.getStr("rd_origin_area"));
                    taskEntity.set("origin", entity.getStr("rd_origin"));
                    taskEntity.set("destin_floor", entity.getStr("rd_destin_floor"));
                    taskEntity.set("destin_area", entity.getStr("rd_destin_area"));
                    taskEntity.set("destin", entity.getStr("rd_destin"));
                    taskEntity.set("task_rank", entity.getStr("rd_rank"));
                    taskEntity.set("task_priority", taskPriority);
                    taskEntity.set("executive_system", entity.getStr("rd_executive_system"));
                    taskEntity.set("finally_task", entity.getStr("rd_finally_task"));
                    taskEntity.set("task_source", taskSource);
                    taskEntity.set("remark", entity.getStr("rd_remark"));
                    count += TaskDB.createTask(taskEntity);
                }

            } else {
                RcsLog.consoleLog.warn(RcsLog.formatTemplate("查询不到到线路预设数据，将直接生成任务"));
                Entity entity = Entity.parse(jsonObject);

                count += TaskDB.createTask(entity);
            }
        }

        if (count > 0) {
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    /**
     * 任务取消
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("task/cancel")
    @HttpLog
    @Transactional
    public JSONObject cancelTask(String jsonStr) throws RuntimeException, SQLException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        int count = 0;
        // 任务组号
        String taskGroup = jsonObject.getStr("task_group");
        if (StrUtil.hasEmpty(taskGroup)) {
            throw new RuntimeException("任务组号不能为空");
        }
        // 任务编号
        String taskCode = jsonObject.getStr("task_code");

        Condition taskGroupCondition = new Condition("task_group", "=", taskGroup);
        Condition taskCodeCondition = null;
        if (!StrUtil.isEmpty(taskCode)) {
            taskCodeCondition = new Condition("task_code", "=", taskCode);
        }
        Condition taskStateWhere = new Condition("task_state", ">", 0);
        Condition interruptStateWhere = new Condition("interrupt_state", "=", 0);

        List<Entity> entities = null;
        if (!StrUtil.isEmpty(taskCode)) {
            entities = TaskDB.queryTaskList(taskGroupCondition, taskCodeCondition, taskStateWhere, interruptStateWhere);
        } else {
            entities = TaskDB.queryTaskList(taskGroupCondition, taskStateWhere, interruptStateWhere);
        }

        if (entities.isEmpty()) {
            throw new RuntimeException("【" + taskGroup + "-" + taskCode + "】查询不到任务数据");
        }

        for (Entity entitySql : entities) {
            String taskCode1 = entitySql.getStr("task_code");
            //从缓存中获取任务
            RcsTask rcsTask = DbCache.RCS_TASK_MAP.get(taskCode1);
            if (rcsTask != null) {
                Integer taskState = rcsTask.getTaskState();
                // 判断任务是否已取消
                if (taskState < TaskStateEnum.FINISH.code) {
                    throw new RuntimeException("任务编号【" + taskGroup + "-" + taskCode1 + "】已取消");
                }
                if (!TaskStateEnum.NEW.code.equals(taskState)) {
                    throw new RuntimeException("任务编号【" + taskGroup + "-" + taskCode1 + "】不是新任务，无法取消");
                }
                //更新缓存
                rcsTask.setTaskState(TaskStateEnum.HIGHER_CANCEL.code);
                count += 1;
            } else {
                Entity whereEntity = new Entity();
                whereEntity.set("task_group", taskGroup);
                whereEntity.set("task_code", taskCode1);

                Entity entity = new Entity();
                //中断状态 0默认 1中断任务 2取消任务 3上位取消
                entity.set("task_state", TaskStateEnum.HIGHER_CANCEL.code);
                entity.set("interrupt_state", TaskStateEnum.ACTION.code);

                count += TaskDB.updateTask(entity, whereEntity);
            }
        }

        if (count > 0) {
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    /**
     * 任务修改
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("task/update")
    @HttpLog
    public JSONObject updateTask(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 任务编号
        String taskCode = jsonObject.getStr("task_code");
        if (StrUtil.hasEmpty(taskCode)) {
            throw new RuntimeException("任务编号不能为空");
        }

        Integer taskState = jsonObject.getInt("task_state");
        Integer taskPriority = jsonObject.getInt("task_priority");

        //从缓存中获取任务
        RcsTask rcsTask = DbCache.RCS_TASK_MAP.get(taskCode);
        if (rcsTask == null) {
            throw new RuntimeException("任务编号【" + taskCode + "】查询不到数据");
        }

        Entity whereEntity = new Entity();
        whereEntity.set("task_code", taskCode);

        Entity entity = new Entity();
        entity.set("task_state", taskState);
        entity.set("task_priority", taskPriority);
        if (entity.isEmpty()) {
            throw new RuntimeException("任务编号【" + taskCode + "】需传入修改参数");
        }

        int count;
        try {
            count = TaskDB.updateTask(entity, whereEntity);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (count > 0) {
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    /**
     * 任务查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("task/select")
    @HttpLog
    public JSONObject selectTask(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 任务编号
        String taskCode = jsonObject.getStr("task_code");
        if (StrUtil.hasEmpty(taskCode)) {
            throw new RuntimeException("任务编号不能为空");
        }

        //从缓存中获取任务
        RcsTask rcsTask = DbCache.RCS_TASK_MAP.get(taskCode);
        if (rcsTask == null) {
            try {
                Entity taskEntity = TaskDB.queryTask(new Entity().set("task_code", taskCode));
                rcsTask = taskEntity.toBean(RcsTask.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (rcsTask == null) {
                throw new RuntimeException("任务编号【" + taskCode + "】查询不到数据");
            }
        }

        return AjaxResult.success(rcsTask);
    }

    /**
     * AGV车体状态查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("agv/select")
    @HttpLog
    public JSONObject selectAgv(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 任务编号
        String agvId = jsonObject.getStr("agv_id");
        if (StrUtil.hasEmpty(agvId)) {
            throw new RuntimeException("AGV编号不能为空");
        }

        //从缓存中获取任务
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv == null) {
            try {
                Entity entity = AgvDB.selectAgv(new Entity().set("agv_id", agvId));
                rcsAgv = entity.toBean(RcsAgv.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (rcsAgv == null) {
                throw new RuntimeException("AGV编号【" + agvId + "】查询不到数据");
            }
        }

        return AjaxResult.success(rcsAgv);
    }

    /**
     * AGV隔离
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("agv/isolation")
    @HttpLog
    public JSONObject isolationAgv(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // agv编号
        String agvId = jsonObject.getStr("agv_id");
        if (StrUtil.hasEmpty(agvId)) {
            throw new RuntimeException("AGV编号不能为空");
        }
        // 隔离状态
        String isolationState = jsonObject.getStr("isolation_state");
        if (StrUtil.hasEmpty(isolationState)) {
            throw new RuntimeException("隔离状态不能为空");
        }
        // 隔离状态枚举
        AgvIsolationStateEnum isolationState2 = AgvIsolationStateEnum.fromEnum(jsonObject.getInt("isolation_state"));
        if (AgvIsolationStateEnum.isEnumByCode(isolationState2, AgvIsolationStateEnum.NULL.code)) {
            throw new RuntimeException("未知的隔离状态");
        }

        //从缓存中获取任务
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv == null) {
            try {
                Entity entity = AgvDB.selectAgv(new Entity().set("agv_id", agvId));
                rcsAgv = entity.toBean(RcsAgv.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (rcsAgv == null) {
                throw new RuntimeException("AGV编号【" + agvId + "】查询不到数据");
            }
        }

        //隔离状态更新到数据库
        Integer count = AgvDB.updateAgvIsolationState(rcsAgv.getAgvId(), jsonObject.getInt("isolation_state"));
        if (count > 0) {
            rcsAgv.setIsolationState(jsonObject.getInt("isolation_state"));
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    /**
     * AGV控制权管理
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("agv/control")
    @HttpLog
    public JSONObject controlAgv(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // agv编号
        String agvId = jsonObject.getStr("agv_id");
        if (StrUtil.hasEmpty(agvId)) {
            throw new RuntimeException("AGV编号不能为空");
        }
        // AGV控制权
        String agvControlStr = jsonObject.getStr("agv_control");
        if (StrUtil.hasEmpty(agvControlStr)) {
            throw new RuntimeException("AGV控制权不能为空");
        }
        // AGV控制权枚举
        if (!AgvControlEnum.isEnumByCode(AgvControlEnum.NULL, jsonObject.getInt("agv_control"))) {
            throw new RuntimeException("未知的AGV控制权");
        }

        //从缓存中获取任务
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv == null) {
            try {
                Entity entity = AgvDB.selectAgv(new Entity().set("agv_id", agvId));
                rcsAgv = entity.toBean(RcsAgv.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (rcsAgv == null) {
                throw new RuntimeException("AGV编号【" + agvId + "】查询不到数据");
            }
        }

        //AGV控制权更新到数据库
        Integer count = AgvDB.updateAgvControl(rcsAgv.getAgvId(), jsonObject.getInt("agv_control"));
        if (count > 0) {
            rcsAgv.setAgvControl(jsonObject.getInt("agv_control"));
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    /**
     * AGV充电
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("agv/charge")
    @HttpLog
    public JSONObject chargeAgv(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // agv编号
        String agvId = jsonObject.getStr("agv_id");
        if (StrUtil.hasEmpty(agvId)) {
            throw new RuntimeException("AGV编号不能为空");
        }
        // 充电信号 0结束充电 1高优先级信号 2低优先级信号
        String chargeSignal = jsonObject.getStr("charge_signal");
        if (StrUtil.hasEmpty(chargeSignal)) {
            throw new RuntimeException("充电信号不能为空");
        }
        // 充电信号
        int chargeSignal2 = jsonObject.getInt("charge_signal");
        if (chargeSignal2 < 0 || chargeSignal2 > 1) {
            throw new RuntimeException("未知的充电信号");
        }

        //从缓存中获取任务
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv == null) {
            try {
                Entity entity = AgvDB.selectAgv(new Entity().set("agv_id", agvId));
                rcsAgv = entity.toBean(RcsAgv.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (rcsAgv == null) {
                throw new RuntimeException("AGV编号【" + agvId + "】查询不到数据");
            }
        }

        //充电信号更新到数据库
        Integer count = AgvDB.updateAgvChargeSignal(rcsAgv, jsonObject.getInt("charge_signal"));
        if (count > 0) {
            rcsAgv.setIsolationState(jsonObject.getInt("charge_signal"));
            return AjaxResult.success();
        } else {
            return AjaxResult.error();
        }
    }

    /**
     * AGV暂停
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("agv/pause")
    @HttpLog
    public JSONObject pauseAgv(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // agv编号
        String agvId = jsonObject.getStr("agv_id");
        if (StrUtil.hasEmpty(agvId)) {
            throw new RuntimeException("AGV编号不能为空");
        }
        // 暂停信号
        String agvState = jsonObject.getStr("agv_state");
        if (StrUtil.hasEmpty(agvState)) {
            throw new RuntimeException("暂停信号不能为空");
        }
        // 暂停信号
        int agvState2 = jsonObject.getInt("agv_state");
        if (agvState2 < 0 || agvState2 > 1) {
            throw new RuntimeException("未知的暂停信号");
        }

        //从缓存中获取任务
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv == null) {
            try {
                Entity entity = AgvDB.selectAgv(new Entity().set("agv_id", agvId));
                rcsAgv = entity.toBean(RcsAgv.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (rcsAgv == null) {
                throw new RuntimeException("AGV编号【" + agvId + "】查询不到数据");
            }
        }

        // 获取AGV任务信息
        AgvTask agvTask = RcsAgvCache.getAGV_TASK_CACHE().getOrDefault(agvId, null);
        if (agvTask != null) {
            RcsPoint currentPlanOrigin = RcsPointCache.getRcsPoint(rcsAgv.getMapId(), agvTask.getTaskStartId());
            RcsPoint currentPlanDestin = RcsPointCache.getRcsPoint(rcsAgv.getMapId(), agvTask.getTaskEndId());

            String taskId = agvTask.getTaskId();
            String[] split = taskId.split("-");
            if (split.length < 2) {
                String message = "AGV[" + agvId + "]车体没有任务号，直发指令失败";
                throw new RuntimeException(message);
            }

            // 创建新的任务路径
            TaskPath taskPath = new TaskPath();
            taskPath.setAgvId(agvId);
            taskPath.setTaskCode(split[0]);
            taskPath.setSubTaskNo(Integer.parseInt(split[1]));
            taskPath.setPathCode(1);
            taskPath.setCurrentPlanOrigin(currentPlanOrigin);
            taskPath.setCurrentPlanDestin(currentPlanDestin);

            if (agvState2 == 1) {
                //发送暂停指令
                String command = PauseState.sendCommand(taskPath);
                if (command != null && !command.isEmpty()) {
                    // 直发指令成功发送，直接返回
                    String message = "向AGV[" + agvId + "]发送暂停指令成功：" + command;
                    return AjaxResult.success(message);
                } else {
                    String message = "向AGV[" + agvId + "]发送暂停指令失败";
                    throw new RuntimeException(message);
                }
            } else {
                //发送暂停指令
                String command = WaitStartState.sendCommand(taskPath);
                if (command != null && !command.isEmpty()) {
                    String message = "向AGV[" + agvId + "]发送恢复指令成功：" + command;
                    return AjaxResult.success(message);
                } else {
                    String message = "向AGV[" + agvId + "]发送恢复指令失败";
                    throw new RuntimeException(message);
                }
            }
        } else {
            String message = "AGV[" + agvId + "]车体没有任务，无法发送暂停/恢复指令";
            throw new RuntimeException(message);
        }
    }

    /**
     * AGV任务路径查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("agv/taskpath/select")
    @HttpLog
    public JSONObject selectAgvTaskPath(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // agv编号
        String agvId = jsonObject.getStr("agv_id");
        if (StrUtil.hasEmpty(agvId)) {
            throw new RuntimeException("AGV编号不能为空");
        }

        //获取任务路径
        TaskPath taskPath = TaskPathCache.getFirst(agvId);
        if (taskPath == null) {
            throw new RuntimeException("AGV没有任务路径数据");
        }
        return AjaxResult.success(taskPath);
    }

    /**
     * 点位占用查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/select")
    @HttpLog
    public JSONObject selectPoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 点位编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("点位编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);

        //获取点位
        RcsPoint rcsPoint = RcsPointCache.getRcsPoint(mapId, pointId);
        if (rcsPoint == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }

        return AjaxResult.success(rcsPoint.getOccupy());
    }

    /**
     * 点位占用
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/occupy")
    @HttpLog
    public JSONObject occupyPoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 点位编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("点位编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);
        String occupyCode = jsonObject.getStr("occupy_code", "HTTP");

        //获取点位
        RcsPoint rcsPoint = RcsPointCache.getRcsPoint(mapId, pointId);
        if (rcsPoint == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }

        RcsPointOccupy occupy = rcsPoint.getOccupy();
        if (occupy.getPathOccupy()) {
            throw new RuntimeException("点位编号编号【" + pointId + "】已经被占用");
        }

        //设置占用
        occupy.addOccupyType(rcsPoint, PointOccupyTypeEnum.MANUAL, occupyCode);
        return AjaxResult.success();
    }

    /**
     * 点位占用移除
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/remove")
    @HttpLog
    public JSONObject removePoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 点位编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("点位编号编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);
        String occupyCode = jsonObject.getStr("occupy_code", "HTTP");


        //获取点位
        RcsPoint rcsPoint = RcsPointCache.getRcsPoint(mapId, pointId);
        if (rcsPoint == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }

        RcsPointOccupy occupy = rcsPoint.getOccupy();
        if (!occupy.getPathOccupy()) {
            throw new RuntimeException("点位编号编号【" + pointId + "】未被占用");
        }

        //移除占用
        occupy.removeOccupyType(rcsPoint, occupyCode, PointOccupyTypeEnum.MANUAL);
        return AjaxResult.success();
    }

    /**
     * 管制区占用查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlarea/select")
    @HttpLog
    public JSONObject selectControlArea(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制区编号
        String code = jsonObject.getStr("code");
        if (StrUtil.hasEmpty(code)) {
            throw new RuntimeException("管制区编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);

        // 获取管制区数据
        Map<String, List<RcsPoint>> controlAreaMap = RcsMapCache.getControlAreaMap(mapId);
        if (controlAreaMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        List<RcsPoint> rcsPoints = controlAreaMap.get(code);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("管制区编号【" + code + "】查询不到数据");
        }

        RcsPoint rcsPoint = rcsPoints.getFirst();
        RcsPointOccupy occupy = rcsPoint.getOccupy();

        return AjaxResult.success(occupy.getPathOccupy());
    }

    /**
     * 管制区占用
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlarea/occupy")
    @HttpLog
    public JSONObject occupyControlArea(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制区编号
        String code = jsonObject.getStr("code");
        if (StrUtil.hasEmpty(code)) {
            throw new RuntimeException("管制区编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);
        String occupyCode = jsonObject.getStr("occupy_code", "HTTP");

        // 获取管制区数据
        Map<String, List<RcsPoint>> controlAreaMap = RcsMapCache.getControlAreaMap(mapId);
        if (controlAreaMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        List<RcsPoint> rcsPoints = controlAreaMap.get(code);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("管制区编号【" + code + "】查询不到数据");
        }

        RcsPoint rcsPoint = rcsPoints.getFirst();
        RcsPointOccupy occupy = rcsPoint.getOccupy();
        if (occupy.getPathOccupy()) {
            throw new RuntimeException("管制区编号【" + code + "】已经被占用");
        }

        //设置占用
        RcsPointOccupy.addOccupyType(rcsPoints, PointOccupyTypeEnum.MANUAL, occupyCode);
        return AjaxResult.success();
    }

    /**
     * 管制区占用移除
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlarea/remove")
    @HttpLog
    public JSONObject removeControlArea(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制区编号
        String code = jsonObject.getStr("code");
        if (StrUtil.hasEmpty(code)) {
            throw new RuntimeException("管制区编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);
        String occupyCode = jsonObject.getStr("occupy_code", "HTTP");

        // 获取管制区数据
        Map<String, List<RcsPoint>> controlAreaMap = RcsMapCache.getControlAreaMap(mapId);
        if (controlAreaMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        List<RcsPoint> rcsPoints = controlAreaMap.get(code);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("管制区编号【" + code + "】查询不到数据");
        }

        RcsPoint rcsPoint = rcsPoints.getFirst();
        RcsPointOccupy occupy = rcsPoint.getOccupy();
        if (!occupy.getPathOccupy()) {
            throw new RuntimeException("管制区编号【" + code + "】未被占用");
        }

        //移除占用
        RcsPointOccupy.removeOccupyType(rcsPoints, occupyCode, PointOccupyTypeEnum.MANUAL);
        return AjaxResult.success();
    }

    /**
     * 管制区占用设备查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlarea/equipment")
    @HttpLog
    public JSONObject equipmentControlArea(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制区编号
        String code = jsonObject.getStr("code");
        if (StrUtil.hasEmpty(code)) {
            throw new RuntimeException("管制区编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);

        // 获取管制区数据
        Map<String, List<RcsPoint>> controlAreaMap = RcsMapCache.getControlAreaMap(mapId);
        if (controlAreaMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        List<RcsPoint> rcsPoints = controlAreaMap.get(code);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("管制区编号【" + code + "】查询不到数据");
        }

        Set<String> equipmentSet = new HashSet<>();
        for (RcsPoint rcsPoint : rcsPoints) {
            RcsPointOccupy occupy = rcsPoint.getOccupy();
            boolean pathOccupy = occupy.getPathOccupy();
            if (!pathOccupy) {
                break;
            }
            List<String> occupyEquipments = occupy.getOccupyEquipments();
            equipmentSet.addAll(occupyEquipments);
        }

        return AjaxResult.success(equipmentSet);
    }

    /**
     * 管制点占用查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlpoint/select")
    @HttpLog
    public JSONObject selectControlPoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制点编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("管制点编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);

        // 获取管制点数据
        Map<RcsPoint, List<RcsPoint>> controlPointMap = RcsMapCache.getControlPointMap(mapId);
        if (controlPointMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        //获取点位
        RcsPoint point = RcsPointCache.getRcsPoint(mapId, pointId);
        if (point == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }
        List<RcsPoint> rcsPoints = controlPointMap.get(point);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("点位编号【" + pointId + "】查询不到管制点数据");
        }

        RcsPoint rcsPoint = rcsPoints.getFirst();
        RcsPointOccupy occupy = rcsPoint.getOccupy();

        return AjaxResult.success(occupy.getPathOccupy());
    }

    /**
     * 管制点占用
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlpoint/occupy")
    @HttpLog
    public JSONObject occupyControlPoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制点编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("管制点编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);
        String occupyCode = jsonObject.getStr("occupy_code", "HTTP");

        // 获取管制点数据
        Map<RcsPoint, List<RcsPoint>> controlPointMap = RcsMapCache.getControlPointMap(mapId);
        if (controlPointMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        //获取点位
        RcsPoint point = RcsPointCache.getRcsPoint(mapId, pointId);
        if (point == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }
        List<RcsPoint> rcsPoints = controlPointMap.get(point);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("点位编号【" + pointId + "】查询不到管制点数据");
        }

        RcsPoint rcsPoint = rcsPoints.getFirst();
        RcsPointOccupy occupy = rcsPoint.getOccupy();
        if (occupy.getPathOccupy()) {
            throw new RuntimeException("管制点编号【" + pointId + "】已经被占用");
        }

        //设置占用
        RcsPointOccupy.addOccupyType(rcsPoints, PointOccupyTypeEnum.MANUAL, occupyCode);
        return AjaxResult.success();
    }

    /**
     * 管制点占用移除
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlpoint/remove")
    @HttpLog
    public JSONObject removeControlPoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制点编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("管制点编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);
        String occupyCode = jsonObject.getStr("occupy_code", "HTTP");

        // 获取管制点数据
        Map<RcsPoint, List<RcsPoint>> controlPointMap = RcsMapCache.getControlPointMap(mapId);
        if (controlPointMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        //获取点位
        RcsPoint point = RcsPointCache.getRcsPoint(mapId, pointId);
        if (point == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }
        List<RcsPoint> rcsPoints = controlPointMap.get(point);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("点位编号【" + pointId + "】查询不到管制点数据");
        }

        RcsPoint rcsPoint = rcsPoints.getFirst();
        RcsPointOccupy occupy = rcsPoint.getOccupy();
        if (!occupy.getPathOccupy()) {
            throw new RuntimeException("管制点编号【" + pointId + "】未被占用");
        }

        //移除占用
        RcsPointOccupy.removeOccupyType(rcsPoints, occupyCode, PointOccupyTypeEnum.MANUAL);
        return AjaxResult.success();
    }

    /**
     * 管制点占用设备查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("point/controlpoint/equipment")
    @HttpLog
    public JSONObject equipmentControlPoint(String jsonStr) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 管制点编号
        Integer pointId = jsonObject.getInt("point_id");
        if (pointId == null) {
            throw new RuntimeException("管制点编号不能为空");
        }
        // 地图编号
        Integer mapId = jsonObject.getInt("map_id", 1);

        // 获取管制点数据
        Map<RcsPoint, List<RcsPoint>> controlPointMap = RcsMapCache.getControlPointMap(mapId);
        if (controlPointMap.isEmpty()) {
            throw new RuntimeException("地图编号【" + mapId + "】查询不到数据");
        }
        //获取点位
        RcsPoint point = RcsPointCache.getRcsPoint(mapId, pointId);
        if (point == null) {
            throw new RuntimeException("地图编号【" + mapId + "】点位编号【" + pointId + "】查询不到点位数据");
        }
        List<RcsPoint> rcsPoints = controlPointMap.get(point);
        if (rcsPoints == null || rcsPoints.isEmpty()) {
            throw new RuntimeException("点位编号【" + pointId + "】查询不到管制点数据");
        }

        Set<String> equipmentSet = new HashSet<>();
        for (RcsPoint rcsPoint : rcsPoints) {
            RcsPointOccupy occupy = rcsPoint.getOccupy();
            boolean pathOccupy = occupy.getPathOccupy();
            if (!pathOccupy) {
                break;
            }
            List<String> occupyEquipments = occupy.getOccupyEquipments();
            equipmentSet.addAll(occupyEquipments);
        }

        return AjaxResult.success(equipmentSet);
    }

    /**
     * 充电桩查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("chargePile/select")
    @HttpLog
    public JSONObject selectChargePile(String jsonStr) throws RuntimeException {
        JSONObject jsonObject;
        try {
            jsonObject = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("传入的参数不是json格式，" + e.getMessage());
        }

        // 充电桩编号
        String code = jsonObject.getStr("code");
        if (StrUtil.hasEmpty(code)) {
            throw new RuntimeException("充电桩编号不能为空");
        }

        RcsChargePile chargePile = DbCache.RCS_CHARGE_MAP.get(code);
        if (chargePile == null) {
            try {
                Entity entity = ChargePileDB.selectChargePile(new Entity().set("agv_id", code));
                chargePile = entity.toBean(RcsChargePile.class);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (chargePile == null) {
                throw new RuntimeException("充电桩编号【" + code + "】查询不到数据");
            }
        }

        return AjaxResult.success(chargePile);
    }

    /**
     * 告警查询
     *
     * @param jsonStr 传入参数
     * @return 结果
     */
    @HttpPost("alarm/select")
    @HttpLog
    public JSONObject selectAlarm(String jsonStr) throws RuntimeException {
        // 获取今天 0:00:00 的时间
        DateTime startTime = DateUtil.beginOfDay(DateUtil.date());
        // 获取今天 23:59:59 的时间
        DateTime endTime = DateUtil.endOfDay(DateUtil.date());
        try {
            List<Entity> entityList = AlarmDB.queryAlarmList(startTime, endTime);
            return AjaxResult.success(new JSONArray(entityList, jsonConfig));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 开始工作
     *
     * @return 结果
     */
    @HttpPost("work/start")
    @HttpLog
    public JSONObject startWork() throws RuntimeException, SQLException {
        if (DbCache.WORK_STATE == 1 || DbCache.WORK_STATE == 2) {
            return AjaxResult.error("请勿重复操作，调度系统工作状态为已开始");
        }
        DbCache.WORK_STATE = 1;
        //工作状态 0结束工作 1正在工作
        ConfigDB.updateConfigValue("rcs.work.state", "1");
        return AjaxResult.success();
    }

    /**
     * 结束工作
     *
     * @return 结果
     */
    @HttpPost("work/end")
    @HttpLog
    public JSONObject endWork() throws RuntimeException, SQLException {
        if (DbCache.WORK_STATE == 0 || DbCache.WORK_STATE == 3) {
            return AjaxResult.error("请勿重复操作，调度系统工作状态为已结束");
        }
        DbCache.WORK_STATE = 0;
        //工作状态 0结束工作 1正在工作
        ConfigDB.updateConfigValue("rcs.work.state", "0");
        return AjaxResult.success();
    }
}
