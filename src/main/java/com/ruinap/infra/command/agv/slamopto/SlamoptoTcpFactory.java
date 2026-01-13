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
 * 司岚 TCP协议工厂
 *
 * @author qianye
 * @create 2025-02-24 18:44
 */
public class SlamoptoTcpFactory implements AgvCommandFactory {

    /**
     * 无状态策略单例
     */
    private static final StateStrategy STATE_STRATEGY = new StateStrategy() {
        @Override
        public String getCommand(String agvId, Long mark) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("IsSucceed", true);
            jsonObject.set("DateTime", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS"));
            jsonObject.set("DataStamps", mark);
            jsonObject.set("Event", "REQUEST_AGV_STATE");
            jsonObject.set("Body", "");
            jsonObject.set("ErrorMessage", "");
            return jsonObject.toString();
        }
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
        jsonObjectBody.set("task_origin", taskPath.getCurrentPlanOrigin().getId());
        jsonObjectBody.set("task_destin", taskPath.getCurrentPlanDestin().getId());
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

    @Override
    public MapCheckStrategy getMapCheckStrategy() {
        return null;
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

    @Override
    public TaskStrategy getTaskStrategy() {
        return null;
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

    @Override
    public StartStrategy getStartStrategy() {
        return null;
    }

    @Override
    public PauseStrategy getPauseStrategy() {
        return null;
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return null;
    }

    @Override
    public CancelStrategy getCancelStrategy() {
        return null;
    }

    @Override
    public InterruptStrategy getInterruptStrategy() {
        return null;
    }

    @Override
    public MoveResetStrategy getMoveResetStrategy() {
        return null;
    }

    @Override
    public ReLocationStrategy getReLocationStrategy() {
        return null;
    }
}
