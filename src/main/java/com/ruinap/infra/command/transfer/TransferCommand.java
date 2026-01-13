package com.ruinap.infra.command.transfer;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;

import java.util.Date;

/**
 * 调度中转指令库
 *
 * @author qianye
 * @create 2025-05-20 17:02
 */
public class TransferCommand {

    /**
     * 查询自动门状态
     *
     * @param equipmentCode 设备编号
     * @param requestId     请求ID
     * @return 指令
     */
    public static JSONObject getAutoDoorState(String equipmentCode, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "door_state");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        return jsonObject;
    }

    /**
     * 打开自动门
     *
     * @param equipmentCode 设备编号
     * @param requestId     请求ID
     * @return 指令
     */
    public static JSONObject openAutoDoor(String equipmentCode, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "open_auto_door");
        jsonObject.set("request_id", requestId);
        jsonObject.set("equipment_code", equipmentCode);
        return jsonObject;
    }

    /**
     * 关门自动门
     *
     * @param equipmentCode 设备编号
     * @param requestId     请求ID
     * @return 指令
     */
    public static JSONObject closeAutoDoor(String equipmentCode, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "close_auto_door");
        jsonObject.set("request_id", requestId);
        jsonObject.set("equipment_code", equipmentCode);
        return jsonObject;
    }

    /**
     * 查询输送线状态
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject getConveyorLineState(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_state");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 设置取货中
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject setLoad(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_load");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 设置取货完成
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject setLoadFinish(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_load_finish");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 设置取货取消
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject cancelLoad(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_load_cancel");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 设置放货中
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject setUnLoad(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_line_unload");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 设置放货完成
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject setUnLoadFinish(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_unload_finish");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 设置放货取消
     *
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求ID
     * @return 指令
     */
    public static JSONObject cancelUnLoad(String equipmentCode, String relevancyPoint, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "conveyor_unload_cancel");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        jsonObject.set("relevancy_point", relevancyPoint);
        return jsonObject;
    }

    /**
     * 查询风淋室状态
     *
     * @param equipmentCode 设备编号
     * @param requestId     请求ID
     * @return 指令
     */
    public static JSONObject getAirShowerState(String equipmentCode, Long requestId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("time", DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_FORMAT));
        jsonObject.set("event", "airShower_state");
        jsonObject.set("request_id", requestId);
        jsonObject.set("data", "");
        jsonObject.set("equipment_code", equipmentCode);
        return jsonObject;
    }
}
