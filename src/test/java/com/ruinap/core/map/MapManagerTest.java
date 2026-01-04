package com.ruinap.core.map;

import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.MapSnapshot;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.map.util.MapKeyUtil;
import com.ruinap.infra.framework.core.event.RcsMapConfigRefreshEvent;
import com.ruinap.infra.thread.VthreadPool;
import org.graph4j.Digraph;
import org.graph4j.GraphBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * MapManager 核心逻辑单元测试
 *
 * @author qianye
 * @create 2025-12-30 14:45
 */
@RunWith(MockitoJUnitRunner.class)
public class MapManagerTest {

    @InjectMocks
    private MapManager mapManager;

    @Mock
    private MapLoader mapLoader;

    @Mock
    private VthreadPool vthreadPool;

    @Before
    public void setUp() {
        // 1. 模拟 VthreadPool 直接同步执行任务，跳过线程调度，方便测试
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }
        }).when(vthreadPool).execute(any(Runnable.class));
    }

    /**
     * 辅助方法：构建真实的测试快照
     *
     * @param mapId    地图ID
     * @param pointIds 点位ID列表
     * @param version  版本号
     */
    private MapSnapshot buildSnapshot(int mapId, List<Integer> pointIds, String version) {
        // 1. 构建图和索引
        Digraph<RcsPoint, RcsPointTarget> graph = GraphBuilder.numVertices(pointIds.size()).buildDigraph();
        Map<Long, Integer> pointKeyToGraphId = new HashMap<>();
        Map<Long, RcsPoint> pointMap = new HashMap<>();
        Map<Long, RcsPointOccupy> occupys = new HashMap<>();
        List<RcsPoint> pointList = new ArrayList<>();

        for (int i = 0; i < pointIds.size(); i++) {
            Integer pid = pointIds.get(i);
            RcsPoint p = new RcsPoint();
            p.setId(pid);
            p.setMapId(mapId);
            p.setGraphIndex(i); // 模拟 MapLoader 分配的 ID

            long key = MapKeyUtil.compositeKey(mapId, pid);
            pointMap.put(key, p);
            pointKeyToGraphId.put(key, i);
            pointList.add(p);

            // 注入 Graph Label (核心)
            graph.setVertexLabel(i, p);

            // 模拟 MapLoader 生产初始状态
            occupys.put(key, new RcsPointOccupy(pid));
        }

        // 2. 使用 Builder 构建 Record 对象
        return MapSnapshot.builder()
                .versionMd5(Collections.singletonMap(mapId, version))
                .graph(graph)
                .pointMap(pointMap)
                .pointKeyToGraphId(pointKeyToGraphId)
                .occupys(occupys)
                // 填充其他非核心字段为空，防止 NPE
                .chargePoints(Collections.emptyMap())
                .standbyPoints(Collections.emptyMap())
                .standbyShieldPoints(Collections.emptyMap())
                .controlAreas(Collections.emptyMap())
                .controlPoints(Collections.emptyMap())
                .avoidancePoints(Collections.emptyMap())
                .actionParamMap(Collections.emptyMap())
                .build();
    }

    // 为了兼容 Record 的 getter 方法名（有些环境是 occupys() 有些是 initialStates()）
    // 假设你的 MapLoader 填充的是 initialStates，这里做个适配
    // 在 buildSnapshot 里使用的是 .initialStates(occupys)

    @Test
    public void testInitialLoad() throws Exception {
        // 准备数据
        MapSnapshot snap = buildSnapshot(1, Arrays.asList(101, 102), "V1");
        when(mapLoader.load()).thenReturn(snap);

        // 执行初始化
        mapManager.run();

        // 验证状态预热
        Assert.assertNotNull("点位 101 的状态对象应该被创建", mapManager.getPointOccupy(1, 101));
        Assert.assertNotNull("点位 102 的状态对象应该被创建", mapManager.getPointOccupy(1, 102));
        Assert.assertNull("不存在的点位应该返回 null", mapManager.getPointOccupy(1, 999));
    }

    @Test
    public void testSingleOccupyAndRelease() {
        // 初始化
        MapSnapshot snap = buildSnapshot(1, Collections.singletonList(101), "V1");
        when(mapLoader.load()).thenReturn(snap);
        mapManager.reloadAsync();

        String agvId = "AGV_001";
        RcsPoint p = snap.pointMap().get("1_101");

        // 1. 添加占用
        boolean addResult = mapManager.addOccupyType(agvId, p, PointOccupyTypeEnum.PARK);
        Assert.assertTrue("添加占用应该成功", addResult);
        Assert.assertTrue("物理状态应该被阻塞", mapManager.getPointOccupyState(1, 101));

        // 2. 释放占用
        boolean removeResult = mapManager.removeOccupyType(agvId, p, PointOccupyTypeEnum.PARK);
        Assert.assertTrue("释放占用应该返回 true", removeResult);
        Assert.assertFalse("物理状态应该恢复空闲", mapManager.getPointOccupyState(1, 101));

        // 3. 重复释放
        boolean removeAgain = mapManager.removeOccupyType(agvId, p, PointOccupyTypeEnum.PARK);
        Assert.assertFalse("重复释放应该返回 false", removeAgain);
    }

    @Test
    public void testBatchOccupy() {
        // 地图有 101, 102, 103
        MapSnapshot snap = buildSnapshot(1, Arrays.asList(101, 102, 103), "V1");
        when(mapLoader.load()).thenReturn(snap);
        mapManager.reloadAsync();

        String agvId = "AGV_BATCH";
        RcsPoint p1 = snap.pointMap().get("1_101");
        RcsPoint p2 = snap.pointMap().get("1_102");

        // 构造一个不存在的点位
        RcsPoint pInvalid = new RcsPoint();
        pInvalid.setId(999);
        pInvalid.setMapId(1);

        // Case 1: 正常批量占用
        boolean res1 = mapManager.addOccupyType(agvId, Arrays.asList(p1, p2), PointOccupyTypeEnum.TASK);
        Assert.assertTrue("所有有效点位都应该成功", res1);
        Assert.assertTrue(mapManager.getPointOccupyState(1, 101));
        Assert.assertTrue(mapManager.getPointOccupyState(1, 102));
        Assert.assertFalse("103 未被操作，应该空闲", mapManager.getPointOccupyState(1, 103));

        // Case 2: 包含无效点位
        boolean res2 = mapManager.addOccupyType(agvId, Arrays.asList(p1, pInvalid), PointOccupyTypeEnum.TASK);
        Assert.assertFalse("包含无效点位时应该返回 false", res2);
    }

    @Test
    public void testFindNearbyPoints_TopologyOnly() {
        // 构建图: 101 -> 102
        List<Integer> ids = Arrays.asList(101, 102);
        MapSnapshot snap = buildSnapshot(1, ids, "V1");
        // 手动加边: 0(101) -> 1(102)
        snap.graph().addEdge(0, 1);

        when(mapLoader.load()).thenReturn(snap);
        mapManager.reloadAsync();

        // 1. 占用 102，使其物理阻塞
        RcsPoint p102 = snap.pointMap().get("1_102");
        mapManager.addOccupyType("BLOCKER", p102, PointOccupyTypeEnum.PARK);
        Assert.assertTrue(mapManager.getPointOccupyState(1, 102));

        // 2. 执行纯 BFS 搜索 (应该无视占用)
        List<RcsPoint> result = mapManager.findNearbyPoints(1, 101, 1);

        // 3. 验证
        Assert.assertEquals("应该找到2个点(起点+邻居)", 2, result.size());
        boolean has102 = false;
        for (RcsPoint p : result) {
            if (p.getId() == 102) has102 = true;
        }
        Assert.assertTrue("结果中应该包含被占用的 102，因为这是纯拓扑搜索", has102);
    }

    @Test
    public void testHotReload_MergeState() {
        // 1. 加载旧版 V1
        MapSnapshot v1 = buildSnapshot(1, Collections.singletonList(101), "V1");
        when(mapLoader.load()).thenReturn(v1);
        mapManager.reloadAsync();

        // 2. 锁住 101
        RcsPoint p101 = v1.pointMap().get("1_101");
        mapManager.addOccupyType("AGV_OLD", p101, PointOccupyTypeEnum.PARK);

        // 记录旧对象引用
        RcsPointOccupy oldStateObj = mapManager.getPointOccupy(1, 101);
        Assert.assertTrue(oldStateObj.isPhysicalBlocked());

        // 3. 加载新版 V2 (内容相同，模拟配置刷新)
        MapSnapshot v2 = buildSnapshot(1, Collections.singletonList(101), "V2");
        when(mapLoader.load()).thenReturn(v2);

        // 触发热更新
        mapManager.onApplicationEvent(new RcsMapConfigRefreshEvent(this));

        // 4. 验证：锁必须还在，且对象引用不能变
        Assert.assertTrue("热更新后锁必须保留", mapManager.getPointOccupyState(1, 101));

        RcsPointOccupy newStateObj = mapManager.getPointOccupy(1, 101);
        Assert.assertSame("为了保证锁的连续性，必须复用旧的状态对象", oldStateObj, newStateObj);
    }

    @Test
    public void testHotReload_CleanupProtection() throws Exception {
        // 1. 加载旧版 (101, 102)
        MapSnapshot v1 = buildSnapshot(1, Arrays.asList(101, 102), "V1");
        when(mapLoader.load()).thenReturn(v1);
        mapManager.reloadAsync();

        // 场景：101 被锁，102 空闲
        RcsPoint p101 = v1.pointMap().get("1_101");
        mapManager.addOccupyType("AGV_LOCK", p101, PointOccupyTypeEnum.PARK);

        // 2. 加载新版
        // 【修正点】不能传 emptyList，否则会触发 MapManager 的防空保护机制导致不更新。
        // 我们传一个无关的新点位 999，模拟地图大改版：101和102被删了，新增了999。
        MapSnapshot v2 = buildSnapshot(1, Collections.singletonList(999), "V2");
        when(mapLoader.load()).thenReturn(v2);

        // 触发热更新
        mapManager.onApplicationEvent(new RcsMapConfigRefreshEvent(this));

        // 3. 验证 102 (空闲) -> 应该被清理
        // 102 不在新地图里，且没锁，应该被移除
        Assert.assertNull("空闲且被删除的点 102 应该从内存移除", mapManager.getPointOccupy(1, 102));

        // 4. 验证 101 (被锁) -> 应该被保护性保留
        // 101 不在新地图里，但有锁，应该保留
        RcsPointOccupy protectedState = mapManager.getPointOccupy(1, 101);

        Assert.assertNotNull("被锁住的点 101 即使在地图中删除，也应保留在内存中以防报错", protectedState);
        Assert.assertTrue("保留下来的对象锁状态应保持不变", protectedState.isPhysicalBlocked());

        // 5. 验证是否能释放
        boolean releaseRes = mapManager.removeOccupyType("AGV_LOCK", p101, PointOccupyTypeEnum.PARK);
        Assert.assertTrue("应该允许释放这个'幽灵'点位", releaseRes);
        Assert.assertFalse("释放后物理状态应变为空闲", mapManager.getPointOccupy(1, 101).isPhysicalBlocked());
    }

    /**
     * 反射工具：获取私有 occupyMap (如果需要深度验证)
     */
    @SuppressWarnings("unchecked")
    private Map<String, RcsPointOccupy> getPrivateOccupyMap() throws Exception {
        Field field = MapManager.class.getDeclaredField("occupyMap");
        field.setAccessible(true);
        return (Map<String, RcsPointOccupy>) field.get(mapManager);
    }
}