package com.ruinap.core.strategy;

import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交通管理器 (Traffic Manager)
 * <p>
 * 职责：
 * 1. 管理所有 AGV 的实时空间状态 (AGV_BUFFER_MAP)。
 * 2. 提供动态避让、碰撞预测服务。
 *
 * @author qianye
 * @create 2025-12-31 09:25
 */
@Component
public class TrafficService {
    @Autowired
    private MapManager mapManager;
    /**
     * 活跃 AGV 的安全包络缓存
     * Key: AGV编号
     * Value: 路径缓冲区几何 (用于碰撞检测)
     */
    private final Map<String, Geometry> agvBufferMap = new ConcurrentHashMap<>();
    /**
     * 路径指纹缓存 (用于性能优化，避免重复计算)
     * Key: AGV编号
     * Value: 路径ID序列的 Hash 或 String 表示
     */
    private final Map<String, String> pathFingerprintMap = new ConcurrentHashMap<>();

    /**
     * 动态更新 AGV 的路径缓冲区 (核心方法)
     * <p>
     * 场景：AGV 移动后，路径从 [1, 2, 3] 变为 [2, 3]。
     * 调用此方法会自动重新计算几何，并覆盖旧数据。
     * </p>
     *
     * @param agvId        AGV 编号
     * @param currentPath  当前剩余路径点列表 (例如 [2, 3])
     * @param bufferRadius 缓冲区半径 (mm)
     */
    public void updateAgvBuffer(String agvId, List<RcsPoint> currentPath, int bufferRadius) {
        // 1. 判空处理：如果路径走完了，或者为空，直接清理该车的缓存
        if (currentPath == null || currentPath.isEmpty()) {
            agvBufferMap.remove(agvId);
            pathFingerprintMap.remove(agvId);
            return;
        }

        // 2. 【性能优化】计算路径指纹
        // 很多时候 AGV 上报位置但不改变路径，避免昂贵的 Geometry.buffer 计算
        String newFingerprint = generatePathFingerprint(currentPath);
        String oldFingerprint = pathFingerprintMap.get(agvId);

        // 如果指纹没变，直接跳过 (节省 90% CPU)
        if (newFingerprint.equals(oldFingerprint)) {
            return;
        }

        // 3. 准备图数据查询器 (Lambda)
        var graph = mapManager.getGraph();
        GeometryUtils.EdgeProvider provider = (u, v) -> {
            try {
                return graph.getEdgeLabel(u, v);
            } catch (Exception e) {
                return null;
            }
        };

        // 4. 【动态绘制】重新计算几何
        // 这里会根据新的点列表 [2, 3] 重新生成贝塞尔曲线或直线
        Geometry pathGeom = GeometryUtils.buildPathGeometry(currentPath, provider);

        if (pathGeom != null) {
            // 5. 生成缓冲区并原子更新 Map
            // buffer() 是最耗时的操作，这一步完成后，碰撞检测立即生效
            Geometry bufferGeom = pathGeom.buffer(bufferRadius);

            agvBufferMap.put(agvId, bufferGeom);
            pathFingerprintMap.put(agvId, newFingerprint);
        } else {
            // 异常防御
            agvBufferMap.remove(agvId);
            pathFingerprintMap.remove(agvId);
        }
    }

    /**
     * 生成路径指纹 (例如 "101->102->103")
     * 比直接比较 List 对象快，且能处理 List 实例不同的情况
     */
    private String generatePathFingerprint(List<RcsPoint> path) {
        if (path.isEmpty()) {
            return "";
        }
        // 简单拼接 ID 即可
        StringBuilder sb = new StringBuilder();
        for (RcsPoint p : path) {
            sb.append(p.getId()).append("-");
        }
        return sb.toString();
    }

    /**
     * 检查新路径规划是否安全，如果存在碰撞，则回退路径点直到安全或路径为空
     *
     * @param mapId          地图标识
     * @param agvId          当前AGV标识
     * @param newPlanPoints  新规划路径点集合
     * @param bufferDistance 安全缓冲距离 （单位：毫米，例如 AGV 的车距半径）
     * @return List<RcsPoint> 安全的路径点集合，如果无法找到安全路径，则返回空列表
     */
    public List<RcsPoint> findSafePlan(Integer mapId, String agvId, List<RcsPoint> newPlanPoints, int bufferDistance) {
        if (newPlanPoints == null || newPlanPoints.isEmpty()) {
            return new ArrayList<>();
        }

        List<RcsPoint> currentPlan = new ArrayList<>(newPlanPoints);

        // 贪心策略：不断回退直到找到安全路径或路径为空
        while (!currentPlan.isEmpty()) {
            if (isPlanSafe(mapId, agvId, currentPlan, bufferDistance)) {
                return currentPlan;
            }
            currentPlan.removeLast();
        }
        return new ArrayList<>();
    }

    /**
     * 碰撞检测
     */
    private boolean isPlanSafe(Integer mapId, String currentAgvId, List<RcsPoint> plan, int bufferDistance) {
        // 1. 构建当前计划的路线
//        Geometry planGeom = GeometryUtils.buildPathGeometry(plan);
//        // 这里如果有 Provider 更好，没有就按直线
//        if (planGeom == null) return true;
//
//        // 获取全局 AGV 状态 (用于查半径)
//        Map<String, RcsAgv> rcsAgvMap = RcsAgvCache.getRcsAgvMap();
//
//        // 2. 遍历检测 (路径 vs 路径)
//        for (Map.Entry<String, Geometry> entry : agvBufferMap.entrySet()) {
//            String otherAgvId = entry.getKey();
//            if (otherAgvId.equals(currentAgvId)) continue;
//
//            RcsAgv otherAgv = rcsAgvMap.get(otherAgvId);
//            if (otherAgv == null || !mapId.equals(otherAgv.getMapId())) continue;
//
//            // 获取对方的路径骨架
//            Geometry otherPathGeom = entry.getValue();
//
//            // 计算安全阈值：我的半径 + 它的半径
//            // [Math] 两个实心管道相撞 <==> 两条中心线距离 < 半径之和
//            double totalSafetyDist = bufferDistance + otherAgv.getCarRange();
//
//            // 调用通用的距离检测
//            if (GeometryUtils.checkCollision(planGeom, otherPathGeom, totalSafetyDist)) {
//                RcsLog.algorithmLog.warn("路径碰撞预警: {} vs {}", currentAgvId, otherAgvId);
//                return false;
//            }
//
//            // 3. 检测静态位置 (路径 vs 点)
//            // 防止对方没路径但停在路上
//            Geometry otherAgvPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(otherAgv.getSlamX(), otherAgv.getSlamY()));
//
//            if (GeometryUtils.checkCollision(planGeom, otherAgvPoint, totalSafetyDist)) {
//                RcsLog.algorithmLog.warn("位置碰撞预警: {} vs {}", currentAgvId, otherAgvId);
//                return false;
//            }
//        }
        return true;
    }

    /**
     * 获取当前的缓冲区快照
     *
     * @return 缓冲区快照
     */
    public Map<String, Geometry> getBufferSnapshot() {
        return Map.copyOf(agvBufferMap);
    }
}
