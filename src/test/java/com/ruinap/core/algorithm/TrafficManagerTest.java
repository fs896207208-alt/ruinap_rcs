package com.ruinap.core.algorithm;

import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.infra.config.CoreYaml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * TrafficManager (交通管理器) 严苛单元测试
 * <p>
 * 测试核心方法: pruneAndReviewPath
 * 覆盖场景:
 * 1. 基础剪枝: 正常剔除身后的历史路径
 * 2. 坐标漂移: 当前点不在预期路径中，全量容错下发
 * 3. 遇阻回退 (Retreat): 遇到其他车占用，精准向后截取保留点数
 * 4. 路口限流 (Intersection): 跨越指定数量的岔路口后精准截断
 * 5. 阈值防御 (Threshold): 下发长度不足时阻断起步
 * 6. 自身穿透 (Self-Occupy): 自己占用的点位直接放行
 * </p>
 *
 * @author qianye
 * @create 2026-03-11 10:10
 */
@ExtendWith(MockitoExtension.class)
class TrafficManagerTest {

    private TrafficManager trafficManager;

    @Mock
    private CoreYaml coreYaml;
    @Mock
    private MapManager mapManager;

    private final String AGV_ID = "AGV_001";
    private final Integer MAP_ID = 1;

    // 默认的 YAML 配置模拟
    private Map<String, Integer> mockAlgorithmCommon;

    @BeforeEach
    void setUp() throws Exception {
        trafficManager = new TrafficManager();
        injectField(trafficManager, "coreYaml", coreYaml);
        injectField(trafficManager, "mapManager", mapManager);

        // 初始化默认配置: 退1步, 1个路口, 最小步数1
        mockAlgorithmCommon = new HashMap<>();
        mockAlgorithmCommon.put("retreat_point", 1);
        mockAlgorithmCommon.put("stop_intersection", 1);
        mockAlgorithmCommon.put("plan_allow_delivery_threshold", 1);
        lenient().when(coreYaml.getAlgorithmCommon()).thenReturn(mockAlgorithmCommon);

        // 默认放行所有点位占用和出边（单边，非路口）
        lenient().when(mapManager.getPointOccupy(anyInt(), anyInt())).thenReturn(null);
        List<RcsPointTarget> singleEdge = Collections.singletonList(new RcsPointTarget());
        lenient().when(mapManager.getOutgoingEdges(anyInt(), anyInt())).thenReturn(singleEdge);
    }

    @Test
    @DisplayName("防御测试: 传入空路径直接返回空集合")
    void testPrune_EmptyPath() {
        List<RcsPoint> result1 = trafficManager.pruneAndReviewPath(AGV_ID, new RcsPoint(), null);
        List<RcsPoint> result2 = trafficManager.pruneAndReviewPath(AGV_ID, new RcsPoint(), new ArrayList<>());
        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
    }

    @Test
    @DisplayName("剪枝测试: 正常裁剪身后的历史节点")
    void testPrune_NormalCut() {
        RcsPoint p1 = createPoint(1);
        RcsPoint p2 = createPoint(2); // Current
        RcsPoint p3 = createPoint(3);
        RcsPoint p4 = createPoint(4);

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2, p3, p4);

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p2, expectRoutes);

        assertEquals(3, safePath.size(), "应剔除 P1，保留 P2, P3, P4");
        assertEquals(p2, safePath.get(0));
        assertEquals(p4, safePath.get(2));
    }

    @Test
    @DisplayName("容错测试: 当前点发生微小漂移不在路线上，全量下发预期路线靠线")
    void testPrune_DriftFallback() {
        RcsPoint currentPoint = createPoint(999); // 不在路径中
        RcsPoint p1 = createPoint(1);
        RcsPoint p2 = createPoint(2);

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2);

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, currentPoint, expectRoutes);

        assertEquals(2, safePath.size(), "发生漂移时，应全量保留预期路径以供底盘靠线");
        assertEquals(p1, safePath.get(0));
    }

    @Test
    @DisplayName("交通管制: 遇阻回退 (Retreat = 1)，前方被占时留出安全距离")
    void testReview_ObstacleRetreat() {
        // 配置退回 1 个点
        mockAlgorithmCommon.put("retreat_point", 1);

        RcsPoint p1 = createPoint(1); // Current
        RcsPoint p2 = createPoint(2); // 空闲
        RcsPoint p3 = createPoint(3); // 空闲
        RcsPoint p4 = createPoint(4); // 被其他车占用！
        RcsPoint p5 = createPoint(5);

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2, p3, p4, p5);

        // Mock 占用状态
        RcsPointOccupy occupy = mock(RcsPointOccupy.class);
        when(occupy.isPhysicalBlocked()).thenReturn(true);
        when(occupy.getDeviceOccupyState(AGV_ID)).thenReturn(false); // 不是自己占的
        when(mapManager.getPointOccupy(MAP_ID, 4)).thenReturn(occupy);

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p1, expectRoutes);

        // 分析: p1, p2, p3 安全加入 (size=3)。
        // 遇到 p4 占用。keepCount = Math.max(1, 3 - 1 + 1) = 3。
        // 截取前 3 个: p1, p2, p3。
        assertEquals(3, safePath.size(), "遇到 P4 占用退1步，应停在 P3");
        assertEquals(p3, safePath.get(2));
    }

    @Test
    @DisplayName("交通管制: 遇阻强力回退 (Retreat = 2)，确保不丢失当前坐标")
    void testReview_ObstacleStrongRetreat() {
        // 配置退回 2 个点
        mockAlgorithmCommon.put("retreat_point", 2);

        RcsPoint p1 = createPoint(1); // Current
        RcsPoint p2 = createPoint(2); // 被其他车占用！

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2);

        // Mock 占用状态
        RcsPointOccupy occupy = mock(RcsPointOccupy.class);
        when(occupy.isPhysicalBlocked()).thenReturn(true);
        when(occupy.getDeviceOccupyState(AGV_ID)).thenReturn(false);
        when(mapManager.getPointOccupy(MAP_ID, 2)).thenReturn(occupy);

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p1, expectRoutes);

        // 分析: p1 安全加入 (size=1)。
        // 遇到 p2 占用。keepCount = Math.max(1, 1 - 2 + 1) = 1。
        // 极限保护生效，强行保留 p1 (不会变成空导致坐标丢失)。
        assertEquals(1, safePath.size(), "极端回退下必须保底保留当前物理节点");
        assertEquals(p1, safePath.getFirst());
    }

    @Test
    @DisplayName("路权连贯性: 目标点被【自己】占用时直接穿透放行 (防自锁)")
    void testReview_SelfOccupyPassThrough() {
        RcsPoint p1 = createPoint(1); // Current
        RcsPoint p2 = createPoint(2); // 自己占用的点
        RcsPoint p3 = createPoint(3);

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2, p3);

        // Mock 占用状态：是自己占的
        RcsPointOccupy occupy = mock(RcsPointOccupy.class);
        when(occupy.isPhysicalBlocked()).thenReturn(true);
        when(occupy.getDeviceOccupyState(AGV_ID)).thenReturn(true); // 关键！
        when(mapManager.getPointOccupy(MAP_ID, 2)).thenReturn(occupy);

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p1, expectRoutes);

        assertEquals(3, safePath.size(), "自己占用的点不应拦截，直接全量下发");
    }

    @Test
    @DisplayName("图论防误判: 1<->2<->3 双向直行道不应被视为岔路口")
    void testReview_NotIntersection_StraightLine() {
        mockAlgorithmCommon.put("stop_intersection", 1);

        RcsPoint p1 = createPoint(1); // Current
        RcsPoint p2 = createPoint(2); // 直行道
        RcsPoint p3 = createPoint(3);

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2, p3);

        // 构造 P2 的真实出边 (仅仅是双向的 1 和 3，不需要 setMapId)
        RcsPointTarget t2_back = new RcsPointTarget();
        t2_back.setId(1);
        RcsPointTarget t2_forward = new RcsPointTarget();
        t2_forward.setId(3);
        when(mapManager.getOutgoingEdges(MAP_ID, 2)).thenReturn(Arrays.asList(t2_back, t2_forward));

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p1, expectRoutes);

        // 分析：p2 剔除来时的 p1，出边只剩 p3。forwardChoices = 1。不是路口！
        // 顺利通过，全量下发
        assertEquals(3, safePath.size(), "双向直行道不应触发路口截断");
    }

    @Test
    @DisplayName("十字路口限流: 真实岔路判定与截断 (排除来时路后仍有多条出边)")
    void testReview_RealStopIntersection() {
        mockAlgorithmCommon.put("stop_intersection", 1);

        RcsPoint p1 = createPoint(1); // Current
        RcsPoint p2 = createPoint(2); // 真实的岔路口1
        RcsPoint p3 = createPoint(3); // 真实的岔路口2
        RcsPoint p4 = createPoint(4);

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2, p3, p4);

        // 构造 P2 的出边 (回头路1, 前进3, 岔路99)
        RcsPointTarget t2_back = new RcsPointTarget();
        t2_back.setId(1);
        RcsPointTarget t2_forward = new RcsPointTarget();
        t2_forward.setId(3);
        RcsPointTarget t2_branch = new RcsPointTarget();
        t2_branch.setId(99); // 岔路
        when(mapManager.getOutgoingEdges(MAP_ID, 2)).thenReturn(Arrays.asList(t2_back, t2_forward, t2_branch));

        // 构造 P3 的出边 (回头路2, 前进4, 岔路88)
        RcsPointTarget t3_back = new RcsPointTarget();
        t3_back.setId(2);
        RcsPointTarget t3_forward = new RcsPointTarget();
        t3_forward.setId(4);
        RcsPointTarget t3_branch = new RcsPointTarget();
        t3_branch.setId(88); // 岔路
        when(mapManager.getOutgoingEdges(MAP_ID, 3)).thenReturn(Arrays.asList(t3_back, t3_forward, t3_branch));

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p1, expectRoutes);

        // 分析:
        // p1 加入。
        // p2 加入，计算出边: 剔除来时的 p1(1)。剩下 3 和 99。forwardChoices=2。是真实路口！count 变 1。
        // p3 加入，计算出边: 剔除来时的 p2(2)。剩下 4 和 88。forwardChoices=2。是真实路口！count=1 >= 1，触发 break！
        // 截断结果应包含 p3，用于在此停车。
        assertEquals(3, safePath.size(), "应在达到第二个真实路口处截断");
        assertEquals(p3, safePath.get(2));
    }

    @Test
    @DisplayName("下发阈值防御: (threshold = 2) 审查后的可用步数不足时拒绝发车，下发原地驻留指令")
    void testReview_ThresholdBlock() {
        // 要求至少能走 2 步 (即 safePath.size() - 1 >= 2)
        mockAlgorithmCommon.put("plan_allow_delivery_threshold", 2);

        RcsPoint p1 = createPoint(1); // Current
        RcsPoint p2 = createPoint(2);
        RcsPoint p3 = createPoint(3); // 被挡住了

        List<RcsPoint> expectRoutes = Arrays.asList(p1, p2, p3);

        // Mock P3 被占用
        RcsPointOccupy occupy = mock(RcsPointOccupy.class);
        when(occupy.isPhysicalBlocked()).thenReturn(true);
        when(occupy.getDeviceOccupyState(AGV_ID)).thenReturn(false);
        when(mapManager.getPointOccupy(MAP_ID, 3)).thenReturn(occupy);

        List<RcsPoint> safePath = trafficManager.pruneAndReviewPath(AGV_ID, p1, expectRoutes);

        // 分析: 审查网截断后，原本 safePath = [p1, p2]，步数为 1。
        // 阈值要求至少走 2 步，触发兜底拦截。
        // 根据最新的防 Panic 架构，系统不应返回空集合，而是应该返回只包含当前 p1 的驻留指令。
        assertEquals(1, safePath.size(), "剩余安全步数不足阈值，应返回仅包含当前点的集合作为原地驻留指令");
        assertEquals(p1, safePath.getFirst(), "必须确保驻留指令的点就是自己当前脚下的点");
    }

    // ================== 辅助方法 ==================

    private RcsPoint createPoint(int id) {
        RcsPoint point = new RcsPoint();
        point.setId(id);
        point.setMapId(MAP_ID);
        point.setName("Point_" + id);
        return point;
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
