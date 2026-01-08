package com.ruinap.core.algorithm.domain;

import com.ruinap.core.map.pojo.RcsPoint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 路径规划返回类
 *
 * @author qianye
 * @create 2024-07-18 14:46
 */
@AllArgsConstructor
public class RouteResult {
    /**
     * 是否规划到终点
     */
    @Setter
    @Getter
    private volatile boolean isArrive;

    /**
     * 路径代价
     */
    @Setter
    @Getter
    private volatile int pathCost;
    /**
     * 路径集合
     */
    @Setter
    @Getter
    private volatile List<RcsPoint> paths;

    /**
     * A*算法的节点类，用于存储当前节点的信息和路径信息
     */
    public static class Node implements Comparable<Node> {
        /**
         * 当前节点
         */
        public RcsPoint point;
        /**
         * 从起点到当前点的实际代价
         */
        public int costSoFar;
        /**
         * 总代价（实际代价 + 启发式估计）
         */
        int totalCost;

        /**
         * 构造函数
         *
         * @param point     当前节点
         * @param costSoFar 从起点到当前点的实际代价
         * @param totalCost 总代价
         */
        public Node(RcsPoint point, int costSoFar, int totalCost) {
            this.point = point;
            this.costSoFar = costSoFar;
            this.totalCost = totalCost;
        }

        /**
         * 比较两个节点，用于排序
         *
         * @param other 另一个节点
         * @return 结果
         */
        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.totalCost, other.totalCost);
        }
    }
}
