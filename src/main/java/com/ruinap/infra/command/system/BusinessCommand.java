package com.ruinap.infra.command.system;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import com.slamopto.task.domain.RcsTask;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 调度业务指令库
 *
 * @author qianye
 * @create 2025-05-19 19:35
 */
public class BusinessCommand {

    static JSONConfig jsonConfig = JSONConfig.create().setIgnoreNullValue(false);

    /**
     * 获取任务列表
     *
     * @param code      状态码
     * @param msg       状态描述
     * @param jsonArray 任务数据
     * @return 指令
     */
    public static JSONObject getTaskList(int code, String msg, CopyOnWriteArrayList<RcsTask> jsonArray) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "task_list");
        //状态码 200成功 其他失败
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", jsonArray);
        return jsonObject;
    }

    /**
     * 获取AGV状态
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param agvs 数据
     * @return 指令
     */
    public static JSONObject getAgvState(int code, String msg, List agvs) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "agv_state");
        //状态码 200成功 其他失败
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", agvs);
        return jsonObject;
    }

    /**
     * 置顶任务
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     * @return 指令
     */
    public static JSONObject topTask(int code, String msg, JSONObject data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "to_top_task");
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 任务取消
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     * @return 指令
     */
    public static JSONObject taskCancel(int code, String msg, JSONObject data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "task_cancel");
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 任务创建
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     * @return 指令
     */
    public static JSONObject taskCreate(int code, String msg, Object data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "task_create");
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 任务状态变更
     *
     * @param data 数据
     * @return 指令
     */
    public static JSONObject taskStateChange(JSONObject data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "task_state_change");
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 请求充电
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     * @return 指令
     */
    public static JSONObject doCharge(int code, String msg, JSONObject data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "do_charge");
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 请求移动
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     * @return 指令
     */
    public static JSONObject doMove(int code, String msg, JSONObject data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "do_move");
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", data);
        return jsonObject;
    }

    /**
     * 请求移动
     *
     * @param code 状态码
     * @param msg  状态描述
     * @param data 数据
     * @return 指令
     */
    public static JSONObject agvInfo(int code, String msg, JSONArray data) {
        JSONObject jsonObject = new JSONObject(jsonConfig);
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("request_id", IdUtil.simpleUUID());
        jsonObject.set("event", "agv_info");
        jsonObject.set("code", code);
        jsonObject.set("msg", msg);
        jsonObject.set("data", data);
        return jsonObject;
    }
}
