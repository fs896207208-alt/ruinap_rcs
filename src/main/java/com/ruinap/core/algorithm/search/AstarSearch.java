package com.ruinap.core.algorithm.search;

import com.ruinap.core.algorithm.SlideTimeWindow;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.NeighborIterator;
import org.graph4j.shortestpath.AStarAlgorithm;
import org.graph4j.shortestpath.AStarEstimator;
import org.graph4j.util.VertexHeap;

import java.util.Arrays;

/**
 * Graph4J的A*算法
 *
 * @author qianye
 * @create 2025-06-30 17:00
 */
public class AstarSearch extends AStarAlgorithm {

    private final MapManager mapManager;
    private final SlideTimeWindow slideTimeWindow;
    private final int hardPenalty;

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
     * 构造函数
     *
     * @param agvCode   AGV编码
     * @param graph     图
     * @param start     开始顶点
     * @param goal      目标顶点
     * @param heuristic 启发式函数
     */
    public AstarSearch(String agvCode, Graph graph, RcsPoint start, RcsPoint goal,
                       AStarEstimator heuristic,
                       MapManager mapManager, SlideTimeWindow slideTimeWindow, int hardPenalty) {
        super(graph, start.getGraphIndex(), goal.getGraphIndex(), heuristic);
        this.agvCode = agvCode;
        this.heuristic = heuristic;
        this.mapManager = mapManager;
        this.slideTimeWindow = slideTimeWindow;
        this.hardPenalty = hardPenalty;
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

                // 声明硬性惩罚变量
                double extraPenalty = 0.0;
                // --- 动态获取当前邻居节点的 MapId ---
                // 因为 u 可能是另一层楼(另一个Map)的点，必须动态获取
                RcsPoint neighborPoint = (RcsPoint) this.graph.getVertexLabel(u);
                // 获取路径占用信息
                RcsPointOccupy pointOccupy = mapManager.getPointOccupy(neighborPoint.getMapId(), neighborPoint.getId());
                // 检查路径是否被占用且占用者不是当前 AGV
                if (pointOccupy != null && pointOccupy.isPhysicalBlocked()) {
                    if (!pointOccupy.getDeviceOccupyState(agvCode)) {
                        // 如果被占用且不是当前 AGV，则加上 CoreYaml配置 的绝对硬性惩罚值
                        extraPenalty = hardPenalty;
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
                    // 累加计算路径代价 = 历史代价 + 拥堵软权重(SlideTimeWindow) + 物理避让硬惩罚(extraPenalty)
                    double tentativeCost = this.cost[vi] + slideTimeWindow.costCalculation(weight, vertexWeight) + extraPenalty;
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
