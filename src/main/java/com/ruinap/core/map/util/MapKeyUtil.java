package com.ruinap.core.map.util;

/**
 * <h1>地图键生成工具</h1>
 * <p>
 * <strong>设计意图：</strong>
 * 解决多地图场景下点位 ID 重复的问题（如 Map1 和 Map2 都有 ID=1 的点）。
 * 通过生成唯一的组合字符串键，确保全局唯一性。
 * </p>
 *
 * @author qianye
 * @create 2025-12-23 16:15
 */
public class MapKeyUtil {

    // ================== 1. 点位 Key (极致性能: long) ==================

    /**
     * 生成点位组合键 (压缩为 long)
     * <p>
     * 适用于内部核心算法（A*搜索、死锁检测）。
     * 注意：若对接 VDA5050，需在协议适配层维护 String nodeId 到此 int pointId 的映射。
     * </p>
     *
     * @param mapId   地图ID
     * @param pointId 点位ID (允许负数，通过位掩码保护)
     * @return 64位唯一键
     */
    public static long compositeKey(int mapId, int pointId) {
        // mapId 放高32位，pointId 放低32位 (使用 0xFFFFFFFFL 确保低位视为无符号拼接)
        return ((long) mapId << 32) | (pointId & 0xFFFFFFFFL);
    }

    /**
     * 解析 long key 获取 MapId
     */
    public static int parseMapId(long key) {
        return (int) (key >>> 32);
    }

    /**
     * 解析 long key 获取 PointId
     */
    public static int parsePointId(long key) {
        return (int) key;
    }

    // ================== 2. 边 Key (JDK 21 Record) ==================

    /**
     * 边(Edge)唯一标识 Record
     * JDK 21 Record 在堆内存中布局更紧凑，且 hashCode 计算针对不可变数据做了优化。
     */
    public record EdgeKey(int fromMapId, int fromPointId, int toMapId, int toPointId) {
        //Compact constructor 进行校验（可选，视性能要求而定）
        public EdgeKey {
            // 在调度系统中，通常不建议包含负数ID（除非是特殊的虚拟点），此处保留灵活性暂不报错
        }
    }

    /**
     * 生成边 Key
     * 直接传递基本类型，避免拆装箱
     */
    public static EdgeKey edgeKey(int fromMapId, int fromPointId, int toMapId, int toPointId) {
        return new EdgeKey(fromMapId, fromPointId, toMapId, toPointId);
    }

    // ================== 3. 辅助方法 ==================

    /**
     * 仅在打印日志时才转换为 String，避免核心逻辑中的拼接
     */
    public static String toString(long compositeKey) {
        return parseMapId(compositeKey) + "_" + parsePointId(compositeKey);
    }

}
