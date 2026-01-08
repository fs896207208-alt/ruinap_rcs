package com.ruinap.core.algorithm;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.map.util.MapKeyUtil;
import org.graph4j.Digraph;
import org.graph4j.GraphBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RcsAstarSearch 核心算法全方位测试 (增强版)
 * <p>
 * 覆盖场景：
 * 1. [基础] 常规最短路
 * 2. [避障] 占用避让
 * 3. [豁免] 自身豁免
 * 4. [权重] 动态拥堵避让
 * 5. [跨层] 同坐标垂直跨层 (Elevator Direct: 起终点 XY 相同)
 * 6. [跨层] 异坐标长途跨层 (Room A -> Elevator -> Room B: 起终点 XY 不同)
 */
@ExtendWith(MockitoExtension.class)
class RcsAstarSearchTest {

    private RcsAstarSearch rcsAstarSearchService;

    @Mock
    private MapManager mapManager;

    @Mock
    private SlideTimeWindow slideTimeWindow;

    private Digraph<RcsPoint, RcsPointTarget> graph;
    private RcsPoint p0, p1, p2, p3, p4;

    @BeforeEach
    void setUp() {
        // --- 1. 实例化 Service (模拟 Spring 容器行为) ---
        Objenesis objenesis = new ObjenesisStd();
        rcsAstarSearchService = objenesis.newInstance(RcsAstarSearch.class);

        // --- 2. 注入 Mock 依赖 ---
        ReflectUtil.setFieldValue(rcsAstarSearchService, "mapManager", mapManager);
        ReflectUtil.setFieldValue(rcsAstarSearchService, "slideTimeWindow", slideTimeWindow);

        // --- 3. 构建基础平面图 (用于前4个测试) ---
        // F1: 0 -> 1(短) -> 3 -> 4
        //     0 -> 2(长) -> 3
        graph = GraphBuilder.empty().estimatedNumVertices(20).buildDigraph();

        // 初始化基础点位 (ID, MapId, X, Y)
        p0 = createPoint(0, 1, 0, 0);
        p1 = createPoint(1, 1, 10, 0);
        p2 = createPoint(2, 1, 10, 10);
        p3 = createPoint(3, 1, 20, 0);
        p4 = createPoint(4, 1, 30, 0);

        addVertexToGraph(graph, 0, p0);
        addVertexToGraph(graph, 1, p1);
        addVertexToGraph(graph, 2, p2);
        addVertexToGraph(graph, 3, p3);
        addVertexToGraph(graph, 4, p4);

        graph.addEdge(0, 1, 10.0);
        graph.addEdge(1, 3, 10.0);
        graph.addEdge(0, 2, 20.0);
        graph.addEdge(2, 3, 20.0);
        graph.addEdge(3, 4, 5.0);

        // --- 4. 配置默认 Mock ---
        lenient().when(mapManager.getGraph()).thenReturn(graph);
        lenient().when(slideTimeWindow.costCalculation(anyDouble(), anyDouble()))
                .thenAnswer(inv -> ((Double) inv.getArgument(0)).intValue());
        lenient().when(mapManager.getPointOccupy(any(), anyInt()))
                .thenAnswer(inv -> createOccupy(false, null));
    }

    // ... (前4个基础测试用例保持不变) ...

    @Test
    @DisplayName("场景1：常规寻路 (0->1->3->4)")
    void testAStarSearch_Normal() {
        RouteResult result = rcsAstarSearchService.aStarSearch("AGV_001", p0, p4, false);
        Assertions.assertTrue(result.isArrive());
        // 注意：resultPoints 是 List<RcsPoint>
        Assertions.assertEquals(1, result.getPaths().get(1).getId());
    }

    @Test
    @DisplayName("场景2：占用避让 (P1被占，走P2)")
    void testAStarSearch_AvoidOccupy() {
        RcsPointOccupy blockedP1 = createOccupy(true, "AGV_OTHER");
        when(mapManager.getPointOccupy(any(), eq(1))).thenReturn(blockedP1);

        RouteResult result = rcsAstarSearchService.aStarSearch("AGV_SELF", p0, p4, true);

        boolean hasP1 = result.getPaths().stream().anyMatch(p -> p.getId() == 1);
        boolean hasP2 = result.getPaths().stream().anyMatch(p -> p.getId() == 2);
        Assertions.assertFalse(hasP1, "应避开P1");
        Assertions.assertTrue(hasP2, "应绕行P2");
    }

    @Test
    @DisplayName("场景3：自身豁免 (P1被自己占，走P1)")
    void testAStarSearch_SelfExemption() {
        RcsPointOccupy selfBlockedP1 = createOccupy(true, "AGV_SELF");
        when(mapManager.getPointOccupy(any(), eq(1))).thenReturn(selfBlockedP1);

        RouteResult result = rcsAstarSearchService.aStarSearch("AGV_SELF", p0, p4, true);
        Assertions.assertEquals(1, result.getPaths().get(1).getId());
    }

    @Test
    @DisplayName("场景4：动态拥堵 (P1权重高，走P2)")
    void testAStarSearch_DynamicWeight() {
        graph.setVertexWeight(1, 10.0); // P1 拥堵
        when(slideTimeWindow.costCalculation(anyDouble(), anyDouble()))
                .thenAnswer(inv -> {
                    Double dist = inv.getArgument(0);
                    Double weight = inv.getArgument(1);
                    return (weight != null && weight >= 5.0) ? dist.intValue() + 10000 : dist.intValue();
                });

        RouteResult result = rcsAstarSearchService.aStarSearch("AGV_001", p0, p4, false);
        boolean hasP1 = result.getPaths().stream().anyMatch(p -> p.getId() == 1);
        Assertions.assertFalse(hasP1, "应避开拥堵点 P1");
    }

    // ==================== 新增：跨楼层测试场景 ====================

    @Test
    @DisplayName("场景5：同坐标折返跨层 (关键修正)")
    void testCrossFloor_LoopBack() {
        // --- 场景重建 (模拟 MapLoader 行为) ---
        // 关键规则：GraphIndex 必须连续！
        // 我们重用 graph 对象，但要小心索引冲突。
        // 为安全起见，我们使用 10, 11, 12, 13 作为连续段（假设 estimatedNumVertices 够大）
        // 或者直接清空图重建（但在 Mockito @BeforeEach 里比较麻烦）。
        // 最稳妥的方式：直接接在 setUp 的 0~4 后面，使用 5, 6, 7, 8

        // 1. 定义点位 (GraphIndex 连续，BusinessID 重复)
        // F1 起点: GraphIndex=5, ID=0, MapId=1, (0,0)
        RcsPoint start = createPointWithIndex(5, 0, 1, 0, 0);

        // F1 电梯: GraphIndex=6, ID=2, MapId=1, (10,0)
        RcsPoint lift1 = createPointWithIndex(6, 2, 1, 10, 0);

        // F2 电梯: GraphIndex=7, ID=2, MapId=2, (10,0)
        RcsPoint lift2 = createPointWithIndex(7, 2, 2, 10, 0);

        // F2 终点: GraphIndex=8, ID=0, MapId=2, (0,0)
        RcsPoint end = createPointWithIndex(8, 0, 2, 0, 0);

        // 2. 加入图
        addVertexToGraph(graph, 5, start);
        addVertexToGraph(graph, 6, lift1);
        addVertexToGraph(graph, 7, lift2);
        addVertexToGraph(graph, 8, end);

        // 3. 连接边
        graph.addEdge(5, 6, 10.0);  // Start -> Lift1
        graph.addEdge(6, 7, 50.0);  // Lift1 -> Lift2 (跨层)
        graph.addEdge(7, 8, 10.0);  // Lift2 -> End

        // 4. Mock Map2 状态
        lenient().when(mapManager.getPointOccupy(eq(2), anyInt()))
                .thenAnswer(inv -> createOccupy(false, null));

        // --- 执行 ---
        // 你的 RcsAstarSearch 内部会使用 start.getGraphIndex()=5, end.getGraphIndex()=8
        RouteResult result = rcsAstarSearchService.aStarSearch("AGV_LOOP", start, end, false);

        // --- 验证 ---
        Assertions.assertTrue(result.isArrive(), "GraphIndex连续后，寻路应成功");
        List<RcsPoint> path = result.getPaths();
        System.out.println("✅ 修正后的折返路径: " + path);

        Assertions.assertEquals(4, path.size());

        // 验证业务ID顺序: 0 -> 2 -> 2 -> 0
        Assertions.assertEquals(0, path.get(0).getId());
        Assertions.assertEquals(2, path.get(1).getId());
        Assertions.assertEquals(2, path.get(2).getId());
        Assertions.assertEquals(0, path.get(3).getId());

        // 验证 MapId 切换: 1 -> 1 -> 2 -> 2
        Assertions.assertEquals(1, path.get(0).getMapId());
        Assertions.assertEquals(1, path.get(1).getMapId());
        Assertions.assertEquals(2, path.get(2).getMapId());
        Assertions.assertEquals(2, path.get(3).getMapId());
    }

    @Test
    @DisplayName("场景6：异坐标跨层")
    void testCrossFloor_DiffCoordinates() {
        // 使用连续索引 9, 10, 11, 12
        RcsPoint start = createPointWithIndex(9, 10, 1, 0, 0);
        RcsPoint lift1 = createPointWithIndex(10, 11, 1, 10, 0);
        RcsPoint lift2 = createPointWithIndex(11, 21, 2, 10, 0);
        RcsPoint end = createPointWithIndex(12, 22, 2, 20, 20);

        addVertexToGraph(graph, 9, start);
        addVertexToGraph(graph, 10, lift1);
        addVertexToGraph(graph, 11, lift2);
        addVertexToGraph(graph, 12, end);

        graph.addEdge(9, 10, 10.0);
        graph.addEdge(10, 11, 60.0);
        graph.addEdge(11, 12, 25.0);

        lenient().when(mapManager.getPointOccupy(eq(2), anyInt()))
                .thenAnswer(inv -> createOccupy(false, null));

        RouteResult result = rcsAstarSearchService.aStarSearch("AGV_TRAVEL", start, end, false);
        Assertions.assertTrue(result.isArrive());
        Assertions.assertEquals(4, result.getPaths().size());
        // 验证 MapId 切换
        Assertions.assertEquals(1, result.getPaths().get(1).getMapId());
        Assertions.assertEquals(2, result.getPaths().get(2).getMapId());
    }

    // ==================== 辅助方法 ====================

    private void addVertexToGraph(Digraph<RcsPoint, RcsPointTarget> g, int id, RcsPoint point) {
        g.addVertex(id);
        g.setVertexLabel(id, point);
    }

    private RcsPointOccupy createOccupy(boolean blocked, String occupierDeviceCode) {
        RcsPointOccupy occupy = new RcsPointOccupy();
        ReflectUtil.setFieldValue(occupy, "physicalBlocked", blocked);
        if (occupierDeviceCode != null) {
            Map<String, Set<PointOccupyTypeEnum>> map = occupy.getOccupants();
            Set<PointOccupyTypeEnum> types = new HashSet<>();
            PointOccupyTypeEnum mockEnum = Mockito.mock(PointOccupyTypeEnum.class);
            types.add(mockEnum);
            map.put(occupierDeviceCode, types);
        }
        return occupy;
    }

    /**
     * 创建带坐标的点位
     */
    private RcsPoint createPoint(int id, int mapId, int x, int y) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        // 使用 Graph Index 避免 ID 冲突
        p.setGraphIndex(Long.valueOf(MapKeyUtil.compositeKey(mapId, id)).intValue());
        p.setMapId(mapId);
        p.setFloor(mapId); // 假设 MapId = Floor
        p.setX(x);
        p.setY(y);
        return p;
    }

    // 核心：手动指定 GraphIndex
    private RcsPoint createPointWithIndex(int graphIndex, int id, int mapId, int x, int y) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        p.setGraphIndex(graphIndex); // 必须与 addVertex 的 index 一致
        p.setMapId(mapId);
        p.setFloor(mapId);
        p.setX(x);
        p.setY(y);
        return p;
    }

    private RcsPoint createPoint(int id) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        p.setMapId(1);
        p.setFloor(1);
        p.setX(0); // 初始化坐标，防止 GeometryUtils 报空指针
        p.setY(0);
        return p;
    }
}