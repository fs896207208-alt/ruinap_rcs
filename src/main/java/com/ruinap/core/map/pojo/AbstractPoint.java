package com.ruinap.core.map.pojo;

import cn.hutool.core.annotation.Alias;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * 地图点位
 *
 * @author qianye
 * @create 2024-11-20 17:24
 */
@Data
@EqualsAndHashCode(of = {"id", "mapId", "floor"}, callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
public abstract class AbstractPoint implements Serializable {
    /**
     * 编号
     */
    @ToString.Include
    private int id;
    /**
     * 名称
     */
    private String name;
    /**
     * 地图编号
     */
    @ToString.Include
    @Alias("map_id")
    private int mapId;
    /**
     * 楼层
     */
    private int floor;
    /**
     * X坐标
     */
    private int x;
    /**
     * Y坐标
     */
    private int y;
    /**
     * 区域编码
     */
    @Alias("area_code")
    private String areaCode;
    /**
     * 动作参数
     */
    @Alias("action_param")
    private List<PointActionParam> actionParam;
    /**
     * 是待机点
     */
    private boolean standby;
    /**
     * 是充电点
     */
    private boolean charge;
    /**
     * 是取货点
     */
    private boolean loading;
    /**
     * 是卸货点
     */
    private boolean unloading;
    /**
     * 是自动门对接点
     */
    private boolean door;

    /**
     * 是否是取卸货点
     *
     * @return true:是取卸货点 false:不是取卸货点
     */
    public boolean isStation() {
        return loading || unloading;
    }
}
