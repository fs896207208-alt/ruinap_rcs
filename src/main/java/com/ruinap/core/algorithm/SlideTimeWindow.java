package com.ruinap.core.algorithm;

import cn.hutool.core.util.StrUtil;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.event.ApplicationListener;
import com.ruinap.infra.framework.core.event.config.RcsMapConfigRefreshEvent;
import com.ruinap.infra.log.RcsLog;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动时间窗口
 * <p>
 * 当然，此类只是参考了滑动时间窗口算法，并非是真正的滑动时间窗口算法
 * 目的：如果有很多条路径都可以去到目标点，该类则是将点位权重添加，路径规划时点位权重值越小越优先选择，防止很多AGV同时挤在一条路径上，平衡其他路径的使用率
 * <p>
 * 大白话解释：有多条道路可以去到北京且所需时间一样，权重就像红绿灯时长，车多就将红绿灯时长加长，车少就将红绿灯时长变短，使用地图导航时就会优先避开拥堵路径，以此达到分散车流的目的，让通行更顺畅
 * <p>
 * digraph.setVertexWeight();
 *
 * @author qianye
 * @create 2025-08-15 15:08
 */
@Component
public class SlideTimeWindow implements ApplicationListener<RcsMapConfigRefreshEvent> {
    @Autowired
    private CoreYaml coreYaml;
    /**
     * 点位权重表
     * Key: Graph Index (Integer) —— 对应图算法的顶点索引
     * Value: 当前权重 (虽配置为int，但存储为Double以适配图算法接口)
     * -- GETTER --
     * 获取权重集合
     */
    @Getter
    private final Map<Integer, Double> weightMap = new ConcurrentHashMap<>();

    /**
     * 默认基础权重 1.0
     */
    private static final double BASE_WEIGHT = 1.0;

    // --- 核心配置缓存区 (Volatile 保证可见性) ---
    /**
     * 权重增量步长 (默认 1)
     */
    private volatile int weightStep = 1;
    /**
     * 最大权重限制 (默认 100)
     */
    private volatile int weightMaxStep = 100;
    /**
     * 拥堵阈值 (默认 5)
     */
    private volatile int maxOccupancy = 5;

    /**
     * 1. 启动时加载配置
     */
    @PostConstruct
    public void init() {
        refreshConfig();
    }

    @Override
    public void onApplicationEvent(RcsMapConfigRefreshEvent event) {
        RcsLog.consoleLog.warn("地图配置变更，重置滑动时间窗口权重表");
        weightMap.clear();
        refreshConfig();
    }

    /**
     * 统一刷新配置逻辑
     * 作用：将配置读取风险隔离在此方法内，运行时不再抛出配置相关异常
     */
    private void refreshConfig() {
        // 使用安全的 getIntConfig，即使 yaml 没配也不会崩
        this.weightStep = getIntConfig("weight_step", 1);
        this.weightMaxStep = getIntConfig("weight_max_step", 100);
        this.maxOccupancy = getIntConfig("move_time_window_max_occupancy", 5);

        RcsLog.consoleLog.info("滑动时间窗口参数已更新: weight_step={}, weight_max_step={}, move_time_window_max_occupancy={}", weightStep, weightMaxStep, maxOccupancy);
    }

    /**
     * 获取权重 (适配 Graph4J 接口)
     */
    public double getWeight(int current, int next) {
        if (weightMap.isEmpty()) {
            return BASE_WEIGHT;
        }
        // 直接用 int 类型的 next 去查，匹配 Key 类型
        return weightMap.getOrDefault(next, BASE_WEIGHT);
    }

    /**
     * 增加权重
     */
    public void addWeight(List<RcsPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }

        int step = this.weightStep;
        double maxStep = this.weightMaxStep;

        for (RcsPoint point : points) {
            weightMap.compute(point.getGraphIndex(), (k, oldVal) -> {
                double current = (oldVal == null) ? BASE_WEIGHT : oldVal;
                double newVal = Math.min(current + step, maxStep);
                return newVal;
            });
        }
    }

    /**
     * 减少权重
     */
    public void subWeight(List<RcsPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }

        int step = this.weightStep;

        for (RcsPoint point : points) {
            weightMap.compute(point.getGraphIndex(), (k, oldVal) -> {
                if (oldVal == null) {
                    return null;
                }

                double newVal = oldVal - step;
                // 浮点数判断：如果小于等于 1.0001 则视为归零
                if (newVal <= BASE_WEIGHT + 0.0001) {
                    // 移除 Key
                    return null;
                }
                return newVal;
            });
        }
    }

    /**
     * 计算成本
     * <p>
     * 1. 当 weight ≈ 1 时，直接返回距离
     * 2. 当 weight >= MAX_OCCUPANCY 时，公式：distance + (weight - 1) * 100
     * 3. 当 weight < MAX_OCCUPANCY 时，公式：distance * 1.1 + (weight - 1) * 20
     *
     * @param distance 路径距离
     * @param weight   权重
     * @return 计算得到的成本（double 类型，保留精度）
     */
    public double costCalculation(Double distance, Double weight) {
        // 使用 double 原生类型避免频繁拆箱，并处理可能的 null
        double cost = distance != null ? distance : 0.0;
        double costWeight = weight != null ? weight : 0.0;

        // 校验输入参数
        if (cost <= 0 || costWeight <= 0) {
            // 保持原有的告警逻辑
            RcsLog.consoleLog.warn(RcsLog.getTemplate(2), RcsLog.randomInt(),
                    StrUtil.format("使用系统默认值计算成本，因为传入无效的距离或权重：distance = {} weight = {}", distance, weight));
            return cost > 0 ? cost : 1.0;
        }

        // 如果权重约为 1 (允许微小误差)
        if (Math.abs(costWeight - 1.0) < 0.001) {
            return cost;
        }

        // 严重拥堵：距离 + (权重-1)*100
        if (this.maxOccupancy <= costWeight) {
            return cost + (costWeight - 1.0) * 100.0;
        }
        // 轻微拥堵：距离*1.1 + (权重-1)*20
        return cost * 1.1 + (costWeight - 1.0) * 20.0;
    }

    /**
     * 安全读取配置辅助方法
     */
    private int getIntConfig(String key, int defaultValue) {
        try {
            Map<String, Integer> common = coreYaml.getAlgorithmCommon();
            if (common != null && common.containsKey(key)) {
                return common.get(key);
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return defaultValue;
    }
}
