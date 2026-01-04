package com.ruinap.core.map.pojo;

import cn.hutool.core.annotation.Alias;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 地图点位包装类
 *
 * @author qianye
 * @create 2025-06-24 10:24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class RcsPoint extends AbstractPoint {

    /**
     * 全局图索引
     * <p>
     * 1. transient: 不需要序列化到 Redis 或 磁盘，仅内存运行时有效。
     * 2. volatile: 配合新架构的可见性（虽然通常构建完就不变了，但在构造期间安全）。
     * </p>
     */
    private transient int graphIndex = -1;

    /**
     * 该点位通往其他点位的连接信息
     */
    @Alias("targets")
    private List<RcsPointTarget> targets;

    /**
     * 自定义toString方法
     *
     * @return 自定义字符串
     */
    @Override
    public String toString() {
        return StrUtil.format("{}-{}", super.getMapId(), super.getId());
    }
}
