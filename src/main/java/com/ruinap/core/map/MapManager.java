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
     * 设备占用索引
     * Key: deviceCode
     * Value: RcsPointOccupy
     */
    private final Map<String, Set<RcsPointOccupy>> deviceOccupyIndex = new ConcurrentHashMap<>();

    /**
     * 热更新互斥锁，防止多个配置变更事件同时触发重载
     */
    private final Lock reloadLock = new ReentrantLock();

    // ================== 2. 生命周期管理 ==================

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

    // ================== 3. 动态状态操作 ==================

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
        // 捕获当前引用的快照 (局部变量)
        MapSnapshot localSnap = this.snapshot;
        if (localSnap == null) {
            return null;
        }
        // 既然 loadInternal 保证了集合完整性，这里直接 get 即可
        // 甚至不需要查 snapshot.getGraphId 做前置校验，因为 occupyMap 的 Key 集合是 snapshot 的子集(或超集)
        return localSnap.occupys().get(MapKeyUtil.compositeKey(mapId, pointId));
    }

    /**
     * 获取点位占用对象
     * <p>不存在的点位直接返回 null</p>
     *
     * @param key 点位 Key
     * @return 状态对象 或 null
     */
    public RcsPointOccupy getPointOccupy(Long key) {
        if (key == null) {
            return null;
        }
        // 捕获当前引用的快照 (局部变量)
        MapSnapshot localSnap = this.snapshot;
        if (localSnap == null) {
            return null;
        }
        // 既然 loadInternal 保证了集合完整性，这里直接 get 即可
        // 甚至不需要查 snapshot.getGraphId 做前置校验，因为 occupyMap 的 Key 集合是 snapshot 的子集(或超集)
        return localSnap.occupys().get(key);
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
     *
     * @param mapId   地图编号
     * @param pointId 点位编号
     * @return true=点位被占用; false=点位未被占用
     */
    public boolean getPointOccupyState(Integer mapId, Integer pointId) {
        boolean occupied = false;
        RcsPointOccupy rcsOccupy = getPointOccupy(mapId, pointId);
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
        RcsPointOccupy rcsOccupy = getPointOccupy(rcsPoint.getMapId(), rcsPoint.getId());
        if (rcsOccupy == null) {
            RcsLog.sysLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("地图点位 [{}] 获取不到 RcsPointOccupy 数据", rcsPoint));
            return false;
        }
        // 设置占用类型
        boolean flag = rcsOccupy.tryOccupied(deviceCode, occupyType);
        if (flag) {
            deviceOccupyIndex.computeIfAbsent(deviceCode, k -> ConcurrentHashMap.newKeySet())
                    .add(rcsOccupy);
        }
        return flag;
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

        // 1. 事务日志：记录本次操作成功锁定的点，用于失败时回滚
        // 预设大小避免扩容开销
        List<RcsPoint> lockedPoints = new ArrayList<>(points.size());
        boolean transactionSuccess = true;

        for (RcsPoint point : points) {
            // 2. 尝试锁定单个点
            // 调用现有的单点锁定逻辑
            boolean success = addOccupyType(deviceCode, point, type);

            if (success) {
                // 锁定成功，记入“账本”
                lockedPoints.add(point);
            } else {
                // 3. 遇到失败：标记事务失败，并立即停止后续尝试
                transactionSuccess = false;
                // 日志记录：这对排查“车为什么停住”非常关键
                if (RcsLog.algorithmLog.isInfoEnabled()) {
                    RcsLog.algorithmLog.info("路径申请受阻，触发回滚。Device: {}, BlockedAt: {}", deviceCode, point.getId());
                }
                break;
            }
        }

        // 4. 如果事务失败，执行补偿操作 (回滚)
        if (!transactionSuccess) {
            rollbackLockedPoints(deviceCode, lockedPoints, type);
            return false;
        }

        return true;
    }

    /**
     * 内部辅助方法：回滚已锁定的点位 (补偿事务)
     *
     * @param deviceCode   设备ID
     * @param lockedPoints 本次事务中已成功锁定的点位集合
     * @param type         占用类型
     */
    private void rollbackLockedPoints(String deviceCode, List<RcsPoint> lockedPoints, PointOccupyTypeEnum type) {
        if (lockedPoints.isEmpty()) {
            return;
        }

        // 建议倒序释放 (LIFO)，虽然在无死锁检测算法中顺序可能不敏感，
        // 但倒序释放符合栈的逻辑，能最大程度减少与其他线程锁竞争的复杂度。
        for (int i = lockedPoints.size() - 1; i >= 0; i--) {
            RcsPoint point = lockedPoints.get(i);
            try {
                // 调用现有的释放逻辑
                // 注意：这里必须确保 removeOccupyType 是幂等的或安全的（当前代码是安全的）
                removeOccupyType(deviceCode, point, type);
            } catch (Exception e) {
                // 5. 异常防御：回滚过程绝对不能抛出异常中断，否则会造成永久的“幽灵锁”
                // 这里必须 catch 住所有异常并打印 ERROR 日志
                RcsLog.sysLog.error("CRITICAL: 回滚路径锁定时发生异常，可能导致点位永久占用! Point: {}", point.getId(), e);
            }
        }
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
            //记录占用点位
            deviceOccupyIndex.computeIfAbsent(deviceCode, k -> ConcurrentHashMap.newKeySet())
                    .add(rcsOccupy);
        }
    }

    /**
     * 【核心业务】更新驻留占用 (原子性：查重 -> 锁新 -> 清旧)
     * <p>
     * <strong>功能定义：</strong>
     * 1. 幂等性检查：如果已经在目标点位持有目标类型的锁，直接返回。
     * 2. 尝试以目标类型 (PARK 或 OFFLINE) 锁定新点位。
     * 3. 锁定成功后，自动清理该设备持有的所有旧的 PARK 和 OFFLINE 锁。
     * </p>
     *
     * @param deviceCode 设备编号
     * @param newPoint   目标点位
     * @param targetType 目标占用类型 (通常是 PARK 或 OFFLINE)
     * @return true=更新成功(或已处于该状态); false=目标点位无法锁定
     */
    public boolean updateParkOccupy(String deviceCode, RcsPoint newPoint, PointOccupyTypeEnum targetType) {
        // 1. 基础校验
        if (deviceCode == null || newPoint == null || targetType == null) {
            return false;
        }

        // 2. 获取新点位的 Occupy 对象
        RcsPointOccupy newOccupy = getPointOccupy(newPoint.getMapId(), newPoint.getId());
        if (newOccupy == null) {
            RcsLog.sysLog.warn("更新驻留状态失败，目标点位不存在: {}", newPoint);
            return false;
        }

        // 3. 【新增】幂等性/查重检查 (Fast Path)
        // 如果该设备在目标点位，已经持有了想要申请的锁类型 (例如已经是 PARK)，直接返回成功
        // 这一步避免了后续昂贵的“快照”和“清理”操作
        if (newOccupy.getDeviceOccupyTypes(deviceCode).contains(targetType)) {
            return true;
        }

        // 4. “快照”该设备当前的驻留锁 (查旧账)
        // 必须在加新锁之前查，否则无法区分“刚加的”和“原有的”
        Set<RcsPointOccupy> previousOccupies = new HashSet<>();
        previousOccupies.addAll(getDeviceOccupiedPoints(deviceCode, PointOccupyTypeEnum.PARK));
        previousOccupies.addAll(getDeviceOccupiedPoints(deviceCode, PointOccupyTypeEnum.OFFLINE));

        // 5. 尝试锁定新状态 (Try Lock)
        // 这一步如果失败，说明新坑位被别人占了，直接返回 false，旧锁保持不变
        boolean lockSuccess = addOccupyType(deviceCode, newPoint, targetType);

        // 6. 锁定成功，开始清理旧账 (Commit Transaction)
        if (lockSuccess) {
            for (RcsPointOccupy oldOccupy : previousOccupies) {
                // --- 情况 A: 同点位状态切换 (例如 OFFLINE -> PARK) ---
                if (oldOccupy.equals(newOccupy)) {
                    // 如果目标是 PARK，那就把旧的 OFFLINE 删掉
                    if (targetType == PointOccupyTypeEnum.PARK) {
                        removeOccupyType(deviceCode, oldOccupy, PointOccupyTypeEnum.OFFLINE);
                    }
                    // 如果目标是 OFFLINE，那就把旧的 PARK 删掉
                    else if (targetType == PointOccupyTypeEnum.OFFLINE) {
                        removeOccupyType(deviceCode, oldOccupy, PointOccupyTypeEnum.PARK);
                    }
                }
                // --- 情况 B: 位置变更 (彻底释放旧点) ---
                else {
                    removeOccupyType(deviceCode, oldOccupy, PointOccupyTypeEnum.PARK);
                    removeOccupyType(deviceCode, oldOccupy, PointOccupyTypeEnum.OFFLINE);

                    if (RcsLog.algorithmLog.isInfoEnabled()) {
                        RcsLog.algorithmLog.info("AGV[{}] 驻留点变更: 释放旧点[{}], 锁定新点[{}] 类型:{}",
                                deviceCode, oldOccupy.getPointId(), newPoint.getId(), targetType);
                    }
                }
            }
        } else {
            // 锁定失败日志
            if (RcsLog.algorithmLog.isInfoEnabled()) {
                RcsLog.algorithmLog.warn("AGV[{}] 申请驻留点[{}] 类型[{}] 失败，已被占用",
                        deviceCode, newPoint.getId(), targetType);
            }
        }

        return lockSuccess;
    }

    /**
     * 移除占用类型
     *
     * @param deviceCode 设备编号
     * @param rcsOccupy  点位占用类
     * @param type       占用类型
     * @return true=操作成功, false=点位不存在
     */
    public boolean removeOccupyType(String deviceCode, RcsPointOccupy rcsOccupy, PointOccupyTypeEnum type) {
        if (rcsOccupy != null) {
            boolean flag = rcsOccupy.release(deviceCode, type);
            // 维护占用点位
            // 只有当该设备在这个点位上没有任何锁了，才从索引中移除 Key
            if (flag && !rcsOccupy.getDeviceOccupyState(deviceCode)) {
                deviceOccupyIndex.computeIfPresent(deviceCode, (k, set) -> {
                    set.remove(rcsOccupy);
                    return set.isEmpty() ? null : set;
                });
            }
            return flag;
        }
        return false;
    }

    /**
     * 移除占用类型
     *
     * @param deviceCode 设备编号
     * @param rcsPoint   地图点位
     * @param type       占用类型
     * @return true=操作成功, false=点位不存在
     */
    public boolean removeOccupyType(String deviceCode, RcsPoint rcsPoint, PointOccupyTypeEnum type) {
        RcsPointOccupy rcsOccupy = getPointOccupy(rcsPoint.getMapId(), rcsPoint.getId());
        if (rcsOccupy != null) {
            boolean flag = rcsOccupy.release(deviceCode, type);
            // 维护占用点位
            // 只有当该设备在这个点位上没有任何锁了，才从索引中移除 Key
            if (flag && !rcsOccupy.getDeviceOccupyState(deviceCode)) {
                deviceOccupyIndex.computeIfPresent(deviceCode, (k, keys) -> {
                    keys.remove(rcsOccupy);
                    return keys.isEmpty() ? null : keys;
                });
            }
            return flag;
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

    /**
     * 获取指定设备当前占用的所有点位对象
     * <p>
     * 业务层直接调用此方法，拿到的是 RcsPointOccupy 对象集合，无需关心 Key。
     * </p>
     */
    public Set<RcsPointOccupy> getDeviceOccupiedPoints(String deviceCode) {
        // 1. 从反向索引获取 Keys
        Set<RcsPointOccupy> keys = deviceOccupyIndex.get(deviceCode);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }

        // 2. 转换为对象
        Set<RcsPointOccupy> result = new HashSet<>(keys.size());
        for (RcsPointOccupy occupy : keys) {
            // 二次确认：防止并发间隙中对象被删除了
            if (occupy != null) {
                result.add(occupy);
            }
        }
        return result;
    }

    /**
     * 【核心实现】获取指定设备、指定类型的占用点位对象
     * <p>
     * 1. 利用反向索引快速定位设备涉及的所有点位 (O(1))
     * 2. 内存过滤出包含指定占用类型的点位 (O(k), k为该设备占用的点位数，通常极小)
     * </p>
     *
     * @param deviceCode   设备编号
     * @param specificType 指定的占用类型 (如 PARK). 如果为 null，则返回该设备占用的所有点位
     * @return 符合过滤条件的点位集合
     */
    public Set<RcsPointOccupy> getDeviceOccupiedPoints(String deviceCode, PointOccupyTypeEnum specificType) {
        // 1. 从反向索引获取 Keys (快速缩小范围)
        Set<RcsPointOccupy> keys = deviceOccupyIndex.get(deviceCode);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }

        Set<RcsPointOccupy> result = new HashSet<>(keys.size());

        // 2. 遍历并过滤
        for (RcsPointOccupy occupy : keys) {

            // 防御性检查：对象可能已被移除
            if (occupy == null) {
                continue;
            }

            // 3. 核心判断逻辑
            if (specificType == null) {
                // 如果不指定类型，只要设备在这个点有任意锁，就返回
                if (occupy.getDeviceOccupyState(deviceCode)) {
                    result.add(occupy);
                }
            } else {
                // 如果指定了类型 (例如 PARK)，检查该设备在这个点位是否持有该特定类型的锁
                Set<PointOccupyTypeEnum> types = occupy.getDeviceOccupyTypes(deviceCode);
                if (types != null && types.contains(specificType)) {
                    result.add(occupy);
                }
            }
        }
        return result;
    }

    // ================== 4. 图算法查询 ==================

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

    // ================== 5. 地图加载逻辑 ==================

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
     * 内部核心加载逻辑
     * <p>
     * <b>State Migration (状态迁移)：</b>
     * 关键步骤：当新地图加载时，必须保留旧地图中依然存在的点位的【对象实例】。
     * 因为 RcsPointOccupy 内部持有 Lock，如果换了新对象，会导致正在等待锁的线程失效。
     * </p>
     */
    private void loadInternal(boolean force) {
        try {
            // 1. 加载全新的快照 (包含全新的 Graph 和全新的 RcsPointOccupy 对象)
            MapSnapshot newSnap = mapLoader.load();
            if (newSnap.pointMap().isEmpty()) {
                RcsLog.consoleLog.error("地图加载为空，保持原地图不变");
                return;
            }

            // 2. 校验版本
            if (!force && isSameVersion(newSnap, this.snapshot)) {
                RcsLog.consoleLog.info("地图文件无变更，忽略更新");
                return;
            }

            // 3. 状态迁移 (CRITICAL SECTION)
            // 获取新生成的 Occupy Map
            Map<Long, RcsPointOccupy> newOccupys = newSnap.occupys();
            // 获取当前的 Occupy Map (直接从旧快照拿)
            Map<Long, RcsPointOccupy> oldOccupys = this.snapshot.occupys();

            if (oldOccupys != null && !oldOccupys.isEmpty()) {
                // 遍历新地图的所有点位
                for (Map.Entry<Long, RcsPointOccupy> entry : newOccupys.entrySet()) {
                    Long key = entry.getKey();

                    // 检查旧地图中是否也有这个点
                    RcsPointOccupy oldOccupy = oldOccupys.get(key);

                    if (oldOccupy != null) {
                        // 【核心修复】
                        // 如果旧地图有这个点，直接把新 Map 里的 Value 替换为旧的 Occupy 对象！
                        // 这样就保留了之前的锁(Lock)和占用者(Occupants)信息。
                        newOccupys.put(key, oldOccupy);
                    }
                    // 如果旧地图没有（是新增点位），则保留 newSnap 里的新对象
                }
            }

            // 4. 原子切换 (Atomic Swap)
            // 此时 newSnap 内部已经持有了“旧锁对象”和“新点位对象”的正确组合
            this.snapshot = newSnap;

            RcsLog.consoleLog.info("MapManager 地图同步完毕 Points: {}", newSnap.pointMap().size());
            RcsLog.consoleLog.info("MapManager 地图加载完成 当前地图指纹: {}", newSnap.versionMd5());

        } catch (Exception e) {
            RcsLog.consoleLog.error("地图加载严重失败", e);
            if (force) {
                throw new RuntimeException("系统启动阶段地图加载失败", e);
            }
        }
    }

    private boolean isSameVersion(MapSnapshot newSnap, MapSnapshot oldSnap) {
        if (oldSnap == null || oldSnap.versionMd5() == null) {
            return false;
        }
        return Objects.equals(newSnap.versionMd5(), oldSnap.versionMd5());
    }
}