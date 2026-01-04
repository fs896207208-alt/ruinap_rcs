package com.ruinap.core.map.factory;


import com.ruinap.core.map.strategy.FileMapSourceStrategy;
import com.ruinap.core.map.strategy.MapSourceStrategy;

/**
 * 地图工厂类，根据输入的类型创建不同的地图获取策略对象
 *
 * @author qianye
 * @create 2025-01-15 13:07
 */
public class MapFactory {

    /**
     * 根据类型创建不同的地图获取策略对象
     *
     * @param type 类型
     * @return 策略对象
     */
    public static MapSourceStrategy createStrategy(String type) {
        switch (type) {
            case "file":
                return new FileMapSourceStrategy();
            default:
                throw new IllegalArgumentException("未实现的地图获取方式: " + type);
        }
    }
}
