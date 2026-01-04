package com.ruinap.core.map.pojo;

import com.ruinap.core.map.util.MapKeyUtil;
import lombok.Builder;
import org.graph4j.Digraph;
import org.graph4j.GraphBuilder;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <h1>不可变地图快照 (全量版)</h1>
 * <p>
 * <strong>核心职责：</strong>
 * 1. 存储全量地图拓扑结构 (Graph, Points)。
 * 2. 存储所有业务规则数据 (管制、避让、充电、待机)，无论数据来自 JSON 还是 YAML，最终都汇聚于此。
 * </p>
 *
 * @author qianye
 * @create 2025-12-23 16:02
 */
@Builder
public record MapSnapshot(
        // --- 基本数据 ---
        // 1. 核心图
        Digraph<RcsPoint, RcsPointTarget> graph,
        // 2. 入口索引 (业务ID -> 算法ID)
        Map<String, Integer> pointKeyToGraphId,
        // 3. 业务索引
        Map<String, RcsPoint> pointMap,
        // 4. 版本号
        Map<Integer, String> versionMd5,
        // 5. 点位占用集合
        // Key: 地图编号_点位编号
        Map<String, RcsPointOccupy> occupys,

        // 6. 空间索引 (JTS STRtree)
        // Key: 地图编号, Value: 只读的 R-Tree 索引
        Map<Integer, STRtree> spatialIndexes,

        // --- 业务规则数据 (Merge JSON & YAML) ---

        /** 充电点: MapId -> List<Point> */
        Map<Integer, List<RcsPoint>> chargePoints,

        /** 待机点: MapId -> List<Point> */
        Map<Integer, List<RcsPoint>> standbyPoints,

        /** 待机屏蔽点: MapId -> List<Point> */
        Map<Integer, List<RcsPoint>> standbyShieldPoints,

        /** * 管制区: MapId -> (AreaCode -> List<Point>)
         * e.g. 1号图 -> {"AreaA": [P1, P2], "AreaB": [P3]}
         */
        Map<Integer, Map<String, List<RcsPoint>>> controlAreas,

        /** * 管制点: MapId -> (TriggerPoint -> List<BlockedPoint>)
         * e.g. 当车压在 Key 点时，Value 列表中的点不可用
         */
        Map<Integer, Map<RcsPoint, List<RcsPoint>>> controlPoints,

        /** * 避让点: MapId -> (AvoidCode -> List<Point>)
         */
        Map<Integer, Map<String, List<RcsPoint>>> avoidancePoints,

        /** * 动作参数绑定: MapId -> (ParamKey -> Point)
         */
        Map<Integer, Map<String, RcsPoint>> actionParamMap
) {
    /**
     * <h2>创建一个空的快照</h2>
     * <p>通常用于系统初始化或加载失败时的兜底对象，避免 NullPointerException。</p>
     */
    public static MapSnapshot empty() {
        // 泛型安全的空图
        Digraph<RcsPoint, RcsPointTarget> g = GraphBuilder.empty().buildDigraph();
        return MapSnapshot.builder()
                .graph(g)
                .pointMap(Collections.emptyMap())
                .versionMd5(Collections.emptyMap())
                .occupys(Collections.emptyMap())
                .spatialIndexes(Collections.emptyMap())
                .chargePoints(Collections.emptyMap())
                .standbyPoints(Collections.emptyMap())
                .standbyShieldPoints(Collections.emptyMap())
                .controlAreas(Collections.emptyMap())
                .controlPoints(Collections.emptyMap())
                .avoidancePoints(Collections.emptyMap())
                .actionParamMap(Collections.emptyMap())
                .build();
    }

    /**
     * <h2>业务查询：根据 MapID 和 PointID 获取点位</h2>
     * <p>
     * 自动处理组合键逻辑，解决 ID 冲突问题。
     * 时间复杂度: O(1)
     * </p>
     *
     * @param mapId   地图编号
     * @param pointId 点位编号
     * @return 对应的点位对象，如果不存在则返回 null
     */
    public RcsPoint getPoint(Integer mapId, Integer pointId) {
        if (mapId == null || pointId == null) {
            return null;
        }
        return pointMap.get(MapKeyUtil.compositeKey(mapId, pointId));
    }

    /**
     * <h2>获取两点之间的边数据</h2>
     * <p>查询从点 A 到点 B 的详细行驶参数（曲线、速度等）。</p>
     *
     * @param fromMapId   起点地图ID
     * @param fromPointId 起点点位ID
     * @param toMapId     终点地图ID (通常与起点相同，除非是跨层桥接)
     * @param toPointId   终点点位ID
     */
    public RcsPointTarget getEdgeData(Integer fromMapId, Integer fromPointId, Integer toMapId, Integer toPointId) {
        // 1. 【入口转换】获取起点和终点的 图算法ID (O(1))
        Integer u = getGraphId(fromMapId, fromPointId);
        Integer v = getGraphId(toMapId, toPointId);

        // 2. 【图内查询】如果两点都在图中，直接获取边 Label (O(1) ~ O(d))
        if (u != null && v != null) {
            // graph.getEdgeLabel(u, v) 会返回我们在 MapLoader 里塞进去的 RcsPointTarget 对象
            // 如果两点之间没有边，Graph4J 通常会返回 null
            return graph.getEdgeLabel(u, v);
        }

        return null;
    }

    /**
     * <h2>算法查询：根据图顶点 ID 获取点位</h2>
     * <p>
     * 专供 {@code Heuristic} 启发式函数使用。
     * 在 A* 算法中，需要根据顶点 ID 反查物理坐标 (x, y, mapId) 来计算预估距离。
     * </p>
     *
     * @param graphId Graph4J 分配的唯一整数顶点 ID
     * @return 对应的点位对象
     */
    public RcsPoint getPointByGraphId(int graphId) {
        return graph.getVertexLabel(graphId);
    }

    /**
     * <h2>算法查询：根据业务 ID 获取图顶点 ID</h2>
     * <p>
     * 用于将业务上的起终点 (如 "去 101号地图的 5号点") 转换为图算法的起点/终点 ID。
     * </p>
     *
     * @param mapId   地图编号
     * @param pointId 点位编号
     * @return 图顶点 ID，如果点位不存在返回 null
     */
    public Integer getGraphId(Integer mapId, Integer pointId) {
        if (mapId == null || pointId == null) {
            return null;
        }
        return pointKeyToGraphId.get(MapKeyUtil.compositeKey(mapId, pointId));
    }

    /**
     * <h2>获取特定地图的 MD5 版本号</h2>
     * <p>用于校验 AGV 上报的地图版本是否与调度系统一致。</p>
     */
    public String getMd5(Integer mapId) {
        return versionMd5.get(mapId);
    }
}
