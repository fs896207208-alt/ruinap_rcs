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

        // 2. 计算距离
        double dist = GeometryUtils.calculateDistance(startNode, endNode);
        return dist;
    }
}
