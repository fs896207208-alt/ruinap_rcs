package com.ruinap.core.map;

/**
 * * MapManager 并发压力测试
 * * <p>
 * * 重点测试：多线程争抢锁、状态一致性
 * * </p>
 *
 * @author qianye
 * @create 2025-12-30 15:29
 */
public class MapManagerConcurrentTest {

//    @InjectMocks
//    private MapManager mapManager;
//
//    @Mock
//    private MapLoader mapLoader;
//
//    @Mock
//    private VthreadPool vthreadPool;
//
//    // 使用真实线程池模拟并发
//    private ExecutorService executorService;
//
//    // JUnit 6 Mock 资源句柄
//    private AutoCloseable mockitoCloseable;
//
//    @BeforeEach
//    public void setUp() {
//        // JUnit 6 初始化 Mock
//        mockitoCloseable = MockitoAnnotations.openMocks(this);
//
//        // 1. 模拟 VthreadPool (并发测试中其实不太依赖它，因为我们自己开线程池，但为了防NPE)
//        doAnswer(invocation -> {
//            Runnable r = invocation.getArgument(0);
//            r.run();
//            return null;
//        }).when(vthreadPool).execute(any(Runnable.class));
//
//        // JDK 21 推荐使用虚拟线程，这里为了通用性使用固定线程池，效果一样
//        executorService = Executors.newFixedThreadPool(100);
//    }
//
//    @AfterEach
//    public void tearDown() throws Exception {
//        // 关闭 Mock 资源
//        if (mockitoCloseable != null) {
//            mockitoCloseable.close();
//        }
//        // 最好关闭线程池，虽然原代码没写，但为了测试稳定性建议加上
//        if (executorService != null) {
//            executorService.shutdownNow();
//        }
//    }
//
//    /**
//     * 辅助构建：必须使用 真实的 RcsPointOccupy 对象，否则测不出锁的效果
//     */
//    private MapSnapshot buildRealSnapshot(int mapId, int pointCount) {
//        Digraph<RcsPoint, RcsPointTarget> graph = GraphBuilder.numVertices(pointCount).buildDigraph();
//        Map<Long, RcsPoint> pointMap = new HashMap<>();
//        Map<Long, Integer> keyToId = new HashMap<>();
//        Map<Long, RcsPointOccupy> occupys = new HashMap<>();
//        List<RcsPoint> pointList = new ArrayList<>();
//
//        for (int i = 0; i < pointCount; i++) {
//            int pid = 100 + i;
//            RcsPoint p = new RcsPoint();
//            p.setId(pid);
//            p.setMapId(mapId);
//            p.setGraphIndex(i);
//
//            long key = MapKeyUtil.compositeKey(mapId, pid);
//            pointMap.put(key, p);
//            keyToId.put(key, i);
//            pointList.add(p);
//            graph.setVertexLabel(i, p);
//
//            // 【关键】必须 New 真实的 RcsPointOccupy，因为锁逻辑在它里面
//            // 假设 RcsPointOccupy 内部使用了 RcsLock (ReentrantLock)
//            occupys.put(key, new RcsPointOccupy(key, pid));
//        }
//
//        return MapSnapshot.builder()
//                .versionMd5(Collections.singletonMap(mapId, "CONCURRENT_TEST"))
//                .graph(graph)
//                .pointMap(pointMap)
//                .pointKeyToGraphId(keyToId)
//                .occupys(occupys)
//                .build();
//    }
//
//    @Test
//    public void testConcurrentLockCompetition() throws InterruptedException {
//        // 1. 准备环境：1个点位 (101)
//        MapSnapshot snap = buildRealSnapshot(1, 1);
//        when(mapLoader.load()).thenReturn(snap);
//        mapManager.reloadAsync();
//
//        RcsPoint targetPoint = snap.pointMap().get("1_100");
//        int threadCount = 50;
//        CountDownLatch startGun = new CountDownLatch(1);
//        CountDownLatch finishLine = new CountDownLatch(threadCount);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        // 2. 启动 50 个线程争抢同一个点
//        for (int i = 0; i < threadCount; i++) {
//            final String deviceCode = "AGV_" + i;
//            executorService.submit(() -> {
//                try {
//                    startGun.await(); // 等待发令枪
//                    // 争抢：调用 addOccupyType (底层是 tryOccupied)
//                    // 注意：不同 AGV 争抢同一个点，期望互斥
//                    boolean result = mapManager.addOccupyType(deviceCode, targetPoint, PointOccupyTypeEnum.TASK);
//                    if (result) {
//                        successCount.incrementAndGet();
//                    } else {
//                        failCount.incrementAndGet();
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    finishLine.countDown();
//                }
//            });
//        }
//
//        // 3. 砰！开抢
//        startGun.countDown();
//        finishLine.await(); // 等待所有线程跑完
//
//        // 4. 验证结果
//        // 理论上：只有 1 个能抢到，49 个失败
//        System.out.println("争抢结果 - 成功: " + successCount.get() + ", 失败: " + failCount.get());
//
//        // JUnit 6: assertEquals(expected, actual, message)
//        Assertions.assertEquals(1, successCount.get(), "应该只有1个线程能抢到锁");
//        Assertions.assertEquals(threadCount - 1, failCount.get(), "其他线程应该全部失败");
//
//        // 验证物理状态
//        // JUnit 6: assertTrue(condition, message)
//        Assertions.assertTrue(mapManager.getPointOccupyState(1, 100), "点位最终应该被锁住");
//    }
//
//    @Test
//    public void testConcurrentBatchLocking() throws InterruptedException {
//        // 1. 准备环境：3个点位 (100, 101, 102)
//        MapSnapshot snap = buildRealSnapshot(1, 3);
//        when(mapLoader.load()).thenReturn(snap);
//        mapManager.reloadAsync();
//
//        RcsPoint p1 = snap.pointMap().get("1_100");
//        RcsPoint p2 = snap.pointMap().get("1_101");
//        RcsPoint p3 = snap.pointMap().get("1_102");
//
//        // 场景：
//        // 线程 A 试图锁: [100, 101]
//        // 线程 B 试图锁: [101, 102]
//        // 冲突点在 101
//
//        CountDownLatch startGun = new CountDownLatch(1);
//        CountDownLatch finishLine = new CountDownLatch(2);
//        AtomicInteger successCount = new AtomicInteger(0);
//
//        // 线程 A
//        executorService.submit(() -> {
//            try {
//                startGun.await();
//                if (mapManager.addOccupyType("AGV_A", Arrays.asList(p1, p2), PointOccupyTypeEnum.TASK)) {
//                    successCount.incrementAndGet();
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            } finally {
//                finishLine.countDown();
//            }
//        });
//
//        // 线程 B
//        executorService.submit(() -> {
//            try {
//                startGun.await();
//                if (mapManager.addOccupyType("AGV_B", Arrays.asList(p2, p3), PointOccupyTypeEnum.TASK)) {
//                    successCount.incrementAndGet();
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            } finally {
//                finishLine.countDown();
//            }
//        });
//
//        startGun.countDown();
//        finishLine.await();
//
//        // 验证：
//        // 1. 101 只能被 A 或 B 其中一个锁住，不能共同锁住
//        // 2. 成功数可能是 1 (一个成功一个失败) 或者 2 (如果允许重入/或者逻辑不同)
//        // 现在的逻辑是 tryOccupied，互斥的。所以理论上最多只有一个能把 101 锁住。
//
//        // 检查 101 的归属
//        RcsPointOccupy occupy101 = mapManager.getPointOccupy(1, 101);
//        Assertions.assertTrue(occupy101.isPhysicalBlocked(), "101 必须被锁住");
//
//        Set<PointOccupyTypeEnum> ownersA = occupy101.getOccupants().get("AGV_A");
//        Set<PointOccupyTypeEnum> ownersB = occupy101.getOccupants().get("AGV_B");
//
//        boolean aHasIt = ownersA != null && !ownersA.isEmpty();
//        boolean bHasIt = ownersB != null && !ownersB.isEmpty();
//
//        System.out.println("101 归属: A=" + aHasIt + ", B=" + bHasIt);
//
//        // 互斥验证：不能同时拥有
//        Assertions.assertFalse(aHasIt && bHasIt, "101 不能同时被 A 和 B 锁住");
//        Assertions.assertTrue(aHasIt || bHasIt, "101 必须被 A 或 B 锁住");
//    }
}