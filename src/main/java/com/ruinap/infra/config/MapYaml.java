package com.ruinap.infra.config;

import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.config.pojo.MapConfig;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.config.RcsMapConfigRefreshEvent;
import com.ruinap.infra.log.RcsLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;

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
        config = environment.bind("rcs_map", MapConfig.class);
    }

    /**
     * 【内存重载】由外部接口触发
     */
    @Override
    public void rebind() {
        // 1. 重新绑定最新配置 (Environment 此时已被 GlobalConfigManager 刷新过)
        this.config = environment.bind("rcs_map", MapConfig.class);

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
