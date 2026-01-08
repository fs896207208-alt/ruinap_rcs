package com.ruinap.core.algorithm;

import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.map.util.GeometryUtils;
import org.graph4j.Digraph;
import org.graph4j.shortestpath.AStarEstimator;

/**
 * Graph4j的 A*算法启发式估值类
 *
 * @author qianye
 * @create 2026-01-07 14:06
 */
public record RcsAstarEstimator(Digraph<RcsPoint, RcsPointTarget> graph) implements AStarEstimator {

    /**
     * 单层楼高度估算值 (单位: mm)
     * <p>注意：此值必须 < MapLoader中的桥接边权重(500,000)，否则 A* 不再保证最优解。</p>
     * <p>设为 100,000 意味着：算法认为跨一层楼至少相当于平面跑 100米。</p>
     */
    public static final double FLOOR_PENALTY = 100000.0;

    /**
     * 启发式估值函数 h(n)
     *
     * @param vertex 当前节点的 算法索引 (Graph Index, 0~N)
     * @param target 目标节点的 算法索引 (Graph Index, 0~N)
     * @return 预估代价
     */
    @Override
    public double estimate(int vertex, int target) {
        RcsPoint startNode;
        RcsPoint endNode;
        try {
            // 1. O(1) 获取点位对象
            startNode = graph.getVertexLabel(vertex);
            endNode = graph.getVertexLabel(target);
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
        // 防御性编程
        if (startNode == null || endNode == null) {
            return Double.MAX_VALUE;
        }

        // 2. 计算基础二维距离
        double dist = GeometryUtils.calculateDistance(startNode, endNode);

        // 3. 累加楼层高度差惩罚
        // 如果楼层不同，强制叠加高度代价，打破“终点就在脚下”的幻觉
        if (startNode.getFloor() != endNode.getFloor()) {
            int floorDiff = Math.abs(startNode.getFloor() - endNode.getFloor());
            dist += floorDiff * FLOOR_PENALTY;
        }
        return dist;
    }
}
