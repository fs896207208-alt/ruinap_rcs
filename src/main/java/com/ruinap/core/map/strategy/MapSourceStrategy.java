package com.ruinap.core.map.strategy;


import java.util.Map;

/**
 * 地图数据源策略接口
 *
 * @author qianye
 * @create 2025-01-15 10:05
 */
public interface MapSourceStrategy {
    /**
     * 获取地图 json 字符串
     *
     * @return 地图 json 字符串
     */
    Map<Integer, String> loadRawData();
}
