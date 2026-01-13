package com.ruinap.adapter.communicate.event;

import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.event.AbstractServerEvent;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.log.RcsLog;

/**
 * 调度仿真事件
 *
 * @author qianye
 * @create 2025-05-19 19:27
 */
public class SimulationWebSocketEvent extends AbstractServerEvent {

    /**
     * 处理事件
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    public static void receiveMessage(String serverId, JSONObject jsonObject) {
        String event = jsonObject.getStr("event");
        switch (event.toLowerCase()) {
            case "door_state":
                doorState(serverId, jsonObject);
                break;
            case "open_auto_door":
                openAutoDoor(serverId, jsonObject);
                break;
            case "close_auto_door":
                closeAutoDoor(serverId, jsonObject);
                break;
            case "conveyor_state":
                conveyorState(serverId, jsonObject);
                break;
            case "conveyor_load":
                conveyorLoad(serverId, jsonObject);
                break;
            case "conveyor_load_finish":
                conveyorLoadFinish(serverId, jsonObject);
                break;
            case "conveyor_load_cancel":
                conveyorLoadCancel(serverId, jsonObject);
                break;
            case "conveyor_line_unload":
                conveyorUnload(serverId, jsonObject);
                break;
            case "conveyor_unload_finish":
                conveyorUnloadFinish(serverId, jsonObject);
                break;
            case "conveyor_unload_cancel":
                conveyorUnloadCancel(serverId, jsonObject);
                break;
            case "airshower_state":
                airShowerState(serverId, jsonObject);
                break;
            default:
                RcsLog.consoleLog.error("Simulation 未知事件：" + event);
                break;
        }
    }

    /**
     * 获取自动门状态
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void doorState(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", AutoDoorSimulation.getState(equipmentCode));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 请求开门
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void openAutoDoor(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getLong("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", AutoDoorSimulation.requestOpen(equipmentCode));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 请求关门
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void closeAutoDoor(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", AutoDoorSimulation.requestClose(equipmentCode));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 获取输送线状态
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorState(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.getState(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 设置输送线取货中
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorLoad(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.setLoad(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 设置输送线取货完成
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorLoadFinish(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.setLoadFinish(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 设置输送线取货取消
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorLoadCancel(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.cancelLoad(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 设置输送线放货中
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorUnload(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.setUnLoad(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 设置输送线放货完成
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorUnloadFinish(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.setUnLoadFinish(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 设置输送线放货取消
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void conveyorUnloadCancel(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", ConveyorLineSimulation.cancelUnLoad(equipmentCode, jsonObject.getStr("relevancy_point")));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }

    /**
     * 获取风淋室状态
     *
     * @param serverId   客户端id
     * @param jsonObject 数据
     */
    private static void airShowerState(String serverId, JSONObject jsonObject) {
        String equipmentCode = jsonObject.getStr("equipment_code");
        JSONObject reJSONObject = new JSONObject();
        reJSONObject.set("event", jsonObject.getStr("event"));
        reJSONObject.set("equipment_code", equipmentCode);
        reJSONObject.set("request_id", jsonObject.getStr("request_id"));
        reJSONObject.set("code", 200);
        reJSONObject.set("msg", "成功");
        reJSONObject.set("data", AirShowerSimulation.getState(equipmentCode));
        NettyServer.getServer(ProtocolEnum.WEBSOCKET_SERVER).sendMessage(serverId, reJSONObject);
    }
}
