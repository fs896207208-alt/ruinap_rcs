package com.ruinap.infra.command.agv.slamopto;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.slamopto.command.agv.basis.factory.AgvCommandFactory;
import com.slamopto.command.agv.basis.strategy.*;
import com.slamopto.common.system.RcsUtils;
import com.slamopto.map.domain.point.RcsPoint;

import java.util.Date;

/**
 * 司岚 WebSocket协议工厂
 *
 * @author qianye
 * @create 2025-02-24 18:41
 */
public class SlamoptoWebSocketFactory implements AgvCommandFactory {
    // 无状态策略单例
    private static final MapCheckStrategy MAPCHECK_STRATEGY = (agvId, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS"));
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MAP");
        jsonObject.set("Body", "");
        jsonObject.set("ErrorMessage", "");
        return jsonObject.toString();
    };

    private static final StateStrategy STATE_STRATEGY = (agvId, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS"));
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_Dispatch_STATE");
        jsonObject.set("Body", "");
        jsonObject.set("ErrorMessage", "");
        return jsonObject.toString();
    };

    private static final TaskStrategy TASK_STRATEGY = (agvId, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS"));
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_TASK");
        jsonObject.set("Body", "");
        jsonObject.set("ErrorMessage", "");
        return jsonObject.toString();
    };

    private static final MoveStrategy MOVE_STRATEGY = (taskPath, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("agv_id", taskPath.getAgvId());
        jsonObjectBody.set("task_id", RcsUtils.commonFormat(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        jsonObjectBody.set("task_origin", taskPath.getTaskOrigin().getId());
        jsonObjectBody.set("task_destin", taskPath.getTaskDestin().getId());
        jsonObjectBody.set("task_action", taskPath.getTaskAction());
        jsonObjectBody.set("task_parameter", taskPath.getTaskParameter());
        jsonObjectBody.set("points_qua", taskPath.getNewPlanRoutes().size());
        jsonObjectBody.set("path_id", taskPath.getPathCode());

        JSONArray points = new JSONArray();
        //处理数据
        for (RcsPoint rcsPoint : taskPath.getNewPlanRoutes()) {
            //获取子路径的线路属性
            JSONObject jsonObjectTemp = new JSONObject();
            jsonObjectTemp.set("id", rcsPoint.getId());
            jsonObjectTemp.set("rotation", 0);
            points.add(jsonObjectTemp);
        }
        jsonObjectBody.set("points", points);
        jsonObject.set("Body", jsonObjectBody);

        return jsonObject.toString();
    };

    private static final StartStrategy START_STRATEGY = (taskPath, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE_START");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("agv_id", taskPath.getAgvId());
        jsonObjectBody.set("task_id", RcsUtils.commonFormat(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        jsonObjectBody.set("path_id", taskPath.getPathCode());
        jsonObjectBody.set("path_start_id", taskPath.getCurrentPlanOrigin().getId());
        jsonObjectBody.set("path_end_id", taskPath.getCurrentPlanDestin().getId());

        jsonObject.set("Body", jsonObjectBody);
        return jsonObject.toString();
    };

    private static final PauseStrategy PAUSE_STRATEGY = (taskPath, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE_PAUSE");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("agv_id", taskPath.getAgvId());
        jsonObjectBody.set("task_id", RcsUtils.commonFormat(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        jsonObjectBody.set("path_id", taskPath.getPathCode());
        jsonObjectBody.set("path_start_id", taskPath.getCurrentPlanOrigin().getId());
        jsonObjectBody.set("path_end_id", taskPath.getCurrentPlanDestin().getId());

        jsonObject.set("Body", jsonObjectBody);
        return jsonObject.toString();
    };

    private static final ResumeStrategy RESUME_STRATEGY = (taskPath, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE_RESUME");
        jsonObject.set("ErrorMessage", "");
        jsonObject.set("Body", new JSONObject());
        return jsonObject.toString();
    };

    private static final CancelStrategy CANCEL_STRATEGY = (taskPath, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE_INTERRUPT");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("agv_id", taskPath.getAgvId());
        jsonObjectBody.set("task_id", RcsUtils.commonFormat(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        jsonObjectBody.set("path_id", taskPath.getPathCode());
//        jsonObjectBody.set("path_start_id", taskPath.getCurrentPlanOrigin().getId());
//        jsonObjectBody.set("path_end_id", taskPath.getCurrentPlanDestin().getId());

        jsonObject.set("Body", jsonObjectBody);
        return jsonObject.toString();
    };

    private static final InterruptStrategy INTERRUPT_STRATEGY = (taskPath, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE_INTERRUPT");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("agv_id", taskPath.getAgvId());
        jsonObjectBody.set("task_id", RcsUtils.commonFormat(taskPath.getTaskCode(), taskPath.getSubTaskNo()));
        jsonObjectBody.set("path_id", taskPath.getPathCode());
        jsonObjectBody.set("path_start_id", taskPath.getCurrentPlanOrigin().getId());
        jsonObjectBody.set("path_end_id", taskPath.getCurrentPlanDestin().getId());

        jsonObject.set("Body", jsonObjectBody);
        return jsonObject.toString();
    };

    private static final MoveResetStrategy MOVE_RESET_STRATEGY = (agvId, pointId, yaw, battery, load, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_MOVE_RESET");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("agv_id", agvId);
        JSONObject jsonObjectReset = new JSONObject();
        jsonObjectReset.set("id", pointId);
        jsonObjectReset.set("yaw", yaw);
        jsonObjectReset.set("battery", battery);
        jsonObjectReset.set("load", load);
        jsonObjectBody.set("reset", jsonObjectReset);

        jsonObject.set("Body", jsonObjectBody);
        return jsonObject.toString();
    };

    private static final ReLocationStrategy RE_LOCATION_STRATEGY = (agvId, mapId, pointId, angle, mark) -> {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("IsSucceed", true);
        jsonObject.set("DateTime", DateUtil.now());
        jsonObject.set("DataStamps", mark);
        jsonObject.set("Event", "REQUEST_AGV_RELOC");
        jsonObject.set("ErrorMessage", "");

        JSONObject jsonObjectBody = new JSONObject();
        jsonObjectBody.set("map_id", mapId);
        jsonObjectBody.set("point_id", pointId);
        jsonObjectBody.set("angle", angle);

        jsonObject.set("Body", jsonObjectBody);
        return jsonObject.toString();
    };

    /**
     * 获取地图检查指令
     *
     * @return JSONObject
     */
    @Override
    public MapCheckStrategy getMapCheckStrategy() {
        return MAPCHECK_STRATEGY;
    }

    /**
     * 获取状态指令
     *
     * @return JSONObject
     */
    @Override
    public StateStrategy getStateStrategy() {
        return STATE_STRATEGY;
    }

    /**
     * 获取任务指令
     *
     * @return JSONObject
     */
    @Override
    public TaskStrategy getTaskStrategy() {
        return TASK_STRATEGY;
    }

    /**
     * 获取移动指令
     *
     * @return JSONObject
     */
    @Override
    public MoveStrategy getMoveStrategy() {
        return MOVE_STRATEGY;
    }

    /**
     * 获取开始指令
     *
     * @return JSONObject
     */
    @Override
    public StartStrategy getStartStrategy() {
        return START_STRATEGY;
    }

    /**
     * 获取暂停指令
     *
     * @return JSONObject
     */
    @Override
    public PauseStrategy getPauseStrategy() {
        return PAUSE_STRATEGY;
    }

    /**
     * 获取恢复指令
     *
     * @return JSONObject
     */
    @Override
    public ResumeStrategy getResumeStrategy() {
        return RESUME_STRATEGY;
    }

    /**
     * 获取取消指令
     *
     * @return JSONObject
     */
    @Override
    public CancelStrategy getCancelStrategy() {
        return CANCEL_STRATEGY;
    }

    /**
     * 获取中断指令
     *
     * @return JSONObject
     */
    @Override
    public InterruptStrategy getInterruptStrategy() {
        return INTERRUPT_STRATEGY;
    }

    /**
     * 获取重置指令
     *
     * @return JSONObject
     */
    @Override
    public MoveResetStrategy getMoveResetStrategy() {
        return MOVE_RESET_STRATEGY;
    }

    /**
     * 获取重定位指令
     *
     * @return JSONObject
     */
    @Override
    public ReLocationStrategy getReLocationStrategy() {
        return RE_LOCATION_STRATEGY;
    }
}
