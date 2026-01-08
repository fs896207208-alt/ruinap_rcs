package com.ruinap.core.algorithm;

import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.log.RcsLog;
import org.graph4j.Digraph;
import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.NeighborIterator;
import org.graph4j.shortestpath.AStarAlgorithm;
import org.graph4j.shortestpath.AStarEstimator;
import org.graph4j.util.Path;
import org.graph4j.util.VertexHeap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Graph4J的A*算法实现
 *
 * @author qianye
 * @create 2025-06-30 17:00
 */
public class RcsAstarSearch extends AStarAlgorithm {

    @Autowired
    private MapManager mapManager;
    @Autowired
    private SlideTimeWindow slideTimeWindow;
    /**
     * AStarAlgorithm内部属性，因为父类中属性是private，所以只能重新定义
     */
    private VertexHeap heap;
    private final AStarEstimator heuristic;

    /**
     * AGV编码
     */
    private final String agvCode;
    /**
     * 是否考虑占用
     */
    private final boolean considerOccupy;

    /**
     * 构造函数
     *
     * @param agvCode        AGV编码
     * @param graph          图
     * @param start          开始顶点
     * @param goal           目标顶点
     * @param heuristic      启发式函数
     * @param considerOccupy 是否考虑占用
     */
    public RcsAstarSearch(String agvCode, Graph graph, RcsPoint start, RcsPoint goal,
                          AStarEstimator heuristic, boolean considerOccupy,
                          MapManager mapManager, SlideTimeWindow slideTimeWindow) {
        super(graph, start.getGraphIndex(), goal.getGraphIndex(), heuristic);
        this.agvCode = agvCode;
        this.heuristic = heuristic;
        this.considerOccupy = considerOccupy;
        this.mapManager = mapManager;
        this.slideTimeWindow = slideTimeWindow;
    }

    /**
     * 使用A*算法进行从起点到目标点的路径搜索
     * 如果考虑路径被占用，则可能会返回绕路路径列表
     *
     * @param agvCode        AGV编号
     * @param start          起点
     * @param goal           目标点
     * @param considerOccupy 是否考虑路径被占用（如果传入false，则可不传入agvCode）
     * @return 返回结果，包括是否到达目标点、路径代价和路径
     */
    public RouteResult aStarSearch(String agvCode, RcsPoint start, RcsPoint goal, boolean considerOccupy) {
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
        AStarEstimator estimator = new RcsAstarEstimator(graph);
        // 创建 AStarAlgorithm 实例
        AStarAlgorithm astar = new RcsAstarSearch(
                agvCode,
                graph,
                start,
                goal,
                estimator,
                considerOccupy,
                this.mapManager,
                this.slideTimeWindow
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

    @Override
    protected void compute() {
        int n = this.vertices.length;
        this.cost = new double[n];
        this.before = new int[n];
        this.size = new int[n];
        this.solved = new boolean[n];
        this.numSolved = 0;
        Arrays.fill(this.cost, Double.POSITIVE_INFINITY);
        Arrays.fill(this.before, -1);
        this.cost[this.graph.indexOf(this.source)] = (double) 0.0F;
        this.heap = new VertexHeap(this.graph, (i, j) -> (int) Math.signum(this.cost[i] + this.heuristic.estimate(i, this.target) - this.cost[j] - this.heuristic.estimate(j, this.target)));

        while (true) {
            int vi = this.heap.poll();
            this.solved[vi] = true;
            ++this.numSolved;
            int v = this.vertices[vi];
            if (v == this.target || this.numSolved == n) {
                return;
            }

            NeighborIterator it = this.graph.neighborIterator(v);
            while (it.hasNext()) {
                int u = it.next();
                int ui = this.graph.indexOf(u);

                // --- 自定义逻辑开始 ---
                if (considerOccupy) {
                    // --- 动态获取当前邻居节点的 MapId ---
                    // 因为 u 可能是另一层楼(另一个Map)的点，必须动态获取
                    RcsPoint neighborPoint = (RcsPoint) this.graph.getVertexLabel(u);
                    // 获取路径占用信息
                    RcsPointOccupy pointOccupy = mapManager.getPointOccupy(neighborPoint.getMapId(), u);
                    // 检查路径是否被占用且占用者不是当前 AGV
                    if (pointOccupy != null && pointOccupy.isPhysicalBlocked()) {
                        if (pointOccupy.getDeviceOccupyState(agvCode)) {
                            // 如果被占用且不是当前 AGV，则跳过此邻居
                            continue;
                        }
                    }
                }
                // --- 自定义逻辑结束 ---

                if (!this.solved[ui]) {
                    double weight = it.getEdgeWeight();
                    if (weight < (double) 0.0F) {
                        Edge var10002 = this.graph.edge(v, u);
                        throw new IllegalArgumentException("不允许使用负加权边: " + var10002);
                    }

                    // 计算成本
                    double vertexWeight = this.graph.getVertexWeight(u);

                    // --- 自定义逻辑开始 ---
                    // 累加计算路径代价
                    double tentativeCost = this.cost[vi] + slideTimeWindow.costCalculation(weight, vertexWeight);
                    // --- 自定义逻辑结束 ---

                    if (this.cost[ui] > tentativeCost) {
                        this.cost[ui] = tentativeCost;
                        this.before[ui] = vi;
                        this.size[ui] = this.size[vi] + 1;
                        this.heap.update(ui);
                    }
                }
            }
        }
    }
}
