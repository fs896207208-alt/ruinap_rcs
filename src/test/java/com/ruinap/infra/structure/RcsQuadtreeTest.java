package com.ruinap.infra.structure;

import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RcsQuadtree 分片空间索引测试
 * <p>
 * 测试目标：
 * 1. 验证分片计算与边界处理逻辑。
 * 2. 验证跨分片插入与查询的去重机制。
 * 3. 验证增删改查的基本功能完整性。
 * </p>
 *
 * @author qianye
 * @create 2026-01-06 18:09
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RcsQuadtreeTest {

    private RcsQuadtree rcsQuadtree;

    // 地图参数：100x100 的地图，原点 (0,0)
    // 分片设置：2x2 = 4 个分片
    private final double MAP_WIDTH = 100.0;
    private final double MAP_HEIGHT = 100.0;
    private final int GRID_SIZE = 2;

    @BeforeEach
    void setUp() {
        // 直接初始化，不再干涉 Logger
        rcsQuadtree = new RcsQuadtree(MAP_WIDTH, MAP_HEIGHT, GRID_SIZE, 0, 0);
    }

    // ==========================================
    // 1. 基础功能测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("基础插入与单分片查询")
    void testInsertAndQuery_SingleShard() {
        System.out.println("★ 1. 测试单分片插入查询");

        // 插入一个位于 Shard 0 中心的小物体 (25, 25)
        Envelope itemEnv = new Envelope(24, 26, 24, 26);
        String item = "AGV-001";
        rcsQuadtree.insert(itemEnv, item);

        // 1. 精确查询
        List<Object> result = rcsQuadtree.query(new Envelope(20, 30, 20, 30));
        assertEquals(1, result.size());
        assertEquals(item, result.get(0));

        // 2. 不相交查询
        List<Object> emptyResult = rcsQuadtree.query(new Envelope(80, 90, 80, 90)); // Shard 3
        assertTrue(emptyResult.isEmpty());

        System.out.println("   [PASS] 基础功能正常");
    }

    @Test
    @Order(2)
    @DisplayName("跨分片插入与去重查询")
    void testInsertAndQuery_CrossShard() {
        System.out.println("★ 2. 测试跨分片处理 (去重)");

        // 插入一个横跨 Shard 0 和 Shard 1 的物体
        Envelope crossItemEnv = new Envelope(40, 60, 10, 20);
        String item = "Long-AGV-002";
        rcsQuadtree.insert(crossItemEnv, item);

        assertEquals(2, rcsQuadtree.size(), "跨两个分片的物体物理计数应为 2");

        // 验证查询去重
        List<Object> result = rcsQuadtree.query(new Envelope(0, 100, 0, 50));
        assertEquals(1, result.size(), "查询结果必须去重");
        assertEquals(item, result.get(0));

        System.out.println("   [PASS] 跨分片去重逻辑正常");
    }

    // ==========================================
    // 2. 边界条件测试
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("边界钳制测试")
    void testBoundaryClamp() {
        System.out.println("★ 3. 测试边界钳制");

        // 插入一个负坐标物体 (应该被钳制到 Shard 0)
        Envelope outLeft = new Envelope(-50, -40, 10, 20);
        rcsQuadtree.insert(outLeft, "Out-Left");

        // 插入一个超大坐标物体 (应该被钳制到 Shard 3)
        Envelope outRight = new Envelope(150, 160, 150, 160);
        rcsQuadtree.insert(outRight, "Out-Right");

        // 查询 Shard 0 (内部坐标范围 0~50)
        List<Object> res0 = rcsQuadtree.query(new Envelope(0, 10, 0, 10));
        assertTrue(res0.isEmpty());

        // 如果我们扩大查询范围到负数，应该能查到
        List<Object> resNegative = rcsQuadtree.query(new Envelope(-100, 0, 0, 100));
        assertEquals(1, resNegative.size());
        assertEquals("Out-Left", resNegative.get(0));

        System.out.println("   [PASS] 边界钳制及查询正常");
    }

    // ==========================================
    // 3. 增删改测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("移除与更新")
    void testRemoveAndUpdate() {
        System.out.println("★ 4. 测试移除与更新");

        Envelope env = new Envelope(10, 20, 10, 20);
        String obj = "Moving-Obj";

        // 1. 插入
        rcsQuadtree.insert(env, obj);
        assertFalse(rcsQuadtree.query(env).isEmpty());

        // 2. 移除
        boolean removed = rcsQuadtree.remove(env, obj);
        assertTrue(removed, "移除应成功");
        assertEquals(0, rcsQuadtree.size());
        assertTrue(rcsQuadtree.query(env).isEmpty());

        // 3. 再次移除 (应失败)
        boolean removedAgain = rcsQuadtree.remove(env, obj);
        assertFalse(removedAgain, "重复移除应返回 false");

        // 4. Update (模拟移动)
        // 先插入
        rcsQuadtree.insert(env, obj);

        // 移动到新位置 (Shard 3)
        Envelope newEnv = new Envelope(60, 70, 60, 70);

        // [修复] 使用 factory.toGeometry(env)
        GeometryFactory factory = new GeometryFactory();
        rcsQuadtree.update(obj, factory.toGeometry(env), factory.toGeometry(newEnv));

        // 验证旧位置没了
        assertTrue(rcsQuadtree.query(env).isEmpty());
        // 验证新位置有了
        List<Object> newRes = rcsQuadtree.query(newEnv);
        assertEquals(1, newRes.size());
        assertEquals(obj, newRes.get(0));

        System.out.println("   [PASS] 增删改逻辑正常");
    }

    @Test
    @Order(5)
    @DisplayName("全量查询 (QueryAll)")
    void testQueryAll() {
        System.out.println("★ 5. 测试全量查询");

        rcsQuadtree.insert(new Envelope(10, 20, 10, 20), "A"); // Shard 0
        rcsQuadtree.insert(new Envelope(60, 70, 60, 70), "B"); // Shard 3

        List<Object> all = rcsQuadtree.queryAll();
        assertEquals(2, all.size());
        assertTrue(all.contains("A"));
        assertTrue(all.contains("B"));
        System.out.println("   [PASS] 全量查询正常");
    }
}