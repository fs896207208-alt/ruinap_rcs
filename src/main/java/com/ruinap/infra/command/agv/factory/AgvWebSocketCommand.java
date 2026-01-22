package com.ruinap.infra.command.agv.factory;

import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.util.CachedTimeUtils;
import com.ruinap.infra.util.FastJsonBuilder;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度 WebSocket协议工厂
 * 使用 Jackson Streaming API 直接写入 ByteBuf，零 GC 压力
 *
 * @author qianye
 * @create 2025-02-24 18:41
 */
@Component
public class AgvWebSocketCommand implements AgvCommandFactory {

    @Autowired
    private TaskManager taskManager;

    /**
     * 获取协议名称
     *
     * @return 协议名称
     */
    @Override
    public String getProtocol() {
        return ProtocolEnum.WEBSOCKET_CLIENT.getProtocol();
    }

    // =========================================================
    // 1. 核心控制指令
    // =========================================================

    /**
     * 写入移动指令
     *
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    @Override
    public void writeMoveCommand(ByteBuf out, TaskPath taskPath, Long mark) {
        // 1. 构建 Body 数据 (使用 LinkedHashMap 保持协议字段顺序)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agv_id", taskPath.getAgvId());
        body.put("task_id", taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        body.put("task_origin", String.valueOf(taskPath.getTaskOrigin().getId()));
        body.put("task_destin", String.valueOf(taskPath.getTaskDestin().getId()));
        body.put("task_action", taskPath.getTaskAction());
        body.put("task_parameter", String.valueOf(taskPath.getTaskParameter()));
        body.put("points_qua", taskPath.getNewPlanRoutes().size());
        body.put("path_id", taskPath.getPathCode());

        // 2. 构建 Points 数组
        List<Map<String, Object>> points = new ArrayList<>();
        for (RcsPoint rcsPoint : taskPath.getNewPlanRoutes()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", rcsPoint.getId());
            point.put("rotation", 0);
            points.add(point);
        }
        body.put("points", points);

        // 3. 写入 ByteBuf
        try (FastJsonBuilder json = new FastJsonBuilder(out)) {
            writeHeader(json, mark, "REQUEST_AGV_MOVE");
            // 自动序列化复杂对象
            json.set("Body", body);
        }
    }

    /**
     * 写入开始指令
     *
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    @Override
    public void writeStartCommand(ByteBuf out, TaskPath taskPath, Long mark) {
        writeStandardPathCommand(out, taskPath, mark, "REQUEST_AGV_MOVE_START", true);
    }

    /**
     * 写入暂停指令
     *
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    @Override
    public void writePauseCommand(ByteBuf out, TaskPath taskPath, Long mark) {
        writeStandardPathCommand(out, taskPath, mark, "REQUEST_AGV_MOVE_PAUSE", true);
    }

    /**
     * 写入恢复指令
     *
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    @Override
    public void writeResumeCommand(ByteBuf out, TaskPath taskPath, Long mark) {
        try (FastJsonBuilder json = new FastJsonBuilder(out)) {
            writeHeader(json, mark, "REQUEST_AGV_MOVE_RESUME");
            // Resume 的 Body 是空对象 {}，这里传一个空 Map 即可
            json.set("Body", new LinkedHashMap<>());
        }
    }

    /**
     * 写入取消指令
     *
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    @Override
    public void writeCancelCommand(ByteBuf out, TaskPath taskPath, Long mark) {
        writeStandardPathCommand(out, taskPath, mark, "REQUEST_AGV_MOVE_INTERRUPT", false);
    }

    /**
     * 写入中断指令
     *
     * @param out      指令字节流
     * @param taskPath 任务路径
     * @param mark     数据戳
     */
    @Override
    public void writeInterruptCommand(ByteBuf out, TaskPath taskPath, Long mark) {
        writeStandardPathCommand(out, taskPath, mark, "REQUEST_AGV_MOVE_INTERRUPT", true);
    }

    // =========================================================
    // 2. 状态查询指令 (Body 为空字符串)
    // =========================================================

    /**
     * 写入地图检查指令
     *
     * @param out   指令字节流
     * @param agvId AGV编号
     * @param mark  数据戳
     */
    @Override
    public void writeMapCheckCommand(ByteBuf out, String agvId, Long mark) {
        writeEmptyBodyStringCommand(out, mark, "REQUEST_AGV_MAP");
    }

    /**
     * 写入状态查询指令
     *
     * @param out   指令字节流
     * @param agvId AGV编号
     * @param mark  数据戳
     */
    @Override
    public void writeStateCommand(ByteBuf out, String agvId, Long mark) {
        writeEmptyBodyStringCommand(out, mark, "REQUEST_Dispatch_STATE");
    }

    /**
     * 写入任务查询指令
     *
     * @param out   指令字节流
     * @param agvId AGV编号
     * @param mark  数据戳
     */
    @Override
    public void writeTaskCommand(ByteBuf out, String agvId, Long mark) {
        writeEmptyBodyStringCommand(out, mark, "REQUEST_AGV_TASK");
    }

    // =========================================================
    // 3. 高级控制指令
    // =========================================================

    /**
     * 写入移动复位指令
     *
     * @param out     指令字节流
     * @param agvId   AGV编号
     * @param pointId 点位编号
     * @param yaw     角度
     * @param battery 电池电量
     * @param load    有无货
     * @param mark    数据戳
     */
    @Override
    public void writeMoveResetCommand(ByteBuf out, String agvId, Integer pointId, Integer yaw, Integer battery, Integer load, Long mark) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agv_id", agvId);

        Map<String, Object> resetInfo = new LinkedHashMap<>();
        resetInfo.put("id", pointId);
        resetInfo.put("yaw", yaw);
        resetInfo.put("battery", battery);
        resetInfo.put("load", load);

        body.put("reset", resetInfo);

        try (FastJsonBuilder json = new FastJsonBuilder(out)) {
            writeHeader(json, mark, "REQUEST_AGV_MOVE_RESET");
            json.set("Body", body);
        }
    }

    /**
     * 写入重新定位指令
     *
     * @param out     指令字节流
     * @param agvId   AGV编号
     * @param mapId   地图编号
     * @param pointId 点位编号
     * @param angle   角度
     * @param mark    数据戳
     */
    @Override
    public void writeReLocationCommand(ByteBuf out, String agvId, Integer mapId, Integer pointId, Integer angle, Long mark) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("map_id", mapId);
        body.put("point_id", pointId);
        body.put("angle", angle);

        try (FastJsonBuilder json = new FastJsonBuilder(out)) {
            writeHeader(json, mark, "REQUEST_AGV_RELOC");
            json.set("Body", body);
        }
    }

    // =========================================================
    // 私有辅助方法
    // =========================================================

    /**
     * 写入公共头部
     */
    private void writeHeader(FastJsonBuilder json, Long mark, String event) {
        json.set("IsSucceed", true);
        json.set("DateTime", CachedTimeUtils.getNowStringWithMillis());
        json.set("DataStamps", mark);
        json.set("Event", event);
        json.set("ErrorMessage", "");
    }

    /**
     * 处理标准路径指令
     */
    private void writeStandardPathCommand(ByteBuf out, TaskPath taskPath, Long mark, String event, boolean includeEndpoints) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agv_id", taskPath.getAgvId());
        body.put("task_id", taskManager.formatTask(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        body.put("path_id", taskPath.getPathCode());

        if (includeEndpoints) {
            if (taskPath.getCurrentPlanOrigin() != null) {
                body.put("path_start_id", String.valueOf(taskPath.getCurrentPlanOrigin().getId()));
            }
            if (taskPath.getCurrentPlanDestin() != null) {
                body.put("path_end_id", String.valueOf(taskPath.getCurrentPlanDestin().getId()));
            }
        }

        try (FastJsonBuilder json = new FastJsonBuilder(out)) {
            writeHeader(json, mark, event);
            json.set("Body", body);
        }
    }

    /**
     * 处理 Body 为空字符串的指令
     */
    private void writeEmptyBodyStringCommand(ByteBuf out, Long mark, String event) {
        try (FastJsonBuilder json = new FastJsonBuilder(out)) {
            writeHeader(json, mark, event);
            json.set("Body", "");
        }
    }
}