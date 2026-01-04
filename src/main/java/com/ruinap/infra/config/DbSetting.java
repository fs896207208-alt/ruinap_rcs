package com.ruinap.infra.config;

import cn.hutool.setting.Setting;
import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.Environment;

import java.util.Collections;
import java.util.Map;

/**
 * 数据库配置
 *
 * @author qianye
 * @create 2024-10-28 18:36
 */
@Component
public class DbSetting implements ReloadableConfig {
    @Autowired
    private Environment environment;
    @Autowired
    private ConfigAdapter configAdapter;
    /**
     * 存储解析后的配置数据
     * 结构: GroupName -> { Key -> Value }
     */
    private Map<String, Object> settingMap;
    // 常量定义
    private final String GROUP_DB = "rcs_db";
    private final String KEY_DB_NAME = "db_name";
    private final String KEY_DB_IP = "db_ip";
    private final String KEY_DB_PORT = "db_port";

    /**
     * 初始化：从 Environment 绑定数据
     */
    @PostConstruct
    public void initialize() {
        loadConfig();
    }

    private void loadConfig() {

        // "rcs_db" 对应 config/rcs_db.setting 的文件名
        // 直接通过 Adapter 获取 Map。
        // 因为 GlobalConfigManager 已经调用过 environment.scanAndLoad 了，
        // 所以此时 Environment 里的数据肯定是最新的。
        Map<String, Object> rawData = configAdapter.bind(GROUP_DB, Map.class);
        this.settingMap = (rawData != null) ? rawData : Collections.emptyMap();
    }

    /**
     * 【内存重载】由外部接口触发
     */
    @Override
    public void rebind() {
        // 1. 重新执行初始化逻辑，更新 settingMap 缓存
        loadConfig();
    }

    @Override
    public String getSourceName() {
        return GROUP_DB;
    }

    /**
     * 获取 Setting 对象
     *
     * @return Setting 对象
     */
    public Setting getSetting() {
        return configAdapter.getSetting(GROUP_DB);
    }

    /**
     * 根据组名和键名获取值
     *
     * @param group 组名
     * @param key   键名
     * @return 值
     */
    public String getKeyByGroupAndKey(String group, String key) {
        if (settingMap == null) {
            return null;
        }
        // 获取分组 Map
        @SuppressWarnings("unchecked")
        Map<String, String> groupData = (Map<String, String>) settingMap.get(group);
        return groupData != null ? groupData.get(key) : null;
    }

    /**
     * 获取数据库名称
     */
    public String getDatabaseName() {
        return getKeyByGroupAndKey(GROUP_DB, KEY_DB_NAME);
    }

    /**
     * 获取数据库IP
     */
    public String getDatabaseIp() {
        return getKeyByGroupAndKey(GROUP_DB, KEY_DB_IP);
    }

    /**
     * 获取数据库端口
     */
    public String getDatabasePort() {
        return getKeyByGroupAndKey(GROUP_DB, KEY_DB_PORT);
    }
}
