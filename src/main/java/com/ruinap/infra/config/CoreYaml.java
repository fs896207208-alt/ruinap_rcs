package com.ruinap.infra.config;

import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.config.pojo.CoreConfig;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.framework.core.event.RcsCoreConfigRefreshEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调度核心Yaml配置文件
 *
 * @author qianye
 * @create 2024-10-30 16:19
 */
@Component
public class CoreYaml implements ReloadableConfig {
    /**
     * 调度核心配置
     */
    private volatile CoreConfig config;
    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private Environment environment;

    /**
     * 初始化
     */
    @PostConstruct
    public void initialize() {
        // 从 Environment 绑定对象 (核心改动)
        // 此时 Environment 已经在容器启动时加载了 config/rcs_core.yaml
        bindInternal();
    }

    private void bindInternal() {
        this.config = environment.bind("rcs_core", CoreConfig.class);
        if (this.config == null) {
            throw new RuntimeException("CoreYaml 初始化失败: Environment 中未找到 rcs_core 配置");
        }
    }

    /**
     * 【内存重载】由外部接口触发
     */
    @Override
    public void rebind() {
        // 纯内存操作，因为GlobalConfigManager已经重新从配置文件读取了最新的数据，这里直接从 Environment 拿最新数据
        bindInternal();
        // 发送事件，通知其他监听器
        ctx.publishEvent(new RcsCoreConfigRefreshEvent(this));
    }

    @Override
    public String getSourceName() {
        return "rcs_core";
    }

    /**
     * 获取 开发环境
     *
     * @return 是否开发环境
     */
    public String getDevelop() {
        CoreConfig current = config;
        return current.getRcsSys().getOrDefault("develop", "false");
    }

    /**
     * 获取 是否启用Arthas应用程序监控
     *
     * @return 是否启用Arthas应用程序监控
     */
    public String getEnableArthas() {
        CoreConfig current = config;
        return current.getRcsSys().getOrDefault("enable_arthas", "true");
    }

    /**
     * 获取 通讯等待回复超时时间
     *
     * @return 通讯等待回复超时时间
     */
    public String getNettyFutureTimeout() {
        CoreConfig current = config;
        return current.getRcsSys().getOrDefault("netty_future_timeout", "3");
    }

    /**
     * 获取定时器配置
     *
     * @return 定时器配置
     */
    public LinkedHashMap<String, LinkedHashMap<String, String>> getTimerCommon() {
        CoreConfig current = config;
        LinkedHashMap<String, LinkedHashMap<String, String>> timerCommon = current.getRcsTimer();
        return timerCommon.isEmpty() ? new LinkedHashMap<>(0) : timerCommon;
    }

    /**
     * 获取 WEB容器端口
     * <p>
     * 如果配置文件中没有配置，则返回默认值 9090
     *
     * @return 端口号
     */
    public Integer getWebPort() {
        CoreConfig current = config;
        return current.getRcsPort().getOrDefault("web_port", 9090);
    }

    /**
     * 获取 Netty WS端口
     * <p>
     * 如果配置文件中没有配置，则返回默认值 9091
     *
     * @return 端口号
     */
    public Integer getNettyWebsocketPort() {
        CoreConfig current = config;
        return current.getRcsPort().getOrDefault("netty_websocket_port", 9091);
    }

    /**
     * 获取 Netty MQTT端口
     * <p>
     * 如果配置文件中没有配置，则返回默认值 9092
     *
     * @return 端口号
     */
    public Integer getNettyMqttPort() {
        CoreConfig current = config;
        return current.getRcsPort().getOrDefault("netty_mqtt_port", 9092);
    }

    /**
     * 获取 算法线程池核心线程数
     *
     * @return 线程池核心线程数
     */
    public Integer getAlgoCorePoolSize() {
        if (config == null || config.getRcsThreadPool() == null) {
            // 默认兜底：CPU核数 + 1
            return Runtime.getRuntime().availableProcessors() + 1;
        }
        return config.getRcsThreadPool().getOrDefault("core_pool_size", Runtime.getRuntime().availableProcessors() + 1);
    }

    /**
     * 获取算法线程池最大线程数
     *
     * @return 线程池最大线程数
     */
    public Integer getAlgoMaxPoolSize() {
        if (config == null || config.getRcsThreadPool() == null) {
            return Runtime.getRuntime().availableProcessors() + 1;
        }
        return config.getRcsThreadPool().getOrDefault("max_pool_size", Runtime.getRuntime().availableProcessors() + 1);
    }

    /**
     * 获取算法线程池队列大小
     *
     * @return 线程池队列大小
     */
    public Integer getAlgoQueueCapacity() {
        if (config == null || config.getRcsThreadPool() == null) {
            return 2048;
        }
        return config.getRcsThreadPool().getOrDefault("queue_capacity", 2048);
    }

    /**
     * 获取算法线程池空闲线程存活时间
     *
     * @return 线程池空闲线程存活时间
     */
    public Integer getAlgoKeepAliveSeconds() {
        if (config == null || config.getRcsThreadPool() == null) {
            return 60;
        }
        return config.getRcsThreadPool().getOrDefault("keep_alive_seconds", 60);
    }

    /**
     * 获取算法配置
     *
     * @return 算法配置
     */
    public Map<String, Integer> getAlgorithmCommon() {
        CoreConfig current = config;
        Map<String, Integer> algorithmCommon = current.getAlgorithmCommon();
        return algorithmCommon.isEmpty() ? new HashMap<>() : algorithmCommon;
    }
}
