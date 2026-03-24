package com.ruinap.infra.config;

import cn.hutool.core.util.StrUtil;
import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.config.event.RcsMapConfigRefreshEvent;
import com.ruinap.infra.config.pojo.MapConfig;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.log.RcsLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调度地图Yaml配置文件(支持指纹校验与热更新)
 *
 * @author qianye
 * @create 2024-10-30 16:19
 */
@Component
public class MapYaml implements ReloadableConfig {
    /**
     * 调度地图配置 (使用 volatile 保证多线程可见性)
     */
    private volatile MapConfig config;
    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private Environment environment;

    /**
     * 初始化调度地图Yaml配置文件
     * 使用局部变量替换法确保 config 引用切换的原子性
     */
    @PostConstruct
    public void initialize() {
        rebind();
    }

    /**
     * 【内存重载】由外部接口触发
     */
    @Override
    public void rebind() {
        // 1. 加载最新配置
        MapConfig newConfig = environment.bind(getSourceName(), MapConfig.class);

        // 2. 【新功能】在赋值前，预处理双向桥接点
        processBidirectionalBridge(newConfig);

        // 3. 原子赋值 (volatile保证可见性)
        this.config = newConfig;

        // 2. 【关键】主动发布事件，通知 MapManager 去重新构建地图
        if (this.config != null && ctx != null) {
            ctx.publishEvent(new RcsMapConfigRefreshEvent(this));
            RcsLog.sysLog.info(">>> [MapYaml] 全局重载触发：已发布地图刷新事件");
        }
    }

    @Override
    public String getSourceName() {
        return "rcs_map";
    }

    /**
     * 预处理双向桥接配置
     * 自动生成反向的配置数据
     */
    private void processBidirectionalBridge(MapConfig config) {
        if (config == null || config.getBridgePoint() == null || config.getBridgePoint().isEmpty()) {
            return;
        }

        LinkedHashMap<String, LinkedHashMap<String, String>> bridgePoints = config.getBridgePoint();
        LinkedHashMap<String, LinkedHashMap<String, String>> reverseEntries = new LinkedHashMap<>();

        for (Map.Entry<String, LinkedHashMap<String, String>> entry : bridgePoints.entrySet()) {
            String key = entry.getKey();
            LinkedHashMap<String, String> props = entry.getValue();
            if (props == null || key == null) {
                continue;
            }

            String isBidirectional = props.get("bidirectional");
            if ("true".equalsIgnoreCase(isBidirectional)) {
                String[] split = key.trim().split("_");
                if (split.length != 2) {
                    continue;
                }

                String startMapId = split[0];
                String targetMapId = split[1];
                String reverseKey = StrUtil.format("{}_{}", targetMapId, startMapId);

                // 如果反向配置不存在，则自动生成
                if (!bridgePoints.containsKey(reverseKey)) {
                    LinkedHashMap<String, String> reverseProps = new LinkedHashMap<>(props);

                    // 交换起点和终点
                    String p1 = props.get("origin");
                    String p2 = props.get("destin");

                    // 确保放入的是 String 类型
                    reverseProps.put("origin", p2 != null ? p2 : "");
                    reverseProps.put("destin", p1 != null ? p1 : "");
                    reverseProps.put("bidirectional", "true");

                    // 反转 bridge_id
                    // 逻辑：找到最后两个 "_"，交换它们之间的内容
                    // 例如：teleport_1_2 -> teleport_2_1
                    // 例如：my_special_bridge_P1_M2 -> my_special_bridge_M2_P1
                    String bridgeId = props.get("bridge_id");
                    if (bridgeId != null) {
                        // 使用正则替换：(.*)贪婪匹配前缀，([^_]+)匹配非下划线的ID段
                        // 这样即使 ID 不是纯数字也能兼容
                        String newId = bridgeId.replaceFirst("(.*)_([^_]+)_([^_]+)$", "$1_$3_$2");
                        reverseProps.put("bridge_id", newId);
                    }

                    reverseEntries.put(reverseKey, reverseProps);
                }
            }
        }

        // 合并反向配置
        if (!reverseEntries.isEmpty()) {
            bridgePoints.putAll(reverseEntries);
        }
    }

    /**
     * 获取地图文件
     *
     * @return 路径集合
     */
    public LinkedHashMap<Integer, String> getRcsMaps() {
        MapConfig current = config;
        if (current == null || current.getRcsMap() == null || current.getRcsMap().getSourceFile() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getRcsMap().getSourceFile();
    }

    /**
     * 获取地图桥接点配置
     *
     * @return 桥接点集合
     */
    public LinkedHashMap<String, LinkedHashMap<String, String>> getBridgePoint() {
        MapConfig current = config;
        if (current == null || current.getBridgePoint() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getBridgePoint();
    }

    /**
     * 获取待机点配置
     *
     * @return 待机点集合
     */
    public LinkedHashMap<Integer, ArrayList<Integer>> getStandbyPoint() {
        MapConfig current = config;
        if (current == null || current.getStandbyPoint() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getStandbyPoint();
    }

    /**
     * 获取待机屏蔽点配置
     *
     * @return 待机屏蔽点集合
     */
    public LinkedHashMap<Integer, ArrayList<Integer>> getStandbyShieldPoint() {
        MapConfig current = config;
        if (current == null || current.getStandbyShieldPoint() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getStandbyShieldPoint();
    }

    /**
     * 获取充电点配置
     *
     * @return 充电点集合
     */
    public LinkedHashMap<Integer, ArrayList<Integer>> getChargePoint() {
        MapConfig current = config;
        if (current == null || current.getChargePoint() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getChargePoint();
    }

    /**
     * 获取管制区配置
     *
     * @return 管制区集合
     */
    public LinkedHashMap<Integer, LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>>> getControlArea() {
        MapConfig current = config;
        if (current == null || current.getControlArea() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getControlArea();
    }

    /**
     * 获取管制点配置
     *
     * @return 管制点集合
     */
    public LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, ArrayList<Integer>>>> getControlPoint() {
        MapConfig current = config;
        if (current == null || current.getControlPoint() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getControlPoint();
    }

    /**
     * 获取避让点配置
     *
     * @return 避让点集合
     */
    public LinkedHashMap<Integer, LinkedHashMap<String, ArrayList<Integer>>> getAvoidancePoint() {
        MapConfig current = config;
        if (current == null || current.getAvoidancePoint() == null) {
            return new LinkedHashMap<>(0);
        }
        return current.getAvoidancePoint();
    }
}
