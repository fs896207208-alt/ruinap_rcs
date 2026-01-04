package com.ruinap.infra.framework.core;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.setting.Setting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【核心组件】环境配置管理器
 * <p>
 * 职责：
 * 1. 启动时扫描 config 目录，自动识别 .yaml 和 .setting 文件。
 * 2. 建立 "扁平化视图" (Properties)：供 @ConditionalOnProperty 进行条件判断 (支持 key.subkey[0] 语法)。
 * 3. 建立 "结构化视图" (SourceMap)：供业务 Bean 通过 bind 方法将配置绑定为 Java对象。
 *
 * @author qianye
 * @create 2025-12-24 15:08
 */
public class Environment {
    /**
     * 扁平化属性池
     * 存储格式：key=value
     * 示例：server.port=8080, security.whitelist[0]=127.0.0.1, rcs_db.db_ip=192.168.1.1
     * 用途：专供 @ConditionalOnProperty 读取
     */
    private final Properties properties = new Properties();

    /**
     * 结构化数据池
     * Key: 文件名 (不含后缀，如 "rcs_core")
     * Value: 解析后的 Map 数据
     * 用途：专供 Bean 初始化时进行对象绑定
     */
    private final Map<String, Map<String, Object>> sourceMap = new ConcurrentHashMap<>();

    /**
     * 存放原始的 Hutool Setting 对象
     */
    private final Map<String, Setting> settingPool = new ConcurrentHashMap<>();

    /**
     * 文件指纹缓存: FileName -> MD5
     */
    private final Map<String, String> fileFingerprints = new ConcurrentHashMap<>();

    /**
     * Jackson 转换器 (复用，避免频繁创建)
     */
    private final ObjectMapper mapper;

    private final RcsLock lock = RcsLock.ofReentrant();

    public Environment() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 【核心入口】扫描并加载指定目录 (智能增量更新)
     *
     * @param configDir 配置文件目录
     * @return 返回本次扫描中【发生变更】的数据源名称集合 (例如 ["rcs_map", "rcs_core"])
     */
    public Set<String> scanAndLoad(String configDir) {
        return lock.supplyInWrite(() -> {
            Set<String> changedSources = new HashSet<>();
            File dir = new File(configDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return changedSources;
            }

            File[] files = dir.listFiles((d, name) ->
                    name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".setting")
            );

            if (files == null) {
                return changedSources;
            }

            for (File file : files) {
                String fileName = file.getName();
                String sourceName = fileName.substring(0, fileName.lastIndexOf("."));

                // 1. 计算当前文件指纹
                String currentMd5 = SecureUtil.md5(file);
                String lastMd5 = fileFingerprints.get(fileName);

                // 2. 指纹一致，跳过解析 (性能优化核心)
                if (currentMd5.equals(lastMd5)) {
                    continue;
                }

                // 3. 指纹变更，执行加载
                try {
                    if (fileName.endsWith(".setting")) {
                        loadSetting(sourceName, file);
                    } else {
                        loadYaml(sourceName, file);
                    }

                    // 更新指纹和变更列表
                    fileFingerprints.put(fileName, currentMd5);
                    changedSources.add(sourceName);

                } catch (Exception e) {
                    RcsLog.sysLog.error("配置加载失败: " + fileName, e);
                }
            }
            return changedSources;
        });
    }

    /**
     * 解析 YAML 文件 (Jackson)
     */
    private void loadYaml(String sourceName, File file) {
        try {
            // 1. Jackson 解析为 Map
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(file, Map.class);

            // 2. 存入结构化池 (供 bind 使用)
            sourceMap.put(sourceName, map);

            // 3. 递归拍平存入 Properties (供 Conditional 使用)
            // 传入空字符串作为前缀，实现混合模式
            buildFlattenedMap("", map);

            RcsLog.consoleLog.info("{} 已加载 YAML 配置: [{}] -> {}", RcsLog.randomInt(), sourceName, file.getName());
        } catch (Exception e) {
            RcsLog.sysLog.error("YAML 解析失败: " + file.getName(), e);
            throw new RuntimeException("环境初始化失败: " + file.getName(), e);
        }
    }

    /**
     * 解析 Setting 文件 (Hutool)
     */
    private void loadSetting(String sourceName, File file) {
        try {
            // 1. Hutool 解析
            Setting setting = new Setting(file, CharsetUtil.CHARSET_UTF_8, true);
            // 将原始对象存入池中，供 DbSetting 等类直接获取
            settingPool.put(sourceName, setting);

            // 2. 转换为标准的 Map<String, Object> 结构
            // Setting 的结构是 Group -> Key -> Value
            Map<String, Object> settingMap = new HashMap<>();

            for (String group : setting.getGroups()) {
                Map<String, String> groupMap = new HashMap<>();
                for (String key : setting.keySet(group)) {
                    String value = setting.getByGroup(key, group);

                    // 构建组内 Map
                    groupMap.put(key, value);

                    // 同时拍平放入 Properties
                    // Setting 的标准键值习惯是 [Group].[Key]
                    properties.put(StrUtil.format("{}.{}", group, key), value);
                }
                settingMap.put(group, groupMap);
            }

            // 3. 存入结构化池
            sourceMap.put(sourceName, settingMap);

            RcsLog.consoleLog.info("{} 已加载 Setting 配置: [{}] -> {}", RcsLog.randomInt(), sourceName, file.getName());
        } catch (Exception e) {
            RcsLog.consoleLog.error("Setting 解析失败: " + file.getName(), e);
            throw new RuntimeException("环境初始化失败: " + file.getName(), e);
        }
    }

    /**
     * 【数据绑定】将指定源的配置转换为 Java Bean
     *
     * @param sourceName 数据源名称 (如 "rcs_core")
     * @param targetType 目标类型 (如 CoreConfig.class)
     */
    public <T> T bind(String sourceName, Class<T> targetType) {
        Map<String, Object> rawData = sourceMap.get(sourceName);
        if (rawData == null) {
            return null;
        }
        // 利用 Jackson 的 convertValue 实现 Map 到 POJO 的转换
        // 这样即使原始数据来自 Setting 文件，只要转成了 Map，也能通过 Jackson 绑定
        return mapper.convertValue(rawData, targetType);
    }

    /**
     * 获取扁平化属性 (给 ConditionalOnProperty 用)
     */
    public boolean containsProperty(String key) {
        return properties.containsKey(key);
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * 直接获取原始的 Hutool Setting 对象
     * 适用于 DbSetting 等需要直接操作 Setting API 的场景
     */
    public Setting getSetting(String sourceName) {
        return settingPool.get(sourceName);
    }

    // ================== 私有递归逻辑 (用于扁平化 YAML) ==================

    private void buildFlattenedMap(String prefix, Map<String, Object> map) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String fullKey = (prefix.isEmpty()) ? key : StrUtil.format("{}.{}", prefix, key);
            processValue(fullKey, entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void processValue(String currentKey, Object value) {
        if (value instanceof Map) {
            buildFlattenedMap(currentKey, (Map<String, Object>) value);
        } else if (value instanceof Collection) {
            // 处理 List，支持 whitelist[0] 语法
            Collection<Object> collection = (Collection<Object>) value;
            int index = 0;
            for (Object item : collection) {
                processValue(StrUtil.format("{}[{}]", currentKey, index), item);
                index++;
            }
        } else if (value != null) {
            // 简单值，直接存入
            properties.put(currentKey, value.toString());
        }
    }
}