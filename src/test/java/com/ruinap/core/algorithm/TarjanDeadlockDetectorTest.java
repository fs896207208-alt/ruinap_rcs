package com.ruinap.core.algorithm;

import com.ruinap.core.algorithm.TarjanDeadlockDetector.Node;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.pojo.RcsPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TarjanDeadlockDetector 核心算法单元测试
 * <p>
 * 覆盖场景：
 * 1. 基础两车对冲死锁
 * 2. 正常跟车（阻塞但非死锁）
 * 3. 复杂混合场景（2车同向 + 1车异向）
 * 4. 三车环形死锁
 * 5. 单车自锁误报过滤
 * </p>
 *
 * @author qianye
 * @create 2026-01-09 14:00
 */
class TarjanDeadlockDetectorTest {

    private TarjanDeadlockDetector detector;

    @BeforeEach
    void setUp() {
        // 每次测试前重置检测器（虽然它是无状态的，但保持习惯）
        detector = new TarjanDeadlockDetector();
    }

    /**
     * 场景一：基础死锁 - 两车对冲 (Head-on Collision)
     * <p>
     * AGV_A: 在 P1，去 P2
     * AGV_B: 在 P2，去 P1
     * 预期：检测到死锁
     * </p>
     */
    @Test
    @DisplayName("场景1: 两车狭路相逢 - 应当报死锁")
    void testBasicHeadOnDeadlock() {
        // 1. 准备数据
        RcsPoint p1 = createPoint(1);
        RcsPoint p2 = createPoint(2);

        RcsAgv agvA = createAgv("A");
        RcsAgv agvB = createAgv("B");

        Map<RcsAgv, List<RcsPoint>> pathMap = new HashMap<>();
        // A: 占 P1 -> 请求 P2
        pathMap.put(agvA, Arrays.asList(p1, p2));
        // B: 占 P2 -> 请求 P1
        pathMap.put(agvB, Arrays.asList(p2, p1));

        // 2. 执行检测
        List<List<Node>> deadlocks = detector.detect(pathMap);

        // 3. 验证结果
        assertFalse(deadlocks.isEmpty(), "应当检测到死锁");
        assertEquals(1, deadlocks.size(), "应当只有一个死锁环");

        List<Node> scc = deadlocks.get(0);
        System.out.println("场景1 检测结果: " + formatScc(scc));

        // 验证环中包含两台车
        assertTrue(containsAgv(scc, "A"));
        assertTrue(containsAgv(scc, "B"));
    }

    /**
     * 场景二：单向追尾 - 阻塞但非死锁 (Chasing / Blocking)
     * <p>
     * AGV_A: 在 P1，去 P2
     * AGV_B: 在 P2，去 P3 (P3空闲)
     * 逻辑：A 被 B 挡住，但 B 是通的。
     * 预期：无死锁
     * </p>
     */
    @Test
    @DisplayName("场景2: 正常跟车追尾 - 不应报死锁")
    void testChasingNoDeadlock() {
        RcsPoint p1 = createPoint(1);
        RcsPoint p2 = createPoint(2);
        RcsPoint p3 = createPoint(3);

        RcsAgv agvA = createAgv("A");
        RcsAgv agvB = createAgv("B");

        Map<RcsAgv, List<RcsPoint>> pathMap = new HashMap<>();
        pathMap.put(agvA, Arrays.asList(p1, p2)); // A 追 B
        pathMap.put(agvB, Arrays.asList(p2, p3)); // B 去 空地

        List<List<Node>> deadlocks = detector.detect(pathMap);

        assertTrue(deadlocks.isEmpty(), "单向阻塞不应判定为死锁");
        System.out.println("场景2 检测结果: 无死锁 (符合预期)");
    }

    /**
     * 场景三：多车混合 - 2车同向 + 1车异向 (Complex Scenario)
     * <p>
     * 地图结构：1 -- 2 -- 3
     * AGV_A (同向1): 在 P1，去 P2
     * AGV_B (同向2): 在 P2，去 P3
     * AGV_C (异向):  在 P3，去 P2
     * * 逻辑推演：
     * - AGV_B 和 AGV_C 互锁 (P2<->P3 对冲)
     * - AGV_A 被 AGV_B 挡住
     * 预期：检测到死锁，且环中主要包含 B 和 C。A 只是受害者。
     * </p>
     */
    @Test
    @DisplayName("场景3: 2车同向+1车异向 - 应当报死锁(B和C互锁)")
    void testComplexMultiAgvDeadlock() {
        RcsPoint p1 = createPoint(1);
        RcsPoint p2 = createPoint(2);
        RcsPoint p3 = createPoint(3);

        RcsAgv agvA = createAgv("A");
        RcsAgv agvB = createAgv("B");
        RcsAgv agvC = createAgv("C");

        Map<RcsAgv, List<RcsPoint>> pathMap = new HashMap<>();
        // A 此时被 B 挡住
        pathMap.put(agvA, Arrays.asList(p1, p2));
        // B 此时被 C 挡住
        pathMap.put(agvB, Arrays.asList(p2, p3));
        // C 此时被 B 挡住 (反向对冲)
        pathMap.put(agvC, Arrays.asList(p3, p2));

        List<List<Node>> deadlocks = detector.detect(pathMap);

        assertFalse(deadlocks.isEmpty(), "应当检测到死锁");

        List<Node> scc = deadlocks.get(0);
        System.out.println("场景3 检测结果: " + formatScc(scc));

        // 验证：B 和 C 必须在死锁环里
        assertTrue(containsAgv(scc, "B"), "AGV B 应当在死锁环中");
        assertTrue(containsAgv(scc, "C"), "AGV C 应当在死锁环中");

        // 验证：A 不一定在环里（取决于Tarjan实现，通常A只是连在环上的单链，不属于强连通分量）
        // 实际上 A -> B <-> C，所以 A 不在 SCC 中。
        assertFalse(containsAgv(scc, "A"), "AGV A 只是被阻塞，不应在死锁闭环中");
    }

    /**
     * 场景四：三车环形死锁 (Circular Deadlock)
     * <p>
     * A -> P2 (B占)
     * B -> P3 (C占)
     * C -> P1 (A占)
     * 预期：检测到包含 3 台车的死锁大环
     * </p>
     */
    @Test
    @DisplayName("场景4: 三车转圈死锁 - 应当报死锁")
    void testCircularDeadlock() {
        RcsPoint p1 = createPoint(1);
        RcsPoint p2 = createPoint(2);
        RcsPoint p3 = createPoint(3);

        RcsAgv agvA = createAgv("A");
        RcsAgv agvB = createAgv("B");
        RcsAgv agvC = createAgv("C");

        Map<RcsAgv, List<RcsPoint>> pathMap = new HashMap<>();
        pathMap.put(agvA, Arrays.asList(p1, p2));
        pathMap.put(agvB, Arrays.asList(p2, p3));
        pathMap.put(agvC, Arrays.asList(p3, p1));

        List<List<Node>> deadlocks = detector.detect(pathMap);

        assertFalse(deadlocks.isEmpty());
        List<Node> scc = deadlocks.get(0);
        System.out.println("场景4 检测结果: " + formatScc(scc));

        assertEquals(3, countAgvs(scc), "死锁环应当包含3台车");
    }

    /**
     * 场景五：单车自锁过滤 (Self-Lock Filtering)
     * <p>
     * AGV_A: 在 P1，路径规划只有 [P1] 或者 [P1, P1]
     * 逻辑：自己请求自己占有的资源。
     * 预期：被算法的 "agvCount >= 2" 逻辑过滤，不报死锁。
     * </p>
     */
    @Test
    @DisplayName("场景5: 单车自锁 - 应当被过滤")
    void testSelfLockFilter() {
        RcsPoint p1 = createPoint(1);
        RcsAgv agvA = createAgv("A");

        Map<RcsAgv, List<RcsPoint>> pathMap = new HashMap<>();
        // 错误路径：自己去自己
        pathMap.put(agvA, Arrays.asList(p1, p1));

        List<List<Node>> deadlocks = detector.detect(pathMap);

        assertTrue(deadlocks.isEmpty(), "单车自锁应当被过滤，不视为系统级死锁");
        System.out.println("场景5 检测结果: 被过滤 (符合预期)");
    }

    // ================= 辅助方法 =================

    private RcsAgv createAgv(String id) {
        RcsAgv agv = new RcsAgv();
        agv.setAgvId(id);
        return agv;
    }

    private RcsPoint createPoint(int id) {
        RcsPoint point = new RcsPoint();
        point.setId(id);
        return point;
    }

    private boolean containsAgv(List<Node> scc, String agvId) {
        return scc.stream()
                .filter(Node::isAgv)
                .anyMatch(n -> n.getRcsAgv().getAgvId().equals(agvId));
    }

    private long countAgvs(List<Node> scc) {
        return scc.stream().filter(Node::isAgv).count();
    }

    private String formatScc(List<Node> scc) {
        return scc.stream()
                .map(n -> n.isAgv() ?
                        "[AGV-" + n.getRcsAgv().getAgvId() + "]" :
                        "(P" + n.getRcsPoint().getId() + ")")
                .collect(Collectors.joining(" -> "));
    }
}