package com.ruinap.infra.command.system;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.ruinap.infra.util.CachedTimeUtils;
import com.ruinap.infra.util.FastJsonBuilder;
import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * 调度业务指令库
 *
 * @author qianye
 * @create 2025-05-19 19:35
 */
public class BusinessCommand {

    /**
     * 写入任务列表
     *
     * @param out       Netty ByteBuf
     * @param code      状态码
     * @param msg       状态描述
     * @param jsonArray 任务数据 (支持 List 或 JSONArray)
     */
    public static void writeTaskList(ByteBuf out, int code, String msg, List<?> jsonArray) {
        writeCommonResponse(out, "task_list", code, msg, jsonArray);
    }

    /**
     * 写入 AGV 状态
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param agvs 数据 (支持 List 或 JSONArray)
     */
    public static void writeAgvState(ByteBuf out, int code, String msg, List<?> agvs) {
        writeCommonResponse(out, "agv_state", code, msg, agvs);
    }

    /**
     * 写入置顶任务指令
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据 (支持 JSONObject 或 Map)
     */
    public static void writeTopTask(ByteBuf out, int code, String msg, JSONObject data) {
        writeCommonResponse(out, "to_top_task", code, msg, data);
    }

    /**
     * 写入任务取消指令
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     */
    public static void writeTaskCancel(ByteBuf out, int code, String msg, JSONObject data) {
        writeCommonResponse(out, "task_cancel", code, msg, data);
    }

    /**
     * 写入任务创建指令
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     */
    public static void writeTaskCreate(ByteBuf out, int code, String msg, Object data) {
        writeCommonResponse(out, "task_create", code, msg, data);
    }

    /**
     * 写入任务状态变更指令
     * <p>
     * 注意：此指令结构特殊，没有 code 和 msg，只有 data
     *
     * @param out  Netty ByteBuf
     * @param data 数据
     */
    public static void writeTaskStateChange(ByteBuf out, JSONObject data) {
        new FastJsonBuilder(out)
                .set("date_time", CachedTimeUtils.getNowStringWithMillis())
                .set("request_id", IdUtil.simpleUUID())
                .set("event", "task_state_change")
                .set("data", data)
                .finish();
    }

    /**
     * 写入请求充电指令
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     */
    public static void writeDoCharge(ByteBuf out, int code, String msg, JSONObject data) {
        writeCommonResponse(out, "do_charge", code, msg, data);
    }

    /**
     * 写入请求移动指令
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     */
    public static void writeDoMove(ByteBuf out, int code, String msg, JSONObject data) {
        writeCommonResponse(out, "do_move", code, msg, data);
    }

    /**
     * 写入 AGV 信息
     *
     * @param out  Netty ByteBuf
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     */
    public static void writeAgvInfo(ByteBuf out, int code, String msg, JSONArray data) {
        writeCommonResponse(out, "agv_info", code, msg, data);
    }

    // =========================================================
    // 私有通用方法
    // =========================================================

    /**
     * 通用响应构建逻辑 (极简写法)
     */
    private static void writeCommonResponse(ByteBuf out, String event, int code, String msg, Object data) {
        new FastJsonBuilder(out)
                .set("date_time", CachedTimeUtils.getNowStringWithMillis())
                .set("request_id", IdUtil.simpleUUID())
                .set("event", event)
                .set("code", code)
                .set("msg", msg)
                .set("data", data)
                .finish();
    }
}
