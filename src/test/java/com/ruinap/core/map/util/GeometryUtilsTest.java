package com.ruinap.core.map.util;

import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointTarget;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author qianye
 * @create 2025-12-31 16:54
 */
public class GeometryUtilsTest {

    private final GeometryFactory factory = new GeometryFactory();

    // ================= 1. 基础距离检测测试 =================

    @Test
    public void testCheckCollision_PointToPoint() {
        Geometry p1 = factory.createPoint(new Coordinate(0, 0));
        Geometry p2 = factory.createPoint(new Coordinate(1000, 0));

        // 阈值 999 -> 安全
        Assert.assertFalse(GeometryUtils.checkCollision(p1, p2, 999));
        // 阈值 1000 -> 碰撞
        Assert.assertTrue(GeometryUtils.checkCollision(p1, p2, 1000));
        // 阈值 1001 -> 碰撞
        Assert.assertTrue(GeometryUtils.checkCollision(p1, p2, 1001));
    }

    @Test
    public void testCheckCollision_LineToPoint() {
        Geometry line = factory.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10000, 0)
        });

        Geometry pA = factory.createPoint(new Coordinate(5000, 400));
        // 阈值 399 -> 安全
        Assert.assertFalse(GeometryUtils.checkCollision(line, pA, 399));
        // 阈值 401 -> 碰撞
        Assert.assertTrue(GeometryUtils.checkCollision(line, pA, 401));

        Geometry pB = factory.createPoint(new Coordinate(-500, 0));
        // 阈值 499 -> 安全
        Assert.assertFalse(GeometryUtils.checkCollision(line, pB, 499));
        // 阈值 501 -> 碰撞
        Assert.assertTrue(GeometryUtils.checkCollision(line, pB, 501));
    }

    // ================= 2. 贝塞尔曲线测试 =================

    @Test
    public void testBezierPathConstruction() {
        List<RcsPoint> points = new ArrayList<>();
        points.add(createPoint(1, 0, 0));
        points.add(createPoint(2, 1000, 1000));

        GeometryUtils.EdgeProvider provider = (u, v) -> {
            RcsPointTarget target = new RcsPointTarget();
            target.setType(2);
            RcsPointTarget.ControlPoint ctl = new RcsPointTarget.ControlPoint();
            ctl.setX(1000);
            ctl.setY(0);
            target.setCtl1(ctl);
            return target;
        };

        Geometry geom = GeometryUtils.buildPathGeometry(points, provider);
        Assert.assertNotNull(geom);
        Assert.assertTrue("贝塞尔曲线应该包含多个插值点", geom.getNumPoints() > 10);

        // 【修正】使用准确的 t=0.5 时的理论坐标 (750, 250)
        // 原坐标 (500, 250) 距离曲线约 149mm，会导致断言失败
        Geometry testPoint = factory.createPoint(new Coordinate(750, 250));

        double dist = geom.distance(testPoint);
        System.out.println("贝塞尔曲线中间点距离: " + dist);

        // 修正后，dist 应该非常接近 0 (由于离散化采样，可能会有微小误差，但肯定小于 1)
        Assert.assertTrue("测试点应贴近曲线", dist < 10);
    }

    // ================= 3. filterSafePoints 业务逻辑测试 =================

    @Test
    public void testFilterSafePoints() {
        List<RcsPoint> deadlockPath = new ArrayList<>();
        deadlockPath.add(createPoint(1, 0, 0));
        deadlockPath.add(createPoint(2, 10000, 0));

        List<RcsPoint> idlePoints = new ArrayList<>();
        RcsPoint safeP = createPoint(101, 5000, 2000);
        RcsPoint unsafeP = createPoint(102, 5000, 800);
        idlePoints.add(safeP);
        idlePoints.add(unsafeP);

        // 使用 lambda 需要 JDK 8+，JUnit 4 支持良好
        List<RcsPoint> result = GeometryUtils.filterSafePoints(
                deadlockPath, idlePoints, 500, 500, (u, v) -> null
        );

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(101, (int) result.get(0).getId());
    }

    private RcsPoint createPoint(int id, int x, int y) {
        RcsPoint p = new RcsPoint();
        p.setId(id);
        p.setX(x);
        p.setY(y);
        p.setMapId(1);
        return p;
    }
}
