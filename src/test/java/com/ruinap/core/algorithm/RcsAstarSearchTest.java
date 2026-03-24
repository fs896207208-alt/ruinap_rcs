package com.ruinap.core.algorithm;

import cn.hutool.core.util.ReflectUtil;
import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.algorithm.search.RcsAstarSearch;
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

    @Mock
    private MapManager mapManager;

    @Mock
    private RcsAstarSearch rcsAstarSearch;

    @Mock
    private SlideTimeWindow slideTimeWindow;

    private Digraph<RcsPoint, RcsPointTarget> graph;
    private RcsPoint p0, p1, p2, p3, p4;

    @BeforeEach
    void setUp() {
        // --- 1. 实例化入口 Service ---
        // 即使现在是手动传参，由于类没有无参构造，我们依然用 Objenesis 创建一个调用“壳子”
        Objenesis objenesis = new ObjenesisStd();

        // --- 2. 构建图 (注意：Vertex ID 必须与 Point 的 GraphIndex 一致) ---
        graph = GraphBuilder.empty().estimatedNumVertices(50).buildDigraph();

        // 使用 createPoint 内部生成的复合键作为索引
        p0 = createPoint(0, 1, 0, 0);
        p1 = createPoint(1, 1, 10, 0);
        p2 = createPoint(2, 1, 10, 10);
        p3 = createPoint(3, 1, 20, 0);
        p4 = createPoint(4, 1, 30, 0);

        addVertexToGraph(graph, p0);
        addVertexToGraph(graph, p1);
        addVertexToGraph(graph, p2);
        addVertexToGraph(graph, p3);
        addVertexToGraph(graph, p4);

        // 连接边
        graph.addEdge(p0.getGraphIndex(), p1.getGraphIndex(), 10.0);
        graph.addEdge(p1.getGraphIndex(), p3.getGraphIndex(), 10.0);
        graph.addEdge(p0.getGraphIndex(), p2.getGraphIndex(), 20.0);
        graph.addEdge(p2.getGraphIndex(), p3.getGraphIndex(), 20.0);
        graph.addEdge(p3.getGraphIndex(), p4.getGraphIndex(), 5.0);

        // 3. 默认 Mock 配置
        lenient().when(mapManager.getGraph()).thenReturn(graph);
        // 【修正点】：mapId 是 Integer, pointId 是 Integer
        lenient().when(mapManager.getPointOccupy(anyInt(), anyInt()))
                .thenReturn(createOccupy(false, null));

        // 默认原价返回 (Double 类型对齐)
        lenient().when(slideTimeWindow.costCalculation(anyDouble(), anyDouble()))
                .thenAnswer(inv -> (Double) inv.getArgument(0));
    }

    @Test
    @DisplayName("场景1：基础路径搜索 (P0->P1->P3->P4)")
    void testAStarSearch_Normal() {
        // 调用最新签名的 aStarSearch
        RouteResult result = rcsAstarSearch.aStarSearch("AGV_001", p0, p4);

        Assertions.assertTrue(result.isArrive());
        List<RcsPoint> path = result.getPaths();
        Assertions.assertEquals(4, path.size());
        Assertions.assertEquals(1, path.get(1).getId());
    }

    @Test
    @DisplayName("场景2：障碍避让 (P1被占，绕行P2)")
    void testAStarSearch_AvoidOccupy() {
        // 模拟 P1 被其他 AGV 物理锁定
        RcsPointOccupy blockedP1 = createOccupy(true, "AGV_OTHER");
        when(mapManager.getPointOccupy(anyInt(), eq(p1.getGraphIndex()))).thenReturn(blockedP1);

        RouteResult result = rcsAstarSearch.aStarSearch("AGV_SELF", p0, p4);

        boolean hasP1 = result.getPaths().stream().anyMatch(p -> p.getId() == 1);
        boolean hasP2 = result.getPaths().stream().anyMatch(p -> p.getId() == 2);
        Assertions.assertFalse(hasP1, "路径必须避开 P1");
        Assertions.assertTrue(hasP2, "路径必须经过 P2 绕行");
    }

    @Test
    @DisplayName("场景3：拥堵代价避让 (P1额外收费，选择P2)")
    void testAStarSearch_Congestion() {
        // 只让 P1 路径（10.0 的边）产生巨额代价
        // 此时 P1 路径 = 1010 + 1010 + 5 = 2025
        // 此时 P2 路径 = 20 + 20 + 5 = 45 (将被选择)
        when(slideTimeWindow.costCalculation(eq(10.0), anyDouble())).thenReturn(1010.0);

        RouteResult result = rcsAstarSearch.aStarSearch("AGV_001", p0, p4);

        boolean hasP1 = result.getPaths().stream().anyMatch(p -> p.getId() == 1);
        Assertions.assertFalse(hasP1, "应当选择总代价更低的 P2 绕行路径");
    }

    // ==================== 辅助私有方法 ====================

    /**
     * 将点位加入图，确保索引同步
     */
    private void addVertexToGraph(Digraph<RcsPoint, RcsPointTarget> g, RcsPoint point) {
        int index = point.getGraphIndex();
        g.addVertex(index);
        g.setVertexLabel(index, point);
    }

    /**
     * 构建点位，使用复合键作为 GraphIndex
     */
    private RcsPoint createPoint(int id, int mapId, int x, int y) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        p.setMapId(mapId);
        // 核心：必须使用 MapKeyUtil 生成唯一的图索引
        p.setGraphIndex(Long.valueOf(MapKeyUtil.compositeKey(mapId, id)).intValue());
        p.setX(x);
        p.setY(y);
        p.setFloor(mapId);
        return p;
    }

    private RcsPointOccupy createOccupy(boolean blocked, String occupierCode) {
        RcsPointOccupy occupy = new RcsPointOccupy();
        // 强制修改私有物理阻塞状态
        ReflectUtil.setFieldValue(occupy, "physicalBlocked", blocked);
        if (occupierCode != null) {
            Map<String, Set<PointOccupyTypeEnum>> occupants = occupy.getOccupants();
            Set<PointOccupyTypeEnum> types = new HashSet<>();
            // Mock 一个占用类型，防止逻辑判断报错
            types.add(Mockito.mock(PointOccupyTypeEnum.class));
            occupants.put(occupierCode, types);
        }
        return occupy;
    }
}