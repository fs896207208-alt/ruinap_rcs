package com.ruinap.core.algorithm;

import com.ruinap.core.map.pojo.MapSnapshot;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.map.util.GeometryUtils;
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

/**
 * @author qianye
 * @create 2026-01-07 14:43
 */
@ExtendWith(MockitoExtension.class)
public class RcsAstarEstimatorTest {

    private Digraph<RcsPoint, RcsPointTarget> graph;
    private RcsAstarEstimator estimator;
    private static final double DELTA = 0.0001;

    @Mock
    private MapSnapshot mapSnapshot;

    @BeforeEach
    public void setUp() {
        // 1. 构建图
        graph = GraphBuilder.numVertices(5).buildDigraph();

        // 2. 注入点位数据 (注意：显式设置 ID，避免 MapKey 重复)

        // P0: 1楼 (0,0), ID=100
        graph.setVertexLabel(0, createPoint(100, 1, 0, 0));

        // P1: 1楼 (3000, 4000), ID=101 -> 确保与 P0 ID 不同
        graph.setVertexLabel(1, createPoint(101, 1, 3000, 4000));

        // P2: 2楼 (0,0), ID=200
        graph.setVertexLabel(2, createPoint(200, 2, 0, 0));

        // P3: 2楼 (3000, 4000), ID=201
        graph.setVertexLabel(3, createPoint(201, 2, 3000, 4000));

        // P4: 3楼 (0,0), ID=300
        graph.setVertexLabel(4, createPoint(300, 3, 0, 0));

        // 3. 实例化估值器
        estimator = new RcsAstarEstimator(graph);
    }

    @Test
    @DisplayName("同层寻路：应返回标准欧氏距离")
    public void testEstimate_SameFloor_Euclidean() {
        // P0(0,0) -> P1(3000,4000) = 5000
        double cost = estimator.estimate(0, 1);
        Assertions.assertEquals(5000.0, cost, DELTA, "同层应为标准欧氏距离");
    }

    @Test
    @DisplayName("跨层且坐标重叠：必须包含楼层惩罚")
    public void testEstimate_DiffFloor_SameCoord() {
        // P0(1楼) -> P2(2楼)
        double cost = estimator.estimate(0, 2);

        double expected = GeometryUtils.FLOOR_PENALTY;
        Assertions.assertEquals(expected, cost, DELTA, "跨层重叠点应包含楼层惩罚");
    }

    @Test
    @DisplayName("跨层且坐标不同：欧氏距离 + 楼层惩罚")
    public void testEstimate_DiffFloor_DiffCoord() {
        // P0(1楼) -> P3(2楼)
        double cost = estimator.estimate(0, 3);

        double expected = 5000.0 + GeometryUtils.FLOOR_PENALTY;
        Assertions.assertEquals(expected, cost, DELTA, "跨层移动应叠加计算");
    }

    @Test
    @DisplayName("跨多层楼：惩罚应倍增")
    public void testEstimate_MultiFloor_Jump() {
        // P0(1楼) -> P4(3楼)
        double cost = estimator.estimate(0, 4);

        double expected = GeometryUtils.FLOOR_PENALTY * 2;
        Assertions.assertEquals(expected, cost, DELTA, "跨多层应有多倍惩罚");
    }

    @Test
    @DisplayName("自身到自身：代价为0")
    public void testEstimate_Self() {
        double cost = estimator.estimate(1, 1);
        Assertions.assertEquals(0.0, cost, DELTA);
    }

    @Test
    @DisplayName("无效节点：应返回不可达")
    public void testEstimate_InvalidVertex() {
        double cost = estimator.estimate(0, 99);
        Assertions.assertEquals(Double.MAX_VALUE, cost, DELTA);
    }

    @Test
    @DisplayName("工厂方法：验证图引用传递")
    public void testFactoryMethod() {
        Mockito.when(mapSnapshot.graph()).thenReturn(graph);
        RcsAstarEstimator instance = new RcsAstarEstimator(mapSnapshot.graph());
        Assertions.assertNotNull(instance);
        Assertions.assertEquals(5000.0, instance.estimate(0, 1), DELTA);
    }

    // --- 辅助方法 ---

    /**
     * 创建 RcsPoint (设置合法 ID 以符合 MapKeyUtil 规范)
     */
    private RcsPoint createPoint(int id, int floor, int x, int y) {
        RcsPoint p = new RcsPoint();
        p.setId(id);        // [Critical Fix] 设置唯一 ID
        p.setMapId(floor);  // 简单模拟 MapId = Floor
        p.setFloor(floor);
        p.setX(x);
        p.setY(y);
        return p;
    }
}