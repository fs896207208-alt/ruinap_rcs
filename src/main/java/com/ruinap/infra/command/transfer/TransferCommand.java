package com.ruinap.infra.command.transfer;

import com.ruinap.infra.util.CachedTimeUtils;
import com.ruinap.infra.util.FastJsonBuilder;
import io.netty.buffer.ByteBuf;

/**
 * 调度中转指令库
 *
 * @author qianye
 * @create 2025-05-20 17:02
 */
public class TransferCommand {

    // =========================================================
    // 1. 自动门 (Auto Door)
    // =========================================================

    /**
     * 获取自动门状态
     *
     * @param out           指令字节流
     * @param equipmentCode 设备编号
     * @param requestId     请求编号
     */
    public static void writeGetAutoDoorState(ByteBuf out, String equipmentCode, Long requestId) {
        writeCommonCommand(out, equipmentCode, requestId, "door_state");
    }

    /**
     * 打开自动门
     *
     * @param out           指令字节流
     * @param equipmentCode 设备编号
     * @param requestId     请求编号
     */
    public static void writeOpenAutoDoor(ByteBuf out, String equipmentCode, Long requestId) {
        writeCommonCommand(out, equipmentCode, requestId, "open_auto_door");
    }

    /**
     * 关闭自动门
     *
     * @param out           指令字节流
     * @param equipmentCode 设备编号
     * @param requestId     请求编号
     */
    public static void writeCloseAutoDoor(ByteBuf out, String equipmentCode, Long requestId) {
        writeCommonCommand(out, equipmentCode, requestId, "close_auto_door");
    }

    // =========================================================
    // 2. 输送线 (Conveyor)
    // =========================================================

    /**
     * 获取输送线状态
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeGetConveyorLineState(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_state", relevancyPoint);
    }

    /**
     * 设置输送线装载中
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeSetLoad(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_load", relevancyPoint);
    }

    /**
     * 确认装载完成
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeSetLoadFinish(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_load_finish", relevancyPoint);
    }

    /**
     * 取消装载
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeCancelLoad(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_load_cancel", relevancyPoint);
    }

    /**
     * 设置输送线卸载中
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeSetUnLoad(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_line_unload", relevancyPoint);
    }

    /**
     * 确认卸载完成
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeSetUnLoadFinish(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_unload_finish", relevancyPoint);
    }

    /**
     * 取消卸载
     *
     * @param out            指令字节流
     * @param equipmentCode  设备编号
     * @param relevancyPoint 关联点位
     * @param requestId      请求编号
     */
    public static void writeCancelUnLoad(ByteBuf out, String equipmentCode, String relevancyPoint, Long requestId) {
        writeConveyorCommand(out, equipmentCode, requestId, "conveyor_unload_cancel", relevancyPoint);
    }

    // =========================================================
    // 3. 风淋室 (Air Shower)
    // =========================================================

    /**
     * 获取风淋室状态
     *
     * @param out           指令字节流
     * @param equipmentCode 设备编号
     * @param requestId     请求编号
     */
    public static void writeGetAirShowerState(ByteBuf out, String equipmentCode, Long requestId) {
        writeCommonCommand(out, equipmentCode, requestId, "airShower_state");
    }

    // =========================================================
    // 私有通用方法 (复用逻辑)
    // =========================================================

    /**
     * 通用指令写入器
     */
    private static void writeCommonCommand(ByteBuf out, String equipmentCode, Long requestId, String event) {
        new FastJsonBuilder(out)
                .set("date_time", CachedTimeUtils.getNowStringWithMillis())
                .set("event", event)
                .set("request_id", requestId)
                .set("data", "")
                .set("equipment_code", equipmentCode)
                .finish();
    }

    /**
     * 输送线指令写入器 (包含 relevancy_point)
     */
    private static void writeConveyorCommand(ByteBuf out, String equipmentCode, Long requestId, String event, String relevancyPoint) {
        new FastJsonBuilder(out)
                .set("date_time", CachedTimeUtils.getNowStringWithMillis())
                .set("event", event)
                .set("request_id", requestId)
                .set("data", "")
                .set("equipment_code", equipmentCode)
                .set("relevancy_point", relevancyPoint)
                .finish();
    }
}