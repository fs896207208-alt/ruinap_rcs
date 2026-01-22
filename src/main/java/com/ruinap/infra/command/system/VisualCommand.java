package com.ruinap.infra.command.system;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.util.CachedTimeUtils;
import com.ruinap.infra.util.FastJsonBuilder;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.Map;

/**
 * 调度可视化指令库
 *
 * @author qianye
 * @create 2024-09-18 1:34
 */
@Component
public class VisualCommand {

    /**
     * 写入连接状态
     *
     * @param out     Netty ByteBuf
     * @param code    状态码
     * @param message 描述
     */
    public void writeConnState(ByteBuf out, int code, String message) {
        // data 传 null
        writeCommonResponse(out, "conn_state", code, message, null);
    }

    /**
     * 写入地图数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 地图数据
     */
    public void writeMapData(ByteBuf out, Object[] jsonArray) {
        writeSuccessResponse(out, "map_data", jsonArray);
    }

    /**
     * 写入占用点位数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 点位数据
     */
    public void writeOccupiedPoint(ByteBuf out, Object[] jsonArray) {
        writeSuccessResponse(out, "occupied_points", jsonArray);
    }

    /**
     * 写入权重点位数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 点位数据
     */
    public void writeWeightPoints(ByteBuf out, Map jsonArray) {
        writeSuccessResponse(out, "weight_points", jsonArray);
    }

    /**
     * 写入 AGV 状态
     *
     * @param out       Netty ByteBuf
     * @param jsonArray AGV列表
     */
    public void writeAgvState(ByteBuf out, Object[] jsonArray) {
        writeSuccessResponse(out, "agv_info", jsonArray);
    }

    /**
     * 写入第三方设备状态
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 设备列表
     */
    public void writeThirdParty(ByteBuf out, List<JSONObject> jsonArray) {
        writeSuccessResponse(out, "third_party", jsonArray);
    }

    /**
     * 写入任务列表数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 任务列表
     */
    public void writeTaskList(ByteBuf out, Object[] jsonArray) {
        writeSuccessResponse(out, "task_list", jsonArray);
    }

    /**
     * 写入告警信息数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 告警列表
     */
    public void writeAlarmList(ByteBuf out, List jsonArray) {
        writeSuccessResponse(out, "alarm_msg", jsonArray);
    }

    /**
     * 写入地图图片数据
     *
     * @param out  Netty ByteBuf
     * @param data 图片数据
     */
    public void writePngData(ByteBuf out, JSONObject data) {
        writeSuccessResponse(out, "png_data", data);
    }

    /**
     * 写入缓冲区数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 缓冲区列表
     */
    public void writeBufferList(ByteBuf out, Object[] jsonArray) {
        writeSuccessResponse(out, "agv_buffer", jsonArray);
    }

    /**
     * 写入多层货物信息
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 货物信息
     */
    public void writeMultiLayerGoods(ByteBuf out, JSONArray jsonArray) {
        writeSuccessResponse(out, "multi_layer_goods", jsonArray);
    }

    /**
     * 写入回放列表数据
     *
     * @param out       Netty ByteBuf
     * @param jsonArray 回放列表
     */
    public void writePlaybackList(ByteBuf out, JSONArray jsonArray) {
        writeSuccessResponse(out, "playback_list", jsonArray);
    }

    /**
     * 写入回放详情数据
     *
     * @param out  Netty ByteBuf
     * @param data 回放数据
     */
    public void writePlaybackData(ByteBuf out, JSONObject data) {
        writeSuccessResponse(out, "playback_data", data);
    }

    // =========================================================
    // 私有通用方法
    // =========================================================

    /**
     * 写入默认成功响应 (code=200, message="成功")
     */
    private void writeSuccessResponse(ByteBuf out, String event, Object data) {
        writeCommonResponse(out, event, 200, "成功", data);
    }

    /**
     * 通用响应构建逻辑
     */
    private void writeCommonResponse(ByteBuf out, String event, int code, String message, Object data) {
        new FastJsonBuilder(out)
                // 增加时间戳 (响应你的 CachedTimeUtils 需求)
                .set("date_time", CachedTimeUtils.getNowStringWithMillis())
                .set("event", event)
                .set("code", code)
                .set("message", message)
                .set("data", data)
                .finish();
    }
}