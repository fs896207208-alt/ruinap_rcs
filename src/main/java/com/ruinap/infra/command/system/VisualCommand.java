package com.ruinap.infra.command.system;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 调度可视化指令库
 *
 * @author qianye
 * @create 2024-09-18 1:34
 */
public class VisualCommand {

    /**
     * 连接状态
     *
     * @param code    状态码
     * @param message 描述
     * @return 指令
     */
    public static JSONObject getConnState(int code, String message) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "conn_state");
        //状态码 200成功 其他失败
        jsonObject.set("code", code);
        jsonObject.set("message", message);
        jsonObject.set("data", null);
        return jsonObject;
    }

    /**
     * 获取地图数据
     *
     * @return 指令
     */
    public static JSONObject getMapData(JSONArray jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "map_data");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取占用点位数据
     *
     * @return 指令
     */
    public static JSONObject getOccupiedPoint(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "occupied_points");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取权重点位数据
     *
     * @return 指令
     */
    public static JSONObject getWeightPoints(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "weight_points");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取AGV状态
     *
     * @return 指令
     */
    public static JSONObject getAgvState(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "agv_info");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取第三方设备状态
     *
     * @return 指令
     */
    public static JSONObject getThirdParty(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "third_party");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取任务列表数据
     *
     * @return 指令
     */
    public static JSONObject getTaskList(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "task_list");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取告警信息数据
     *
     * @return 指令
     */
    public static JSONObject getAlarmList(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "alarm_msg");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取地图图片
     *
     * @return 指令
     */
    public static JSONObject getPngData(JSONObject data) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "png_data");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 获取缓冲区数据
     *
     * @param jsonArray 数据
     * @return 指令
     */
    public static JSONObject getBufferList(CopyOnWriteArrayList<JSONObject> jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "agv_buffer");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取多层货物信息
     *
     * @return 指令
     */
    public static JSONObject getMultiLayerGoods(JSONArray jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "multi_layer_goods");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取回放列表数据
     *
     * @param jsonArray 数据
     * @return 指令
     */
    public static JSONObject getPlaybackList(JSONArray jsonArray) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "playback_list");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取回放数据
     *
     * @return 指令
     */
    public static JSONObject playbackData(JSONObject data) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("event", "playback_data");
        //状态码 200成功 其他失败
        jsonObject.set("code", 200);
        jsonObject.set("message", "成功");
        jsonObject.set("data", data);
        return jsonObject;
    }
}
