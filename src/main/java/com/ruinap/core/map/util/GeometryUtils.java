package com.ruinap.core.map.util;

import com.ruinap.core.map.enums.RcsCurveType;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointTarget;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 几何计算工具类
 * <p>
 * 坐标系单位：毫米(mm)
 * 默认使用欧式距离
 * <p>
 * 职责：提供基于 JTS 的几何计算服务，如构建索引、距离计算、碰撞检测。
 * </p>
 *
 * @author qianye
 * @create 2025-12-31 09:27
 */
public class GeometryUtils {

    /**
     * [Perf] JTS 的 GeometryFactory 是线程安全的。
     * 全局复用单例，避免了每次计算都 new Factory 的开销。
     * PrecisionModel.FLOATING 使用双精度浮点，虽然比 FIXED 稍慢但在现代 CPU 上差异可忽略，且精度更高。
     */
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));

    /**
     * 边属性提供者接口
     * [Perf] 使用 FunctionalInterface 允许 Lambda 表达式传参。
     * 在 HotSpot JVM 中，Lambda 调用会被优化为 invokedynamic，性能开销极低，几乎等同于直接调用。
     */
    @FunctionalInterface
    public interface EdgeProvider extends BiFunction<Integer, Integer, RcsPointTarget> {
    }

    // ================== 1. 空间索引相关 ==================

    /**
     * 构建空间索引 (Write-Once)
     * [Perf] STRtree 构建是非线程安全的，但构建完成后用于查询是线程安全的。
     * 通常在 MapLoader 中单线程构建，构建后变为只读。
     */
    public static STRtree buildSpatialIndex(List<RcsPoint> points) {
        // 初始容量设为默认即可，STRtree 会自动调整
        STRtree spatialIndex = new STRtree();
        for (RcsPoint point : points) {
            // Envelope 对象非常轻量（只包含4个double），在栈上分配或快速回收
            Envelope env = new Envelope(point.getX(), point.getX(), point.getY(), point.getY());
            spatialIndex.insert(env, point);
        }
        // [Critical] build() 会对索引节点进行排序和打包，极大提升后续 query() 的性能。
        spatialIndex.build();
        return spatialIndex;
    }

    /**
     * 初始化路段几何信息
     * 该方法应在地图解析/加载阶段调用，将计算好的 Geometry 注入到 target 中
     *
     * @param start  起点对象
     * @param end    终点对象
     * @param target 连接关系对象 (Geometry 将被注入到这里)
     */
    public static void initGeometry(RcsPoint start, RcsPoint end, RcsPointTarget target) {
        if (target == null || start == null || end == null) {
            return;
        }

        // 1. 消除魔法数字 (假设你已经有了 RcsCurveType 枚举)
        var type = RcsCurveType.of(target.getType());
        Geometry geom = null;

        // 2. 根据类型构建几何
        switch (type) {
            case QUADRATIC_BEZIER:
            case QUADRATIC_BEZIER_CONNECT:
                if (target.getCtl1() != null) {
                    geom = createQuadraticBezierGeometry(start, end, target.getCtl1());
                }
                break;
            case CUBIC_BEZIER:
            case CUBIC_BEZIER_CONNECT:
                if (target.getCtl1() != null && target.getCtl2() != null) {
                    geom = createCubicBezierGeometry(start, end, target.getCtl1(), target.getCtl2());
                }
                break;
            default:
                // 这里不写，由下面判断条件兜底
                break;
        }

        // 兜底：如果是直线或构建失败，生成直线
        if (geom == null) {
            geom = createStraightLine(start, end);
        }

        // 3. 【核心】注入到 Target 对象中
        target.setGeometry(geom);
    }

    /**
     * 空间查询 (Read-Many)
     * [Perf] 时间复杂度 O(log N)。这是极其高效的查询方式。
     */
    @SuppressWarnings("unchecked")
    public static List<RcsPoint> querySpatialIndex(STRtree tree, double minX, double minY, double maxX, double maxY) {
        if (tree == null) {
            // JDK 9+ 不可变空列表，零开销
            return List.of();
        }
        Envelope queryEnv = new Envelope(minX, maxX, minY, maxY);
        return (List<RcsPoint>) tree.query(queryEnv);
    }

    // ================== 2. 基础距离与成本计算 ==================

    /**
     * 计算两点欧氏距离 (纯 CPU 计算)
     * [Perf] 极致性能优化：
     * 1. 避免了任何对象创建 (No Allocation)。
     * 2. Math.sqrt 和 Math.pow 会被 JIT 编译为 CPU 原语指令 (Intrinsic)，纳秒级响应。
     */
    public static int calculateDistance(int x1, int y1, int x2, int y2) {
        if (x1 == x2 && y1 == y2) {
            return 0;
        }
        return (int) Math.round(Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)));
    }

    /**
     * 计算两点欧氏距离 (纯 CPU 计算)
     * [Perf] 极致性能优化：
     * 1. 避免了任何对象创建 (No Allocation)。
     * 2. Math.sqrt 和 Math.pow 会被 JIT 编译为 CPU 原语指令 (Intrinsic)，纳秒级响应。
     */
    public static int calculateDistance(RcsPoint p1, RcsPoint p2) {
        return calculateDistance(p1.getX(), p1.getY(), p2.getX(), p2.getY());
    }

    /**
     * 计算两点成本
     *
     * @param current 当前点
     * @param next    下一点
     * @param target  目标对象
     * @return 成本
     */
    public static int calculateCost(RcsPoint current, RcsPoint next, RcsPointTarget target) {
        if (current.equals(next)) {
            return 0;
        }
        int cost = Integer.MAX_VALUE;
        if (target != null) {
            int distance = target.getDistance();
            if (distance > 0) {
                cost = distance;
            }
        } else {
            // 异常构建涉及字符串拼接，仅在异常路径执行，不影响正常路径性能
            throw new IllegalArgumentException(String.format("缺失属性: %d -> %d", current.getId(), next.getId()));
        }
        return cost;
    }

    // ================== 3. 路径分析 (高频热点) ==================

    /**
     * 计算路径总长度
     * [Perf] 线性遍历 O(N)。主要开销在于贝塞尔曲线长度计算（涉及几何构建）。
     * 优化点：对于直线段，直接走 calculateDistance (纯数学)，避免 JTS Geometry 构建。
     */
    public static int calculatePathLength(List<RcsPoint> points, EdgeProvider edgeProvider) {
        if (points == null || points.size() < 2) {
            return 0;
        }

        double totalDistance = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            RcsPoint start = points.get(i);
            RcsPoint end = points.get(i + 1);
            RcsPointTarget target = (edgeProvider != null) ? edgeProvider.apply(start.getId(), end.getId()) : null;

            if (target != null) {
                // 优先读取缓存几何的长度
                if (target.getGeometry() != null) {
                    totalDistance += target.getGeometry().getLength();
                } else {
                    // 缓存未命中（极少情况），回退到动态计算
                    // 注意：这里简化处理，如果没有缓存几何，可能是直线，直接算欧氏距离最快
                    totalDistance += calculateDistance(start, end);
                }
            } else {
                // 没有 Target，即逻辑上的直线连接
                totalDistance += calculateDistance(start, end);
            }
        }
        return (int) Math.round(totalDistance);
    }

    /**
     * 计算路径的扩展边界框 (AABB)
     * [Perf] 这是一个纯整数运算方法，没有对象分配 (Zero Allocation except result array)。
     * 它是所有复杂几何检测的前置过滤器，性能至关重要。
     */
    public static int[] calculatePathBoundingBox(List<RcsPoint> points, int tolerance, EdgeProvider edgeProvider) {
        if (points == null || points.isEmpty() || tolerance < 0) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        // O(N) 遍历，纯数值比较
        for (int i = 0; i < points.size() - 1; i++) {
            RcsPoint curr = points.get(i);
            RcsPoint next = points.get(i + 1);

            // 1. 基础端点扩展
            minX = Math.min(minX, Math.min(curr.getX(), next.getX()));
            maxX = Math.max(maxX, Math.max(curr.getX(), next.getX()));
            minY = Math.min(minY, Math.min(curr.getY(), next.getY()));
            maxY = Math.max(maxY, Math.max(curr.getY(), next.getY()));

            // 2. 控制点扩展 (防止贝塞尔曲线拱起部分超出端点连线范围)
            // lambda 调用会有极小的虚方法调用开销，可忽略
            RcsPointTarget target = (edgeProvider != null) ? edgeProvider.apply(curr.getId(), next.getId()) : null;
            if (target != null) {
                int type = target.getType();
                if ((type == 2 || type == 5) && target.getCtl1() != null) {
                    minX = Math.min(minX, target.getCtl1().getX());
                    maxX = Math.max(maxX, target.getCtl1().getX());
                    minY = Math.min(minY, target.getCtl1().getY());
                    maxY = Math.max(maxY, target.getCtl1().getY());
                } else if ((type == 3 || type == 6) && target.getCtl1() != null && target.getCtl2() != null) {
                    minX = Math.min(minX, Math.min(target.getCtl1().getX(), target.getCtl2().getX()));
                    maxX = Math.max(maxX, Math.max(target.getCtl1().getX(), target.getCtl2().getX()));
                    minY = Math.min(minY, Math.min(target.getCtl1().getY(), target.getCtl2().getY()));
                    maxY = Math.max(maxY, Math.max(target.getCtl1().getY(), target.getCtl2().getY()));
                }
            }
        }

        if (points.size() == 1) {
            RcsPoint p = points.getFirst();
            minX = maxX = p.getX();
            minY = maxY = p.getY();
        }

        return new int[]{minX - tolerance, minY - tolerance, maxX + tolerance, maxY + tolerance};
    }

    /**
     * 判断点是否在路径容差范围内 (核心热点方法)
     * [Perf] 采用两级检测策略：
     * Level 1: 包围盒粗筛 (Int Math)。拦截 99% 的无效计算。
     * Level 2: 几何精算 (JTS Math)。仅在点位于包围盒内时才执行。
     */
    public static boolean isPointWithinPathTolerance(List<RcsPoint> points, int tolerance, int x, int y, EdgeProvider edgeProvider) {
        if (points == null || points.isEmpty()) {
            return false;
        }

        // 1. [Critical Optimization] 包围盒粗筛
        // 如果点连大框都不在，绝对不需要进行复杂的几何计算
        int[] bbox = calculatePathBoundingBox(points, tolerance, edgeProvider);
        if (!isPointWithinBoundingBox(bbox, x, y)) {
            return false;
        }

        // 2. 几何精确计算
        // 创建 Point 对象，会有堆内存分配，但在粗筛之后频率已大幅降低
        Point checkPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));

        if (points.size() == 1) {
            RcsPoint p = points.getFirst();
            return calculateDistance(x, y, p.getX(), p.getY()) <= tolerance;
        }

        // 逐段检测，一旦满足立即返回 (Short-Circuit)
        for (int i = 0; i < points.size() - 1; i++) {
            RcsPoint start = points.get(i);
            RcsPoint end = points.get(i + 1);
            RcsPointTarget target = (edgeProvider != null) ? edgeProvider.apply(start.getId(), end.getId()) : null;

            Geometry segmentGeom;

            // 优先获取缓存对象
            if (target != null && target.getGeometry() != null) {
                segmentGeom = target.getGeometry();
            }
            // 兜底策略：如果缓存不存在或 Target 缺失，视为直线
            else if (target != null) {
                segmentGeom = buildSingleSegment(start, end, target);
            } else {
                segmentGeom = createStraightLine(start, end);
            }

            if (segmentGeom != null && checkPoint.distance(segmentGeom) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    // ================== 4. 几何构建 (Heap Allocation Heavy) ==================

    /**
     * 构建整条路径的几何对象
     * [Perf] 此方法会创建大量对象 (LineString, Coordinate, MultiLineString)。
     * 建议仅在生成缓存（如 TrafficService 的缓冲区）时调用，不要在每帧检测中调用。
     */
    public static Geometry buildPathGeometry(List<RcsPoint> points, EdgeProvider edgeProvider) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        if (points.size() == 1) {
            RcsPoint p = points.getFirst();
            return GEOMETRY_FACTORY.createPoint(new Coordinate(p.getX(), p.getY()));
        }

        List<LineString> pathSegments = new ArrayList<>(points.size() - 1);
        for (int i = 0; i < points.size() - 1; i++) {
            RcsPoint start = points.get(i);
            RcsPoint end = points.get(i + 1);

            RcsPointTarget target = (edgeProvider != null) ? edgeProvider.apply(start.getId(), end.getId()) : null;
            // 优先构建直线，减少贝塞尔计算开销
            LineString segment = (target == null) ? createStraightLine(start, end) : buildSingleSegment(start, end, target);

            if (segment != null) {
                pathSegments.add(segment);
            }
        }
        return GEOMETRY_FACTORY.createMultiLineString(pathSegments.toArray(new LineString[0]));
    }

    private static LineString buildSingleSegment(RcsPoint start, RcsPoint end, RcsPointTarget target) {
        int type = target.getType();
        switch (RcsCurveType.of(type)) {
            case RcsCurveType.QUADRATIC_BEZIER:
            case RcsCurveType.QUADRATIC_BEZIER_CONNECT:
                return (target.getCtl1() != null)
                        ? createQuadraticBezierGeometry(start, end, target.getCtl1())
                        : createStraightLine(start, end);
            case RcsCurveType.CUBIC_BEZIER:
            case RcsCurveType.CUBIC_BEZIER_CONNECT:
                return (target.getCtl1() != null && target.getCtl2() != null)
                        ? createCubicBezierGeometry(start, end, target.getCtl1(), target.getCtl2())
                        : createStraightLine(start, end);
            case RcsCurveType.STRAIGHT:
            case RcsCurveType.STRAIGHT_CONNECT:
            default:
                return createStraightLine(start, end);
        }
    }

    // ================== 5. 辅助与私有方法 ==================

    public static boolean isPointWithinBoundingBox(int[] bbox, int x, int y) {
        if (bbox == null || bbox.length < 4) {
            return false;
        }
        // 纯逻辑比较，极快
        return !(x < bbox[0] || x > bbox[2] || y < bbox[1] || y > bbox[3]);
    }

    private static LineString createStraightLine(RcsPoint start, RcsPoint end) {
        return GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                new Coordinate(start.getX(), start.getY()),
                new Coordinate(end.getX(), end.getY())
        });
    }

    /**
     * 创建二阶贝塞尔曲线
     * [Perf] 动态采样策略：点数与长度成正比，平衡精度与性能。
     * 计算涉及大量浮点运算，但在现代 CPU 上通常不是瓶颈。
     */
    private static LineString createQuadraticBezierGeometry(RcsPoint start, RcsPoint end, RcsPointTarget.ControlPoint ctl1) {
        Coordinate p0 = new Coordinate(start.getX(), start.getY());
        Coordinate p1 = new Coordinate(ctl1.getX(), ctl1.getY());
        Coordinate p2 = new Coordinate(end.getX(), end.getY());

        double dist = p0.distance(p2);
        // [Perf] 动态采样：限制最小 10 段，最大 100 段
        int segments = Math.min(100, Math.max(10, (int) (dist / 10)));

        List<Coordinate> coordinates = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double mt = 1 - t;
            // B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
            // 展开公式计算，减少函数调用
            double x = mt * mt * p0.x + 2 * mt * t * p1.x + t * t * p2.x;
            double y = mt * mt * p0.y + 2 * mt * t * p1.y + t * t * p2.y;
            coordinates.add(new Coordinate(x, y));
        }
        return GEOMETRY_FACTORY.createLineString(coordinates.toArray(new Coordinate[0]));
    }

    /**
     * 创建三阶贝塞尔曲线
     */
    private static LineString createCubicBezierGeometry(RcsPoint start, RcsPoint end, RcsPointTarget.ControlPoint ctl1, RcsPointTarget.ControlPoint ctl2) {
        Coordinate p0 = new Coordinate(start.getX(), start.getY());
        Coordinate p1 = new Coordinate(ctl1.getX(), ctl1.getY());
        Coordinate p2 = new Coordinate(ctl2.getX(), ctl2.getY());
        Coordinate p3 = new Coordinate(end.getX(), end.getY());

        double dist = p0.distance(p3);
        // [Perf] 动态采样：限制最小 10 段，最大 100 段
        int segments = Math.min(100, Math.max(10, (int) (dist / 10)));

        List<Coordinate> coordinates = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double mt = 1 - t;
            double mt2 = mt * mt;
            double t2 = t * t;
            // B(t) = (1-t)^3 P0 + 3(1-t)^2 t P1 + 3(1-t) t^2 P2 + t^3 P3
            double x = mt2 * mt * p0.x + 3 * mt2 * t * p1.x + 3 * mt * t2 * p2.x + t2 * t * p3.x;
            double y = mt2 * mt * p0.y + 3 * mt2 * t * p1.y + 3 * mt * t2 * p2.y + t2 * t * p3.y;
            coordinates.add(new Coordinate(x, y));
        }
        return GEOMETRY_FACTORY.createLineString(coordinates.toArray(new Coordinate[0]));
    }

    private static double calculateQuadraticBezierLength(RcsPoint start, RcsPoint end, RcsPointTarget target) {
        if (target.getCtl1() == null) {
            return calculateDistance(start, end);
        }
        return createQuadraticBezierGeometry(start, end, target.getCtl1()).getLength();
    }

    private static double calculateCubicBezierLength(RcsPoint start, RcsPoint end, RcsPointTarget target) {
        if (target.getCtl1() == null || target.getCtl2() == null) {
            return calculateDistance(start, end);
        }
        return createCubicBezierGeometry(start, end, target.getCtl1(), target.getCtl2()).getLength();
    }

    /**
     * 【核心通用方法】检测两个几何对象是否在指定安全距离内发生"碰撞"
     * <p>
     * 逻辑等价于：g1.buffer(r1).intersects(g2.buffer(r2))
     * 但实现为：g1.distance(g2) <= (r1 + r2)
     * </p>
     *
     * @param g1            几何对象1 (点、线、面)
     * @param g2            几何对象2
     * @param safeThreshold 安全阈值 (通常为 r1 + r2)
     * @return true 表示碰撞(距离小于阈值)，false 表示安全
     */
    public static boolean checkCollision(Geometry g1, Geometry g2, double safeThreshold) {
        if (g1 == null || g2 == null) {
            return false;
        }

        // 1. [Broad Phase] AABB 包围盒粗筛
        // 这一步纯数值比较，能过滤 99% 的不相干对象
        Envelope env1 = g1.getEnvelopeInternal();
        Envelope env2 = g2.getEnvelopeInternal();

        // 检查 env1 扩大 safeThreshold 后是否与 env2 相交
        // 逻辑：如果 (min1 - t > max2) 或 (max1 + t < min2) ... 则不想交
        if (env1.getMinX() - safeThreshold > env2.getMaxX() ||
                env1.getMaxX() + safeThreshold < env2.getMinX() ||
                env1.getMinY() - safeThreshold > env2.getMaxY() ||
                env1.getMaxY() + safeThreshold < env2.getMinY()) {
            return false;
        }

        // 2. [Narrow Phase] 精确距离检测
        // JTS 的 distance 算法经过高度优化，处理贝塞尔曲线拟合后的 LineString 非常快
        return g1.distance(g2) <= safeThreshold;
    }

    /**
     * 过滤安全避让点
     * <p>
     * <strong>性能优化：</strong>
     * 1. <strong>AABB 粗筛：</strong> 先判断点是否在 (路径包围盒 + 安全距离) 范围内，拦截 99% 的无效计算。
     * 2. <strong>通用性：</strong> 由于 buildPathGeometry 已将贝塞尔曲线离散化为线段，distance() 能准确计算点到曲线的垂直距离。
     * </p>
     *
     * @param deadlockeds   死锁/交管路径点
     * @param idlePoints    待检测的避让点
     * @param deadlockRange 交管路径的安全半径 (mm)
     * @param avoidRange    避让点的自身半径 (mm)
     * @param edgeProvider  边属性查询器
     * @return 安全的避让点列表
     */
    public static List<RcsPoint> filterSafePoints(List<RcsPoint> deadlockeds, List<RcsPoint> idlePoints,
                                                  int deadlockRange, int avoidRange, EdgeProvider edgeProvider) {
        List<RcsPoint> safePoints = new ArrayList<>();
        if (idlePoints == null || idlePoints.isEmpty() || deadlockeds == null || deadlockeds.isEmpty()) {
            return safePoints;
        }

        // 1. 构建路径骨架 (中心线)
        Geometry deadlockPathGeom = buildPathGeometry(deadlockeds, edgeProvider);
        if (deadlockPathGeom == null) {
            return safePoints;
        }

        // 计算总的安全距离阈值 (路径半径 + 点半径)
        double totalThreshold = deadlockRange + avoidRange;

        // 2. 遍历检测
        for (RcsPoint p : idlePoints) {
            // 创建点的几何对象
            // 提示：如果是极高频调用，这里也可以手动写 distance 逻辑避免 new Point，但通常 JTS 对象创建不是瓶颈
            Point pGeom = GEOMETRY_FACTORY.createPoint(new Coordinate(p.getX(), p.getY()));

            // 调用通用检测方法
            // 如果 checkCollision 返回 true (碰撞)，则不加入安全列表
            if (!checkCollision(deadlockPathGeom, pGeom, totalThreshold)) {
                safePoints.add(p);
            }
        }
        return safePoints;
    }
}
