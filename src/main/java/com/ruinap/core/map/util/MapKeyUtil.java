package com.ruinap.core.map.util;

import cn.hutool.core.util.StrUtil;

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

    /**
     * 生成点位组合键
     *
     * @param mapId   地图编号
     * @param pointId 点位编号
     * @return 格式 "mapId_pointId" (e.g. "101_5")
     */
    public static String compositeKey(int mapId, int pointId) {
        return StrUtil.format("{}_{}", mapId, pointId);
    }

    /**
     * 生成边(Edge)组合键
     * 用于唯一标识一条有向边，存储导航数据。
     *
     * @return 格式 "fromMap_fromPt_toMap_toPt"
     */
    public static String edgeKey(Integer fromMapId, Integer fromPointId, Integer toMapId, Integer toPointId) {
        return StrUtil.format("{}_{}_{}_{}", fromMapId, fromPointId, toMapId, toPointId);
    }
}
