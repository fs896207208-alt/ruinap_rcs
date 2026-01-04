package com.ruinap.core.map.pojo;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

/**
 * 点位目标类
 *
 * @author qianye
 * @create 2025-01-14 19:34
 */
@Data
public class RcsPointTarget {

    /**
     * id
     */
    private int id;
    /**
     * 线路类型: 1.前后直线, 2.前后二阶贝塞尔, 3.前后三阶贝塞尔, 4.左右横移, 5.左右二阶贝塞尔, 6.左右三阶贝塞尔
     */
    private int type;
    /**
     * 方向
     */
    private int dir;
    /**
     * 速度
     */
    private int speed;
    /**
     * 控制点1
     */
    @Alias("ctl_1")
    private ControlPoint ctl1;
    /**
     * 控制点2
     */
    @Alias("ctl_2")
    private ControlPoint ctl2;
    /**
     * 距离
     */
    private int distance;

    /**
     * ControlPoint 控制点类
     */
    @Data
    public static class ControlPoint {
        /**
         * x坐标
         */
        private int x;
        /**
         * y坐标
         */
        private int y;
    }

    /**
     * 预计算的几何对象
     * 1. transient: 防止 Java 原生序列化
     * 2. @JsonIgnore: 防止 JSON 序列化 (前端不需要看到这个复杂的对象)
     * 3. 这里的 Geometry 包含了该路段完整的形状 (直线或贝塞尔曲线)
     */
    @JsonIgnore
    private transient Geometry geometry;
}