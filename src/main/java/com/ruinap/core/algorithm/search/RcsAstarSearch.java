package com.ruinap.core.algorithm.search;

import cn.hutool.core.util.StrUtil;
import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.algorithm.TrafficManager;
import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.domain.TaskPath;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.enums.task.TaskTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import org.graph4j.Digraph;
import org.graph4j.shortestpath.AStarAlgorithm;
import org.graph4j.shortestpath.AStarEstimator;
import org.graph4j.util.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Astar搜索算法实现类
 *
 * @author qianye
 * @create 2026-02-25 17:37
 */
@Service
@Order(8)
public class RcsAstarSearch {

    @Autowired
    private CoreYaml coreYaml;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private TrafficManager trafficManager;
    @Autowired
    private SlideTimeWindow slideTimeWindow;

    /**
     * 定义路径长度不能超过原始路径长度的距离
     */
    private int PATH_LENGTH_DISTANCE;
    /**
     * 重新规划路径往回退指定个点
     */
    private int RETREAT_POINT;
    /**
     * 经过多少个十字路口后停止规划
     */
    private int STOP_INTERSECTION;
    /**
     * 新规划路径点数超过多少个点位时，可以下发给AGV
     */
    private int PLAN_ALLOW_DELIVERY_THRESHOLD;

    // 容器会在所有的 @Autowired 依赖注入完成之后，立刻自动调用这个方法！
    @PostConstruct
    public void initConfig() {
        this.PATH_LENGTH_DISTANCE = coreYaml.getAlgorithmCommon().getOrDefault("path_length_distance", 5000);
        this.RETREAT_POINT = coreYaml.getAlgorithmCommon().getOrDefault("retreat_point", 1);
        this.STOP_INTERSECTION = coreYaml.getAlgorithmCommon().getOrDefault("stop_intersection", 1);
        this.PLAN_ALLOW_DELIVERY_THRESHOLD = coreYaml.getAlgorithmCommon().getOrDefault("plan_allow_delivery_threshold", 1);
    }

    /**
     * A*路径规划算法
     *
     * @param taskPath 任务路径
     * @param start    起点
     * @param goal     终点
     * @return 路径点集合
     */
    public List<RcsPoint> aStarSearch(TaskPath taskPath, RcsPoint start, RcsPoint goal) {
        //目标点
        RcsPoint goalPoint = goal;

        //获取任务
        RcsTask rcsTask = taskManager.taskCache.get(taskPath.getTaskCode());
        if (rcsTask == null) {
            RcsLog.consoleLog.warn("获取不到任务：" + taskPath.getTaskCode());
            return null;
        }
        //如果是充电任务并且起点与终点相同
        if (TaskTypeEnum.isEnumByCode(TaskTypeEnum.CHARGE, rcsTask.getTaskType()) && taskPath.getPathCode() == 0 && start.equals(goal)) {
            //获取任务起点的出边
            List<RcsPointTarget> outgoingEdges = mapManager.getOutgoingEdges(goalPoint.getMapId(), goalPoint.getId());
            //获取任务起点的第一个点位属性
            RcsPointTarget firstTarget = outgoingEdges.getFirst();
            //设置任务新起点
            goalPoint = mapManager.getRcsPoint(goalPoint.getMapId(), firstTarget.getId());
            if (goalPoint == null) {
                goalPoint = goal;
            } else {
                RcsLog.consoleLog.warn("检测到充电任务起点与终点相同，则先规划到" + goalPoint.getId() + "点位");
            }
        }

        String formatTemplate = StrUtil.format(RcsLog.getTemplate(3), taskPath.getAgvId(), "规划路径", "本次规划起点为：" + taskManager.formatPoint(start.getMapId(), start.getId()) + "，终点为：" + taskManager.formatPoint(goal.getMapId(), goal.getId()));
        RcsLog.taskLog.info(formatTemplate);
        RcsLog.algorithmLog.info(formatTemplate);
        RcsLog.consoleLog.info(formatTemplate);

        // 不考虑占用情况进行路径搜索
        RouteResult expectCostRouteResult = aStarSearch(taskPath.getAgvId(), start, goal);
        if (expectCostRouteResult.getPaths().isEmpty()) {
            formatTemplate = StrUtil.format(RcsLog.getTemplate(2), taskPath.getAgvId(), "规划起点为：" + start + "，终点为：" + goal + "，路径规划失败，地图数据异常");
            RcsLog.consoleLog.error(formatTemplate);
            RcsLog.algorithmLog.error(formatTemplate);
            RcsLog.sysLog.error(formatTemplate);
        }
        // 获取规划路径点
        List<RcsPoint> returnPoints = expectCostRouteResult.getPaths();
        //设置路径数据
        // 设置不考虑占用的路径
        taskPath.setExpectRoutes(returnPoints);
        // 计算不考虑占用的路径总代价
        int expectCost = expectCostRouteResult.getPathCost();
        // 设置首次路径总代价
        taskPath.setExpectCost(expectCost);

        //初始化十字路口计数器
        int intersectionCount = 0;
        for (int i = 0; i < returnPoints.size(); i++) {
            // 获取当前点
            RcsPoint point = returnPoints.get(i);

            // 获取当前点的出边
            List<RcsPointTarget> outgoingEdges = mapManager.getOutgoingEdges(point.getMapId(), point.getId());
            // 检查并处理交叉点数量，超过预设限制则停止处理并返回当前已处理的点集合
            if (outgoingEdges.size() > 1) {
                if (intersectionCount > STOP_INTERSECTION) {
                    // 仅保留前STOP_INTERSECTION个交叉点
                    List<RcsPoint> subList = returnPoints.subList(0, intersectionCount);
                    returnPoints = new ArrayList<>(subList);
                    break;
                }
                // 交叉点数量增加
                intersectionCount++;
            }
        }

        //检测新路径的缓冲区是否安全
        RcsAgv rcsAgvByCode = agvManager.getRcsAgvByCode(taskPath.getAgvId());
        //检查新路径规划是否安全，如果存在碰撞，则回退路径点直到安全或路径为空
        List<RcsPoint> safePlanPoints = trafficManager.findSafePlan(rcsAgvByCode.getMapId(), taskPath.getAgvId(), returnPoints, rcsAgvByCode.getCarRange());
        if (!safePlanPoints.isEmpty()) {
            returnPoints = new ArrayList<>(safePlanPoints);
        }

        //判断新规划路径点位数是否超过配置允许的点位数
        if ((returnPoints.size() - 1) < PLAN_ALLOW_DELIVERY_THRESHOLD) {
            // 打印记录日志
            formatTemplate = StrUtil.format(RcsLog.getTemplate(2), taskPath.getAgvId(), "新规划路径数[" + (returnPoints.size() - 1) + "]小于配置允许的点位数[" + PLAN_ALLOW_DELIVERY_THRESHOLD + "]，因此不下发路径");
            RcsLog.taskLog.warn(formatTemplate);
            RcsLog.algorithmLog.warn(formatTemplate);
            RcsLog.consoleLog.warn(formatTemplate);
            //清空路径
            returnPoints = new ArrayList<>();
        }
        return returnPoints;
    }

    /**
     * 使用A*算法进行从起点到终点的路径搜索
     * 如果考虑路径被占用，则可能会返回绕路路径列表
     *
     * @param agvCode AGV编号
     * @param start   起点
     * @param goal    终点
     * @return 返回结果，包括是否到达终点、路径代价和路径
     */
    public RouteResult aStarSearch(String agvCode, RcsPoint start, RcsPoint goal) {
        List<RcsPoint> resultPoints = new ArrayList<>();
        boolean isArrive = false;

        Digraph<RcsPoint, RcsPointTarget> graph = mapManager.getGraph();
        if (graph == null || graph.isEmpty()) {
            RcsLog.consoleLog.error("地图 [{}] 不存在", start.getMapId());
            RcsLog.algorithmLog.error("地图 [{}] 不存在", start.getMapId());
            return new RouteResult(
                    isArrive,
                    0,
                    resultPoints
            );
        }

        // 创建自定义的 RcsPointEuclideanEstimator 实例
        AStarEstimator estimator = new AstarEstimator(graph);
        // 创建 AStarAlgorithm 实例
        AStarAlgorithm astar = new AstarSearch(
                agvCode,
                graph,
                start,
                goal,
                estimator,
                mapManager,
                slideTimeWindow,
                PATH_LENGTH_DISTANCE
        );

        // 执行算法
        Path pathResult = astar.findPath();
        // 处理结果
        if (pathResult != null && pathResult.vertices().length > 0) {
            for (Integer vertexId : pathResult.vertices()) {
                RcsPoint point = graph.getVertexLabel(vertexId);
                if (point != null) {
                    resultPoints.add(point);
                } else {
                    RcsLog.consoleLog.error("路径中点位 [{}] 获取不到", vertexId);
                    RcsLog.algorithmLog.error("路径中点位 [{}] 获取不到", vertexId);
                }
            }

            // 判断是否到达目标点
            if (resultPoints.contains(goal)) {
                isArrive = true;
            }
        } else {
            RcsLog.consoleLog.error("未在地图 [{}] 中找到从 [{}] 到 [{}] 的路径", start.getMapId(), start, goal);
            RcsLog.algorithmLog.error("未在地图 [{}] 中找到从 [{}] 到 [{}] 的路径", start.getMapId(), start, goal);
        }

        // 返回搜索结果，包括是否到达、路径代价和路径列表
        return new RouteResult(
                isArrive,
                pathResult != null ? pathResult.length() : 0,
                resultPoints
        );
    }
}
