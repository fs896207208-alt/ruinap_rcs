package com.ruinap.core.map.util;

import com.ruinap.core.map.enums.RcsCurveType;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointTarget;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.strtree.STRtree;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;

/**
 * @author qianye
 * @create 2025-12-31 16:54
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeometryUtils 几何工具类测试套件")
public class GeometryUtilsTest {

    private static MockedStatic<RcsCurveType> rcsCurveTypeMock;

    @BeforeAll
    public static void setup() {
        // 【关键修正】添加 CALLS_REAL_METHODS 参数
        // 这样 values()、valueOf() 等方法会走真实逻辑，避免 switch 语句崩溃
        rcsCurveTypeMock = Mockito.mockStatic(RcsCurveType.class, Mockito.CALLS_REAL_METHODS);

        // 依然拦截并重写 of() 方法的逻辑
        rcsCurveTypeMock.when(() -> RcsCurveType.of(anyInt())).thenAnswer(invocation -> {
            int type = invocation.getArgument(0);
            if (type == 2) return RcsCurveType.QUADRATIC_BEZIER;
            if (type == 3) return RcsCurveType.CUBIC_BEZIER;
            return RcsCurveType.STRAIGHT;
        });
    }

    @AfterAll
    public static void tearDown() {
        if (rcsCurveTypeMock != null) {
            rcsCurveTypeMock.close();
        }
    }

    // ================== 1. 基础距离计算测试 ==================

    @Test
    @DisplayName("测试两点间欧氏距离计算")
    void testCalculateDistance() {
        int dist = GeometryUtils.calculateDistance(0, 0, 3, 4);
        Assertions.assertEquals(5, dist, "欧氏距离计算错误 (勾股定理)");

        Assertions.assertEquals(0, GeometryUtils.calculateDistance(10, 10, 10, 10), "重合点距离应为0");

        RcsPoint p1 = createPoint(1, 0, 0);
        RcsPoint p2 = createPoint(2, 3, 4);
        Assertions.assertEquals(5, GeometryUtils.calculateDistance(p1, p2), "对象重载方法计算错误");
    }

    // ================== 2. 空间索引测试 ==================

    @Test
    @DisplayName("测试 STRtree 空间索引构建与查询")
    void testSpatialIndex() {
        RcsPoint p1 = createPoint(1, 0, 0);
        RcsPoint p2 = createPoint(2, 10, 10);
        RcsPoint p3 = createPoint(3, 20, 20);

        List<RcsPoint> points = Arrays.asList(p1, p2, p3);
        STRtree tree = GeometryUtils.buildSpatialIndex(points);

        // 查询范围覆盖 p1, p2
        List<RcsPoint> result = GeometryUtils.querySpatialIndex(tree, -1, -1, 15, 15);

        Assertions.assertAll("验证空间索引查询结果",
                () -> Assertions.assertNotNull(result),
                () -> Assertions.assertEquals(2, result.size()),
                () -> Assertions.assertTrue(result.contains(p1), "缺失点 p1"),
                () -> Assertions.assertTrue(result.contains(p2), "缺失点 p2"),
                () -> Assertions.assertFalse(result.contains(p3), "不应包含点 p3")
        );
    }

    // ================== 3. 几何构建测试 ==================

    @Test
    @DisplayName("测试直线几何生成")
    void testInitGeometry_Straight() {
        RcsPoint start = createPoint(1, 0, 0);
        RcsPoint end = createPoint(2, 10, 0);
        RcsPointTarget target = new RcsPointTarget();
        target.setType(1);

        GeometryUtils.initGeometry(start, end, target);

        Assertions.assertNotNull(target.getGeometry());
        Assertions.assertInstanceOf(LineString.class, target.getGeometry());
        Assertions.assertEquals(10.0, target.getGeometry().getLength(), 0.001);
    }

    @Test
    @DisplayName("测试二阶贝塞尔曲线生成")
    void testInitGeometry_QuadraticBezier() {
        RcsPoint start = createPoint(1, 0, 0);
        RcsPoint end = createPoint(2, 10, 0);
        RcsPointTarget target = new RcsPointTarget();
        target.setType(2);

        // 控制点 (5, 5)
        RcsPointTarget.ControlPoint ctl1 = new RcsPointTarget.ControlPoint();
        ctl1.setX(5);
        ctl1.setY(5);
        target.setCtl1(ctl1);

        GeometryUtils.initGeometry(start, end, target);

        Geometry geom = target.getGeometry();
        Assertions.assertNotNull(geom);
        Assertions.assertTrue(geom.getLength() > 10, "曲线长度应大于直线距离");
        // 验证动态采样逻辑
        Assertions.assertTrue(geom.getNumPoints() >= 10, "曲线采样点不足");
    }

    // ================== 4. 路径容差测试 ==================

    @Test
    @DisplayName("测试路径包围盒(AABB)与容差检测")
    void testIsPointWithinPathTolerance() {
        List<RcsPoint> points = Arrays.asList(
                createPoint(1, 0, 0),
                createPoint(2, 10, 0)
        );

        Assertions.assertAll("路径点位容差校验",
                // 在线段上
                () -> Assertions.assertTrue(GeometryUtils.isPointWithinPathTolerance(points, 1, 5, 0, null)),
                // 在容差边界
                () -> Assertions.assertTrue(GeometryUtils.isPointWithinPathTolerance(points, 1, 5, 1, null)),
                // 超出容差
                () -> Assertions.assertFalse(GeometryUtils.isPointWithinPathTolerance(points, 1, 5, 2, null)),
                // 在包围盒内但不在几何范围内
                () -> Assertions.assertFalse(GeometryUtils.isPointWithinPathTolerance(points, 1, 5, 5, null))
        );
    }

    // ================== 5. 安全避让测试 ==================

    @Test
    @DisplayName("测试死锁区域安全点过滤")
    void testFilterSafePoints() {
        // 死锁路径: (0,0) -> (10,0)
        List<RcsPoint> deadlockPath = Arrays.asList(
                createPoint(1, 0, 0),
                createPoint(2, 10, 0)
        );

        RcsPoint safePoint = createPoint(100, 5, 50); // y=50 (远)
        RcsPoint dangerPoint = createPoint(101, 5, 1); // y=1 (近)

        // 阈值: 1+1=2
        List<RcsPoint> result = GeometryUtils.filterSafePoints(
                deadlockPath,
                Arrays.asList(safePoint, dangerPoint),
                1, 1, null
        );

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.contains(safePoint));
        Assertions.assertFalse(result.contains(dangerPoint));
    }

    // ================== 辅助方法 ==================

    private RcsPoint createPoint(int id, int x, int y) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        p.setX(x);
        p.setY(y);
        return p;
    }
}