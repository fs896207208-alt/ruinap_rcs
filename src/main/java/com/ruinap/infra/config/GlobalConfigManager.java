package com.ruinap.infra.config;

import com.ruinap.infra.config.common.PathUtils;
import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;

import java.util.Map;
import java.util.Set;

/**
 * @author qianye
 * @create 2025-12-24 18:02
 */
@Component
public class GlobalConfigManager {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    private final RcsLock rcsLock = RcsLock.ofReadWrite();

    /**
     * 一键重载所有配置文件
     * 场景：当你修改了多个文件，或者不知道修改了哪个文件时调用。
     */
    public void reloadAll() {
        rcsLock.runInWrite(() -> {
            long start = System.currentTimeMillis();
            RcsLog.consoleLog.info(">>> 开始全局配置热重载...");

            // 1. 强制 Environment 重新扫描磁盘
            // 返回的是真正发生内容变更的 sourceName 集合 (例如 ["rcs_core", "rcs_map"])
            Set<String> changedSources = environment.scanAndLoad(PathUtils.CONFIG_DIR.toString());

            if (changedSources.isEmpty()) {
                RcsLog.consoleLog.info(">>> 扫描完成，未检测到任何配置文件变更。");
                return;
            }

            // 2. 找到容器中所有实现了 ReloadableConfig 接口的 Bean
            Map<String, ReloadableConfig> beans = applicationContext.getBeansOfType(ReloadableConfig.class);

            if (beans == null || beans.isEmpty()) {
                RcsLog.consoleLog.warn(">>> 未找到任何可重载的配置组件");
                return;
            }

            // 3. 挨个调用 rebind
            int count = 0;

            for (ReloadableConfig configBean : beans.values()) {
                // 精准过滤！只有文件变了才通知
                if (changedSources.contains(configBean.getSourceName())) {
                    try {
                        configBean.rebind();
                        count++;
                    } catch (Exception e) {
                        RcsLog.consoleLog.error("配置组件重载失败: " + configBean.getClass().getSimpleName(), e);
                    }
                }
            }
            RcsLog.consoleLog.info(">>> 全局配置热重载完成，共更新 {} 个组件，耗时: {} ms", count, System.currentTimeMillis() - start);
        });
    }
}