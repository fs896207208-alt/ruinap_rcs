package com.ruinap.adapter.communicate.event;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.slamopto.communicate.base.ClientAttribute;
import com.slamopto.communicate.base.event.AbstractClientEvent;
import com.slamopto.db.business.AlarmManage;
import com.slamopto.db.enums.AlarmCodeEnum;
import com.slamopto.equipment.agv.RcsAgvCache;
import com.slamopto.equipment.domain.AgvTask;
import com.slamopto.equipment.domain.RcsAgv;
import com.slamopto.log.RcsLog;
import com.slamopto.map.RcsPointCache;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.concurrent.CompletableFuture;

/**
 * AGV事件处理器
 *
 * @author qianye
 * @create 2025-02-25 19:26
 */
public class SlamoptoAgvWebSocketClientEvent extends AbstractClientEvent<TextWebSocketFrame> {

    /**
     * 接收消息
     *
     * @param attribute 属性
     * @param frame     消息帧
     */
    @Override
    public void receiveMessage(ClientAttribute attribute, TextWebSocketFrame frame) {
        // 检查文本帧是否是null
        if (frame != null) {
            String pact = attribute.getProtocol().getProtocol();
            String equipmentType = attribute.getEquipmentType().getEquipmentType();
            String clientId = attribute.getClientId();
            String message = frame.text();
            //判断是否是json格式
            boolean typeJsonObj = JSONUtil.isTypeJSONObject(message);
            if (typeJsonObj) {
                JSONObject jsonObject = JSONUtil.parseObj(message);
                String event = jsonObject.getStr("Event");
                // 根据客户端 ID和数据戳 找到对应的 CompletableFuture 并完成它
                String lookupKey = StrUtil.format("{}_{}_{}", equipmentType, clientId, jsonObject.getInt("DataStamps", -1));
                CompletableFuture<String> future = attribute.removeFuture(lookupKey, String.class);
                if (future != null) {
                    future.complete(message);
                }

                //记录日志
                RcsLog.communicateLog.info(RcsLog.formatTemplateRandom(equipmentType + "_" + clientId, "rec_data", jsonObject.getInt("DataStamps"), jsonObject));
                //事件分支
                switch (event.toLowerCase()) {
                    case "connect_success":
                        connectSuccess(pact, equipmentType, clientId, jsonObject.getJSONObject("Body"));
                        break;
                    case "agv_map":
                        agvMap(pact, equipmentType, clientId, jsonObject.getJSONObject("Body"));
                        break;
                    case "dispatch_state":
                        dispatchAgvState(pact, equipmentType, clientId, jsonObject.getJSONObject("Body"));
                        break;
                    case "agv_state":
                        agvState(pact, equipmentType, clientId, jsonObject.getJSONObject("Body"));
                        break;
                    case "agv_task":
                        agvTask(pact, equipmentType, clientId, jsonObject.getJSONObject("Body"));
                        break;
                    default:
                        break;
                }
            } else {
                //记录日志
                RcsLog.communicateLog.error(RcsLog.formatTemplateRandom(equipmentType + "_" + clientId, "rec_data", -1, "AgvEvent解析异常，非json格式数据:" + message));
            }
        }

    }

    /**
     * 请求AGV状态事件
     *
     * @param body 数据
     */
    private static void dispatchAgvState(String pact, String equipmentType, String clientId, JSONObject body) {
        String agvId = body.getByPath("AGVSTATE.agv_id", String.class);
        String agvName = body.getByPath("AGVSTATE.agv_name", String.class);
        Integer controlMode = body.getByPath("AGVSTATE.control_mode", Integer.class);
        Integer agvType = body.getByPath("AGVSTATE.agv_type", Integer.class);
        Integer mapId = body.getByPath("AGVSTATE.map_id", Integer.class);
        Integer slamX = body.getByPath("AGVSTATE.slam_x", Integer.class);
        Integer slamY = body.getByPath("AGVSTATE.slam_y", Integer.class);
        Integer slamAngle = body.getByPath("AGVSTATE.slam_angle", Integer.class);
        Integer slamCov = body.getByPath("AGVSTATE.slam_cov", Integer.class);
        Integer battery = body.getByPath("AGVSTATE.battery", Integer.class);
        Integer vX = body.getByPath("AGVSTATE.v_x", Integer.class);
        Integer vY = body.getByPath("AGVSTATE.v_y", Integer.class);
        Integer vAngle = body.getByPath("AGVSTATE.v_angle", Integer.class);
        Integer agvMode = body.getByPath("AGVSTATE.agv_mode", Integer.class);
        Integer agvState = body.getByPath("AGVSTATE.agv_state", Integer.class);
        Integer pointId = body.getByPath("AGVSTATE.point_id", Integer.class);
        Integer goalPoint = body.getByPath("AGVSTATE.goal_point", Integer.class);
        Integer goodsState = body.getByPath("AGVSTATE.goods_state", Integer.class);
        Integer estopState = body.getByPath("AGVSTATE.estop_state", Integer.class);
        Integer moveDir = body.getByPath("AGVSTATE.move_dir", Integer.class);
        Integer frontArea = body.getByPath("AGVSTATE.front_area", Integer.class);
        Integer frontLeft = body.getByPath("AGVSTATE.front_left", Integer.class);
        Integer frontRight = body.getByPath("AGVSTATE.front_right", Integer.class);
        Integer backArea = body.getByPath("AGVSTATE.back_area", Integer.class);
        Integer backLeft = body.getByPath("AGVSTATE.back_left", Integer.class);
        Integer backRight = body.getByPath("AGVSTATE.back_right", Integer.class);
        Integer bumpFront = body.getByPath("AGVSTATE.bump_front", Integer.class);
        Integer bumpBack = body.getByPath("AGVSTATE.bump_back", Integer.class);
        Integer bumpLeft = body.getByPath("AGVSTATE.bump_left", Integer.class);
        Integer bumpRight = body.getByPath("AGVSTATE.bump_right", Integer.class);
        Integer runTime = body.getByPath("AGVSTATE.run_time", Integer.class);
        Integer runLength = body.getByPath("AGVSTATE.run_length", Integer.class);
        Integer light = body.getByPath("AGVSTATE.light", Integer.class);
        String agvErrMsg = body.getByPath("AGVSTATE.AGV_Err_Msg", String.class);
        Integer palletState = body.getByPath("AGVSTATE.pallet_state", Integer.class);
        Integer liftHeight = body.getByPath("AGVSTATE.lift_height", Integer.class);
        Integer alarmSignal = body.getByPath("AGVSTATE.alarm_signal", Integer.class);

        Integer taskState = body.getByPath("TASKSTATE.task_state", Integer.class);
        Integer taskStart = body.getByPath("TASKSTATE.task_start", Integer.class);
        Integer interruptState = body.getByPath("TASKSTATE.interrupt_state", Integer.class);
        String taskId = body.getByPath("TASKSTATE.task_id", String.class);
        Integer taskStartId = body.getByPath("TASKSTATE.task_start_id", Integer.class);
        Integer taskEndId = body.getByPath("TASKSTATE.task_end_id", Integer.class);
        Integer taskAct = body.getByPath("TASKSTATE.task_act", Integer.class);
        Integer taskParam = body.getByPath("TASKSTATE.task_param", Integer.class);
        Integer setQua = body.getByPath("TASKSTATE.set_qua", Integer.class);
        Integer setId = body.getByPath("TASKSTATE.set_id", Integer.class);
        Integer setState = body.getByPath("TASKSTATE.set_state", Integer.class);
        Integer setStartId = body.getByPath("TASKSTATE.set_start_id", Integer.class);
        Integer setEndId = body.getByPath("TASKSTATE.set_end_id", Integer.class);
        Integer pathQua = body.getByPath("TASKSTATE.path_qua", Integer.class);
        Integer pathStartId = body.getByPath("TASKSTATE.path_start_id", Integer.class);
        Integer pathEndId = body.getByPath("TASKSTATE.path_end_id", Integer.class);
        String taskDescription = body.getByPath("TASKSTATE.task_description", String.class);
        String taskErrMsg = body.getByPath("TASKSTATE.TASK_Err_Msg", String.class);

        //获取根据指定编号获取AGV对象
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv != null) {
            //将数据更新到缓存中
            rcsAgv.setAgvName(agvName);
            rcsAgv.setAgvControl(controlMode);
            rcsAgv.setAgvType(agvType);
            rcsAgv.setMapId(mapId);
            rcsAgv.setSlamX(slamX);
            rcsAgv.setSlamY(slamY);
            rcsAgv.setSlamAngle(slamAngle);
            rcsAgv.setSlamCov(slamCov);
            rcsAgv.setBattery(battery);
            rcsAgv.setVX(vX);
            rcsAgv.setVY(vY);
            rcsAgv.setVAngle(vAngle);
            rcsAgv.setAgvMode(agvMode);
            rcsAgv.setAgvState(agvState);
            rcsAgv.setPointId(pointId);
            rcsAgv.setGoalPoint(goalPoint);
            rcsAgv.setTaskId(taskId);
            rcsAgv.setTaskState(taskState);
            rcsAgv.setTaskAct(taskAct);
            rcsAgv.setTaskParam(taskParam);
            rcsAgv.setTaskDescription(taskDescription);
            rcsAgv.setGoodsState(goodsState);
            rcsAgv.setEstopState(estopState);
            rcsAgv.setMoveDir(moveDir);
            rcsAgv.setFrontArea(frontArea);
            rcsAgv.setFrontLeft(frontLeft);
            rcsAgv.setFrontRight(frontRight);
            rcsAgv.setBackArea(backArea);
            rcsAgv.setBackLeft(backLeft);
            rcsAgv.setBackRight(backRight);
            rcsAgv.setBumpFront(bumpFront);
            rcsAgv.setBumpBack(bumpBack);
            rcsAgv.setBumpLeft(bumpLeft);
            rcsAgv.setBumpRight(bumpRight);
            rcsAgv.setRunTime(runTime);
            rcsAgv.setRunLength(runLength);
            rcsAgv.setLight(light);
            rcsAgv.setAgvErrMsg(agvErrMsg);
            rcsAgv.setPalletState(palletState);
            rcsAgv.setLiftHeight(liftHeight);
            rcsAgv.setAlarmSignal(alarmSignal);
        }

        RcsAgvCache.getAGV_TASK_CACHE().compute(clientId, (key, oldTask) -> {
            // 获取原有的任务信息，如果不存在则创建新的任务对象
            AgvTask agvTask = (oldTask != null) ? oldTask : new AgvTask();
            agvTask.setTaskState(taskState);
            agvTask.setTaskStart(taskStart);
            agvTask.setInterruptState(interruptState);
            agvTask.setTaskId(taskId);
            agvTask.setTaskStartId(taskStartId);
            agvTask.setTaskEndId(taskEndId);
            agvTask.setTaskAct(taskAct);
            agvTask.setTaskParam(taskParam);
            agvTask.setSetQua(setQua);
            agvTask.setSetId(setState);
            agvTask.setSetState(setId);
            agvTask.setSetStartId(setStartId);
            agvTask.setSetEndId(setEndId);
            agvTask.setPathQua(pathQua);
            agvTask.setPathStartId(pathStartId);
            agvTask.setPathEndId(pathEndId);
            agvTask.setTaskDescription(taskDescription);
            agvTask.setTaskErrMsg(taskErrMsg);
            return agvTask;
        });
    }

    /**
     * 请求AGV任务事件
     *
     * @param pact          协议
     * @param equipmentType 设备类型
     * @param clientId      设备ID
     * @param entriesBody   数据
     */
    private void agvTask(String pact, String equipmentType, String clientId, JSONObject entriesBody) {
        Integer taskState = entriesBody.getByPath("task_state.value", Integer.class);
        String taskId = entriesBody.getByPath("task_id.value", String.class);
        Integer taskStartId = entriesBody.getByPath("task_start_id.value", Integer.class);
        Integer taskEndId = entriesBody.getByPath("task_end_id.value", Integer.class);
        Integer pathId = entriesBody.getByPath("path_id.value", Integer.class);
        Integer pathStartId = entriesBody.getByPath("path_start_id.value", Integer.class);
        Integer pathEndId = entriesBody.getByPath("path_end_id.value", Integer.class);
        Integer pathState = entriesBody.getByPath("path_state.value", Integer.class);
        Integer pathQua = entriesBody.getByPath("path_qua.value", Integer.class);
        String taskErrMsg = entriesBody.getByPath("TASK_Err_Msg.value", String.class);

        RcsAgvCache.getAGV_TASK_CACHE().compute(clientId, (key, oldTask) -> {
            // 获取原有的任务信息，如果不存在则创建新的任务对象
            AgvTask agvTask = (oldTask != null) ? oldTask : new AgvTask();
            agvTask.setTaskState(taskState);
            agvTask.setTaskId(taskId);
            agvTask.setTaskStartId(taskStartId);
            agvTask.setTaskEndId(taskEndId);
//            agvTask.setPathId(pathId);
            agvTask.setPathStartId(pathStartId);
            agvTask.setPathEndId(pathEndId);
//            agvTask.setPathState(pathState);
            agvTask.setPathQua(pathQua);
            agvTask.setTaskErrMsg(taskErrMsg);
            return agvTask;
        });
    }

    /**
     * 请求AGV状态事件
     *
     * @param body 数据
     */
    private static void agvState(String pact, String equipmentType, String clientId, JSONObject body) {
        String agvId = body.getByPath("AGVInfo.agv_id.value", String.class);
        String agvName = body.getByPath("AGVInfo.agv_name.value", String.class);
        Integer agvType = body.getByPath("AGVInfo.agv_type.value", Integer.class);
        Integer mapId = body.getByPath("AGVInfo.map_id.value", Integer.class);
        Integer slamX = body.getByPath("AGVInfo.slam_x.value", Integer.class);
        Integer slamY = body.getByPath("AGVInfo.slam_y.value", Integer.class);
        Integer slamAngle = body.getByPath("AGVInfo.slam_angle.value", Integer.class);
        Integer slamCov = body.getByPath("AGVInfo.slam_cov.value", Integer.class);
        Integer battery = body.getByPath("AGVInfo.battery.value", Integer.class);
        Integer vX = body.getByPath("AGVInfo.v_x.value", Integer.class);
        Integer vY = body.getByPath("AGVInfo.v_y.value", Integer.class);
        Integer vAngle = body.getByPath("AGVInfo.v_angle.value", Integer.class);
        Integer agvMode = body.getByPath("AGVInfo.agv_mode.value", Integer.class);
        Integer agvState = body.getByPath("AGVInfo.agv_state.value", Integer.class);
        Integer pointId = body.getByPath("AGVInfo.point_id.value", Integer.class);
        Integer goalPoint = body.getByPath("AGVInfo.goal_point.value", Integer.class);
        String taskId = body.getByPath("AGVInfo.task_id.value", String.class);
        Integer taskState = body.getByPath("AGVInfo.task_state.value", Integer.class);
        Integer taskAct = body.getByPath("AGVInfo.task_act.value", Integer.class);
        Integer taskParam = body.getByPath("AGVInfo.task_param.value", Integer.class);
        String taskDescription = body.getByPath("AGVInfo.task_description.value", String.class);
        Integer goodsState = body.getByPath("AGVInfo.goods_state.value", Integer.class);
        Integer estopState = body.getByPath("AGVInfo.estop_state.value", Integer.class);
        Integer moveDir = body.getByPath("AGVInfo.move_dir.value", Integer.class);
        Integer frontArea = body.getByPath("AGVInfo.front_area.value", Integer.class);
        Integer frontLeft = body.getByPath("AGVInfo.front_left.value", Integer.class);
        Integer frontRight = body.getByPath("AGVInfo.front_right.value", Integer.class);
        Integer backArea = body.getByPath("AGVInfo.back_area.value", Integer.class);
        Integer backLeft = body.getByPath("AGVInfo.back_left.value", Integer.class);
        Integer backRight = body.getByPath("AGVInfo.back_right.value", Integer.class);
        Integer bumpFront = body.getByPath("AGVInfo.bump_front.value", Integer.class);
        Integer bumpBack = body.getByPath("AGVInfo.bump_back.value", Integer.class);
        Integer bumpLeft = body.getByPath("AGVInfo.bump_left.value", Integer.class);
        Integer bumpRight = body.getByPath("AGVInfo.bump_right.value", Integer.class);
        Integer runTime = body.getByPath("AGVInfo.run_time.value", Integer.class);
        Integer runLength = body.getByPath("AGVInfo.run_length.value", Integer.class);
        Integer light = body.getByPath("AGVInfo.light.value", Integer.class);
        String agvErrMsg = body.getByPath("AGVInfo.AGV_Err_Msg.value", String.class);

        Integer palletState = body.getByPath("OptionalINFO.pallet_state.value", Integer.class);
        Integer liftHeight = body.getByPath("OptionalINFO.lift_height.value", Integer.class);

        //获取根据指定编号获取AGV对象
        RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
        if (rcsAgv != null) {
            //将数据更新到缓存中
            rcsAgv.setAgvName(agvName);
            rcsAgv.setAgvType(agvType);
            rcsAgv.setMapId(mapId);
            rcsAgv.setSlamX(slamX);
            rcsAgv.setSlamY(slamY);
            rcsAgv.setSlamAngle(slamAngle);
            rcsAgv.setSlamCov(slamCov);
            rcsAgv.setBattery(battery);
            rcsAgv.setVX(vX);
            rcsAgv.setVY(vY);
            rcsAgv.setVAngle(vAngle);
            rcsAgv.setAgvMode(agvMode);
            rcsAgv.setAgvState(agvState);
            rcsAgv.setPointId(pointId);
            rcsAgv.setTaskId(taskId);
            rcsAgv.setTaskState(taskState);
            rcsAgv.setTaskAct(taskAct);
            rcsAgv.setTaskParam(taskParam);
            rcsAgv.setTaskDescription(taskDescription);
            rcsAgv.setGoodsState(goodsState);
            rcsAgv.setEstopState(estopState);
            rcsAgv.setMoveDir(moveDir);
            rcsAgv.setFrontArea(frontArea);
            rcsAgv.setFrontLeft(frontLeft);
            rcsAgv.setFrontRight(frontRight);
            rcsAgv.setBackArea(backArea);
            rcsAgv.setBackLeft(backLeft);
            rcsAgv.setBackRight(backRight);
            rcsAgv.setBumpFront(bumpFront);
            rcsAgv.setBumpBack(bumpBack);
            rcsAgv.setBumpLeft(bumpLeft);
            rcsAgv.setBumpRight(bumpRight);
            rcsAgv.setRunTime(runTime);
            rcsAgv.setRunLength(runLength);
            rcsAgv.setLight(light);
            rcsAgv.setAgvErrMsg(agvErrMsg);
            rcsAgv.setPalletState(palletState);
            rcsAgv.setLiftHeight(liftHeight);
        }
    }

    /**
     * 请求地图事件
     *
     * @param body 数据
     */
    private static void agvMap(String pact, String equipmentType, String clientId, JSONObject body) {
        //获取地图检查数据
        String agvId = body.getStr("AgvId");
        Integer mapId = body.getInt("MapId");
        if (mapId == null) {
            mapId = body.getInt("MapID");
        }
        String mapCheck = body.getStr("MapCheck");
        String mapMd5 = RcsPointCache.mapMd5Map.get(mapId);
        if (mapId == null || mapCheck == null || mapMd5 == null || mapCheck.isEmpty() || !mapMd5.equals(mapCheck)) {
            //地图不一样则告警
            RcsLog.consoleLog.error(RcsLog.formatTemplate(agvId, "AGV地图数据和调度系统不一致，请检查地图数据"));
            //添加告警
            AlarmManage.triggerAlarm(agvId, AlarmCodeEnum.E10001, "rcs");
            RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
            RcsAgvCache.getRCS_AGV_CHECK().put(rcsAgv.getAgvId(), rcsAgv);
        } else {
            RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
            rcsAgv.setMapChecked(true);
            RcsAgvCache.getRCS_AGV_CHECK().remove(rcsAgv.getAgvId());
            RcsLog.consoleLog.info(RcsLog.formatTemplate(agvId, "AGV地图数据校验通过"));
        }
    }

    /**
     * 连接成功事件
     *
     * @param body 数据
     */
    private static void connectSuccess(String pact, String equipmentType, String clientId, JSONObject body) {
        //获取地图检查数据
        String agvId = body.getStr("AgvId");
        Integer mapId = body.getInt("MapId");
        if (mapId == null) {
            mapId = body.getInt("MapID");
        }
        String mapCheck = body.getStr("MapCheck");
        String mapMd5 = RcsPointCache.mapMd5Map.get(mapId);
        if (mapId == null || mapCheck == null || mapMd5 == null || mapCheck.isEmpty() || !mapMd5.equals(mapCheck)) {
            //地图不一样则告警
            RcsLog.consoleLog.error(RcsLog.formatTemplate(agvId, "AGV地图数据和调度系统不一致，请检查地图数据"));
            //添加告警
            AlarmManage.triggerAlarm(agvId, AlarmCodeEnum.E10001, "rcs");
            RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
            RcsAgvCache.getRCS_AGV_CHECK().put(rcsAgv.getAgvId(), rcsAgv);
        } else {
            RcsAgv rcsAgv = RcsAgvCache.getRcsAgvByCode(agvId);
            if (rcsAgv != null) {
                rcsAgv.setMapChecked(true);
                RcsAgvCache.getRCS_AGV_CHECK().remove(rcsAgv.getAgvId());
                RcsLog.consoleLog.info(RcsLog.formatTemplate(agvId, "AGV地图数据校验通过"));
            }
        }
    }
}