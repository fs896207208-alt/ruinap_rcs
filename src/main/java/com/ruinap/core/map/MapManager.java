package com.ruinap.core.map;

import cn.hutool.core.util.StrUtil;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.MapSnapshot;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.map.util.MapKeyUtil;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.framework.core.event.ApplicationListener;
import com.ruinap.infra.framework.core.event.RcsMapConfigRefreshEvent;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import lombok.Getter;
import org.graph4j.Digraph;
import org.graph4j.Edge;
import org.graph4j.NeighborIterator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <h1>静态地图管理器</h1>
 * <p>持有 MapSnapshot 的唯一引用，负责热更新逻辑。</p>
 *
 * @author qianye
 * @create 2025-12-23 17:23
 */
@Component
@Order(6)
public class MapManager implements CommandLineRunner, ApplicationListener<RcsMapConfigRefreshEvent> {
    @Autowired
    private MapLoader mapLoader;
    @Autowired
    private VthreadPool vthreadPool;

    // ================== 1. 静态数据区 ==================
    /**
     * 不可变快照，支持高并发读。
     * 使用 volatile 保证热更新时的内存可见性。
     */
    @Getter
    private volatile MapSnapshot snapshot = MapSnapshot.empty();
    /**
     * 热更新互斥锁，防止多个配置变更事件同时触发重载
     */
    private final Lock reloadLock = new ReentrantLock();

    // ================== 2. 动态数据区 ==================
    /**
     * 点位占用表
     * Key: "mapId_pointId" (业务主键)
     * Value: RcsPointOccupy (新版高性能状态对象)
     * <p>
     * 策略：全量预加载，严禁运行时动态添加 Key。
     */
    private final Map<String, RcsPointOccupy> occupyMap = new ConcurrentHashMap<>();

    // ================== 3. 生命周期管理 ==================

    /**
     * 容器启动后自动调用此方法
     */
    @Override
    public void run(String... args) throws Exception {
        RcsLog.consoleLog.info("MapManager 即将初始化地图数据，系统需要一点时间，请等待");
        RcsLog.algorithmLog.info("MapManager 即将初始化地图数据，系统需要一点时间，请等待");
        RcsLog.consoleLog.info("MapManager 开始解析地图点位和线路数据");
        RcsLog.algorithmLog.info("MapManager 开始解析地图点位和线路数据");
        // 直接调用内部加载逻辑，主线程阻塞执行
        loadInternal(true);
    }

    /**
     * 【核心】事件监听回调
     * 当 MapYaml 发布配置变更事件时，自动触发此方法
     */
    @Override
    public void onApplicationEvent(RcsMapConfigRefreshEvent event) {
        RcsLog.sysLog.info("MapManager 监听到地图配置变更事件，触发热重载");
        this.reloadAsync();
    }

    // ================== 4. 动态状态操作 ==================

    /**
     * 获取点位占用对象 (严格模式)
     * <p>不存在的点位直接返回 null</p>
     *
     * @param mapId   地图ID
     * @param pointId 点位ID
     * @return 状态对象 或 null
     */
    public RcsPointOccupy getPointOccupy(Integer mapId, Integer pointId) {
        if (mapId == null || pointId == null) {
            return null;
        }
        // 既然 loadInternal 保证了集合完整性，这里直接 get 即可
        // 甚至不需要查 snapshot.getGraphId 做前置校验，因为 occupyMap 的 Key 集合是 snapshot 的子集(或超集)
        return occupyMap.get(MapKeyUtil.compositeKey(mapId, pointId));
    }

    /**
     * 获取地图的点位占用对象
     * <p>
     * 本方法通过点ID从有向权重图缓存中检索对应的点位占用信息。此方法用于快速访问已知ID的点位
     *
     * @param rcsPoint 地图点位
     * @return 返回与给定点位匹配的点位占用对象。如果找不到匹配的点，则返回null
     */
    public RcsPointOccupy getRcsOccupy(RcsPoint rcsPoint) {
        return getPointOccupy(rcsPoint.getMapId(), rcsPoint.getId());
    }

    /**
     * 检查点位是否被占用
     */
    public boolean getPointOccupyState(Integer mapId, Integer pointId) {
        boolean occupied = false;
        long key = MapKeyUtil.compositeKey(mapId, pointId);
        RcsPointOccupy rcsOccupy = occupyMap.get(key);
        if (rcsOccupy != null) {
            occupied = rcsOccupy.isPhysicalBlocked();
        }

        return occupied;
    }

    /**
     * 设置占用类型
     * <p>
     * 如果占用类型已经存在，直接返回，不做操作
     *
     * @param deviceCode 占用设备
     * @param rcsPoint   地图点位
     * @param occupyType 占用类型
     */
    public boolean addOccupyType(String deviceCode, RcsPoint rcsPoint, PointOccupyTypeEnum occupyType) {
        if (snapshot.getGraphId(rcsPoint.getMapId(), rcsPoint.getId()) == null) {
            RcsLog.sysLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("地图点位 [{}] 获取不到 RcsPointOccupy 数据", rcsPoint));
            return false;
        }

        RcsPointOccupy rcsOccupy = getPointOccupy(rcsPoint.getMapId(), rcsPoint.getId());
        if (rcsOccupy == null) {
            RcsLog.sysLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("地图点位 [{}] 获取不到 RcsPointOccupy 数据", rcsPoint));
            return false;
        }
        // 设置占用类型
        return getPointOccupy(rcsPoint.getMapId(), rcsPoint.getId()).tryOccupied(deviceCode, occupyType);
    }

    /**
     * 批量添加占用类型 (统一返回 boolean)
     * <p>
     * 场景：AGV 规划出一组路径后，直接调用此方法将路径上的所有点标记为占用。
     * </p>
     *
     * @param deviceCode 占用设备
     * @param points     点位列表
     * @param type       占用类型
     * @return true=所有点位均设置成功; false=存在无效点位(未找到)或列表为空
     */
    public boolean addOccupyType(String deviceCode, List<RcsPoint> points, PointOccupyTypeEnum type) {
        if (points == null || points.isEmpty()) {
            return false;
        }

        boolean allSuccess = true;
        for (RcsPoint point : points) {
            // 复用单点逻辑
            // 只要有一个失败，整体标记为 false，但我们依然尝试设置后续的点 (Best Effort)
            if (!addOccupyType(deviceCode, point, type)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 强制设置点位占用
     *
     * @param deviceCode 占用设备
     * @param rcsPoint   地图点位
     * @param occupyType 占用类型
     */
    public void setOccupyType(String deviceCode, RcsPoint rcsPoint, PointOccupyTypeEnum occupyType) {
        RcsPointOccupy rcsOccupy = getPointOccupy(rcsPoint.getMapId(), rcsPoint.getId());
        if (rcsOccupy != null) {
            rcsOccupy.setOccupied(deviceCode, occupyType);
        }
    }

    /**
     * 移除占用类型
     *
     * @param deviceCode 设备/任务编号
     * @param rcsPoint   地图点位
     * @param type       占用类型
     * @return true=操作成功, false=点位不存在
     */
    public boolean removeOccupyType(String deviceCode, RcsPoint rcsPoint, PointOccupyTypeEnum type) {
        long key = MapKeyUtil.compositeKey(rcsPoint.getMapId(), rcsPoint.getId());
        RcsPointOccupy rcsOccupy = occupyMap.get(key);
        if (rcsOccupy != null) {
            return rcsOccupy.release(deviceCode, type);
        }
        return false;
    }

    /**
     * 批量移除占用类型
     * <p>
     * 场景：批量释放路径。
     * </p>
     *
     * @param deviceCode 设备编号
     * @param points     点位列表
     * @param type       占用类型
     * @return true=状态发生变更(至少释放了一个点); false=无事发生(原本就没锁)
     */
    public boolean removeOccupyType(String deviceCode, List<RcsPoint> points, PointOccupyTypeEnum type) {
        if (points == null || points.isEmpty()) {
            return false;
        }

        boolean anyChanged = false;
        for (RcsPoint point : points) {
            // 复用单点逻辑
            // 只要有一个点状态变了，整体返回 true
            if (removeOccupyType(deviceCode, point, type)) {
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    // ================== 5. 图算法查询 ==================

    /**
     * 获取全量有向图
     */
    public Digraph<RcsPoint, RcsPointTarget> getGraph() {
        return snapshot.graph();
    }

    /**
     * 获取指定点位的所有出边
     *
     * @param mapId   地图编号
     * @param pointId 点位编号
     * @return 出边列表
     */
    public List<RcsPointTarget> getOutgoingEdges(Integer mapId, Integer pointId) {
        MapSnapshot localSnap = this.snapshot;
        // 1. 业务ID -> 算法ID
        Integer u = localSnap.getGraphId(mapId, pointId);
        if (u == null) {
            return Collections.emptyList();
        }

        List<RcsPointTarget> targets = new ArrayList<>();
        Digraph<RcsPoint, RcsPointTarget> graph = localSnap.graph();

        // 2. 获取关联边 (Graph4J 返回的是 Edge<E>[] 数组)
        Edge<RcsPointTarget>[] incidentEdges = graph.edgesOf(u);

        // 3. 判空 (虽然 Graph4J 通常返回空数组而不是 null，但防御性编程更安全)
        if (incidentEdges != null) {
            // 4. 使用增强 For 循环遍历数组 (不能用 .forEach)
            for (Edge<RcsPointTarget> edge : incidentEdges) {
                // 确保是"出边" (source == u)
                if (edge.source() == u) {
                    // 5. 获取 Label (注意是方法调用 .label())
                    RcsPointTarget target = edge.label();
                    if (target != null) {
                        targets.add(target);
                    }
                }
            }
        }
        return targets;
    }

    /**
     * 获取地图的点位对象
     * <p>
     * 本方法通过点ID从点位有向权重图缓存中检索对应的点位信息。此方法用于快速访问已知ID的点位
     *
     * @param mapId 地图编号
     * @param id    点位编号
     * @return 返回与给定ID匹配的点位对象。如果找不到匹配的点，则返回null
     */
    public RcsPoint getRcsPoint(Integer mapId, Integer id) {
        if (mapId == null || id == null) {
            return null;
        }
        MapSnapshot localSnap = this.snapshot;
        // 1. 业务ID -> 算法ID
        Integer vertexIndex = localSnap.getGraphId(mapId, id);
        if (vertexIndex == null) {
            return null;
        }

        Digraph<RcsPoint, RcsPointTarget> rcsGraph = localSnap.graph();
        if (rcsGraph == null || rcsGraph.isEmpty()) {
            return null;
        }
        // 2. 使用算法ID查询
        return rcsGraph.getVertexLabel(vertexIndex);
    }

    /**
     * 获取点位目标
     *
     * @param mapId    地图编号
     * @param currenId 当前点位编号
     * @param nextId   下一个点位编号
     * @return 点位目标。如果找不到匹配的点，则返回null
     */
    public RcsPointTarget getRcsPointTarget(Integer mapId, Integer currenId, Integer nextId) {
        if (mapId == null || currenId == null || nextId == null) {
            return null;
        }
        MapSnapshot localSnap = this.snapshot;

        // 1. 业务ID -> 算法ID
        Integer u = localSnap.getGraphId(mapId, currenId);
        Integer v = localSnap.getGraphId(mapId, nextId);

        if (u == null || v == null) {
            return null;
        }

        Digraph<RcsPoint, RcsPointTarget> rcsGraph = localSnap.graph();
        if (rcsGraph == null || rcsGraph.isEmpty()) {
            return null;
        }
        // 2. 使用算法ID查询
        return rcsGraph.getEdgeLabel(u, v);
    }

    /**
     * 纯净版 BFS 搜索：查找指定层级内的所有邻居点
     * <p>
     * 1. 仅仅基于图的拓扑结构进行搜索。
     * 2. 不做任何业务过滤（不看占用、不看类型）。
     * 3. 支持双向搜索（出边 + 入边），确保不漏掉任何物理相邻的点。
     * </p>
     *
     * @param startMapId   起点地图
     * @param startPointId 起点点位
     * @param tiers        搜索层级 (深度)
     * @return 附近的点位列表 (包含起点本身)
     */
    public List<RcsPoint> findNearbyPoints(Integer startMapId, Integer startPointId, int tiers) {
        MapSnapshot localSnap = this.snapshot;
        // 1. 入口转换
        Integer startNode = localSnap.getGraphId(startMapId, startPointId);
        // 如果起点不存在，或者层级为负，直接返回空
        if (startNode == null || tiers < 0) {
            return Collections.emptyList();
        }

        Digraph<RcsPoint, RcsPointTarget> graph = localSnap.graph();
        List<RcsPoint> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Queue<Map.Entry<Integer, Integer>> queue = new LinkedList<>();

        // 2. 初始化队列
        queue.offer(new AbstractMap.SimpleEntry<>(startNode, 0));
        visited.add(startNode);

        // 3. 添加起点 (无论起点状态如何，它是搜索的中心)
        RcsPoint startPoint = graph.getVertexLabel(startNode);
        if (startPoint != null) {
            result.add(startPoint);
        }

        while (!queue.isEmpty()) {
            Map.Entry<Integer, Integer> current = queue.poll();
            int nodeId = current.getKey();
            int level = current.getValue();

            // 如果达到最大层级，停止向下裂变，但当前层的点已经处理过了
            if (level >= tiers) {
                continue;
            }

            // --- 搜索策略：双向遍历 (出边 + 入边) ---

            // A. 正向邻居 (出边, Downstream)
            NeighborIterator<RcsPointTarget> outIt = graph.neighborIterator(nodeId);
            while (outIt.hasNext()) {
                int nextId = outIt.next();
                processNeighbor(graph, nextId, level + 1, visited, queue, result);
            }

            // B. 反向邻居 (入边, Upstream)
            // 补齐旧版逻辑，防止漏掉"上游"的点 (对于支持双向行驶或倒车的AGV很重要)
            var inIt = graph.predecessorIterator(nodeId);
            while (inIt.hasNext()) {
                int prevId = inIt.next();
                processNeighbor(graph, prevId, level + 1, visited, queue, result);
            }
        }
        return result;
    }

    /**
     * 内部辅助方法：处理邻居节点
     */
    private void processNeighbor(Digraph<RcsPoint, RcsPointTarget> graph, int nodeId, int nextLevel,
                                 Set<Integer> visited, Queue<Map.Entry<Integer, Integer>> queue,
                                 List<RcsPoint> result) {
        if (visited.add(nodeId)) {
            // 只要图里有，就加入结果，完全不进行状态判断
            RcsPoint point = graph.getVertexLabel(nodeId);
            if (point != null) {
                result.add(point);
            }
            // 加入队列继续下一层
            queue.offer(new AbstractMap.SimpleEntry<>(nodeId, nextLevel));
        }
    }

    // ================== 6. 地图加载逻辑 ==================

    /**
     * 运行时异步热更新
     */
    public void reloadAsync() {
        vthreadPool.execute(() -> {
            if (reloadLock.tryLock()) {
                try {
                    loadInternal(false);
                } finally {
                    reloadLock.unlock();
                }
            } else {
                RcsLog.consoleLog.warn("地图正在更新中，忽略本次并发刷新请求");
            }
        });
    }

    /**
     * 内部核心加载逻辑 (提取公共代码)
     *
     * @param force true=强制覆盖; false=检查MD5
     */
    private void loadInternal(boolean force) {
        try {
            // 1. 获取 MapLoader 生产好的快照 (包含 occupys)
            MapSnapshot newSnap = mapLoader.load();
            if (newSnap.pointMap().isEmpty()) {
                RcsLog.consoleLog.error("地图加载为空，保持原地图不变");
                return;
            }

            if (!force && newSnap.versionMd5().equals(snapshot.versionMd5())) {
                RcsLog.consoleLog.info("地图文件无变更，忽略更新");
                return;
            }

            // 2. 状态合并 (State Merge)
            // 策略：putIfAbsent。
            // 如果 key 已存在 (旧地图有)，则保留旧值 (保留锁状态)。
            // 如果 key 不存在 (新地图新增)，则放入 MapLoader 创建好的新值。
            newSnap.occupys().forEach(occupyMap::putIfAbsent);

            // 3. 状态清理 (State Cleanup)
            // 移除那些在新地图中已经不存在的点位 (防止内存泄漏)
            // 使用 newSnap 的 occupys 作为"当前有效点位"的判断依据
            occupyMap.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                RcsPointOccupy state = entry.getValue();

                // 如果新地图里没有这个 Key
                if (!newSnap.occupys().containsKey(key)) {
                    // 只有当该点位 "没有物理阻塞" 时才安全移除
                    // 保护机制：防止删除正在被 AGV 占用的点导致程序崩溃
                    return !state.isPhysicalBlocked();
                }
                return false;
            });

            // 4. 切换快照
            this.snapshot = newSnap;
            RcsLog.consoleLog.info("MapManager 地图同步完毕 Points: {}", newSnap.pointMap().size());
            RcsLog.consoleLog.info("MapManager 地图加载完成 当前地图指纹: {}", newSnap.versionMd5());
        } catch (Exception e) {
            RcsLog.consoleLog.error("地图加载严重失败", e);
            // 如果是启动阶段失败，这里可以抛出异常让系统停机，看业务需求
            if (force) {
                throw new RuntimeException("系统启动阶段地图加载失败", e);
            }
        }
    }
}