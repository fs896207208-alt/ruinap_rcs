package com.ruinap.core.map.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MapKeyUtil 单元测试
 * 覆盖位运算边界、负数处理及 Record 特性
 *
 * @author qianye
 * @create 2026-01-04 17:19
 */
@DisplayName("地图键生成工具测试")
class MapKeyUtilTest {

    @Test
    @DisplayName("测试点位Key生成与解析 - 常规正数")
    void testCompositeKeyNormal() {
        int mapId = 1001;
        int pointId = 500;
        long key = MapKeyUtil.compositeKey(mapId, pointId);

        // JUnit 6 注意：Message 参数移到了最后！
        // Assertions.assertEquals(expected, actual, message)
        Assertions.assertEquals(mapId, MapKeyUtil.parseMapId(key), "MapId 解析错误");
        Assertions.assertEquals(pointId, MapKeyUtil.parsePointId(key), "PointId 解析错误");
    }

    @Test
    @DisplayName("测试点位Key - 边界值(MAX_VALUE)")
    void testCompositeKeyBoundary() {
        // 测试 Integer.MAX_VALUE 边界
        int mapId = Integer.MAX_VALUE;
        int pointId = Integer.MAX_VALUE;
        long key = MapKeyUtil.compositeKey(mapId, pointId);

        Assertions.assertEquals(mapId, MapKeyUtil.parseMapId(key));
        Assertions.assertEquals(pointId, MapKeyUtil.parsePointId(key));
    }

    @Test
    @DisplayName("测试点位Key - 负数ID支持")
    void testCompositeKeyNegative() {
        // 测试负数 ID 支持 (模拟 Hash 碰撞或虚拟点)
        int mapId = -1;
        int pointId = -99;
        long key = MapKeyUtil.compositeKey(mapId, pointId);

        Assertions.assertEquals(mapId, MapKeyUtil.parseMapId(key));
        Assertions.assertEquals(pointId, MapKeyUtil.parsePointId(key));
    }

    @Test
    @DisplayName("测试 EdgeKey Record 的不可变性与相等性")
    void testEdgeKeyEquality() {
        // Record 在 Java 21 下表现优秀
        // 使用 var 简化类型推断 (Java 10+)
        var key1 = MapKeyUtil.edgeKey(1, 10, 1, 11);
        var key2 = MapKeyUtil.edgeKey(1, 10, 1, 11);
        var key3 = MapKeyUtil.edgeKey(1, 10, 2, 11);

        Assertions.assertEquals(key1, key2, "相同参数生成的Record对象应当相等");
        Assertions.assertEquals(key1.hashCode(), key2.hashCode(), "相同参数生成的Record对象hashCode应当相等");
        Assertions.assertNotEquals(key1, key3, "不同参数生成的对象不应相等");
    }

    @Test
    @DisplayName("测试 EdgeKey 空值校验 (Fail Fast)")
    void testEdgeKeyNullSafety() {
        // JUnit 6 标准异常断言写法
        // 期望抛出 NullPointerException
        Assertions.assertThrows(NullPointerException.class, () -> {
            MapKeyUtil.edgeKey(0, 1, 1, 1);
        }, "传递 Null 应当抛出异常，禁止静默失败");
    }
}