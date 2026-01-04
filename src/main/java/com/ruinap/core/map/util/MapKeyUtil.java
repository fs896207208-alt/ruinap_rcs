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
     * 结构: [ MapId (高32位) | PointId (低32位) ]
     * 优势: 纯 CPU 寄存器操作，无堆内存分配，无 GC。
     * 限制: mapId 和 pointId 必须在 int 范围内 (足够 AGV 场景使用)。
     * </p>
     */
    public static long compositeKey(int mapId, int pointId) {
        // mapId 放高32位，pointId 放低32位
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
     * <p>
     * 替代了原有的 String 拼接 key。
     * Record 在 Map 中的查找性能远高于 String，因为没有字符数组的遍历比对。
     * </p>
     */
    public record EdgeKey(int fromMapId, int fromPointId, int toMapId, int toPointId) {
        // Record 自动实现了最高效的 equals 和 hashCode
    }

    /**
     * 生成边 Key
     * * @return EdgeKey 对象 (比 String 对象更轻量)
     */
    public static EdgeKey edgeKey(Integer fromMapId, Integer fromPointId, Integer toMapId, Integer toPointId) {
        // 自动拆箱防御
        int fMap = fromMapId == null ? 0 : fromMapId;
        int fPt = fromPointId == null ? 0 : fromPointId;
        int tMap = toMapId == null ? 0 : toMapId;
        int tPt = toPointId == null ? 0 : toPointId;
        return new EdgeKey(fMap, fPt, tMap, tPt);
    }

    // ================== 3. 辅助方法 ==================

    /**
     * 仅在打印日志时才转换为 String，避免核心逻辑中的拼接
     */
    public static String toString(long compositeKey) {
        return parseMapId(compositeKey) + "_" + parsePointId(compositeKey);
    }
    
}
