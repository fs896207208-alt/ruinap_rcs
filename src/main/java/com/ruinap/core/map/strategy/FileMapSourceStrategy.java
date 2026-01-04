package com.ruinap.core.map.strategy;

import cn.hutool.core.io.FileUtil;
import com.ruinap.infra.config.MapYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.ConditionalOnProperty;
import com.ruinap.infra.log.RcsLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件系统读取策略
 *
 * @author qianye
 * @create 2025-01-15 10:39
 */
@Component
// 当配置文件 rcs_map.source_type = file 时生效 (matchIfMissing = true 表示默认就是 file)
@ConditionalOnProperty(name = "rcs_map.source_type", havingValue = "file", matchIfMissing = true)
public class FileMapSourceStrategy implements MapSourceStrategy {

    @Autowired
    private MapYaml mapYaml;

    /**
     * 获取地图数据集合
     */
    @Override
    public Map<Integer, String> loadRawData() {
        Map<Integer, String> result = new HashMap<>();
        Map<Integer, String> files = mapYaml.getRcsMaps();
        if (files == null) {
            return result;
        }

        for (Map.Entry<Integer, String> entry : files.entrySet()) {
            try {
                File file = new File(entry.getValue());
                if (file.exists()) {
                    result.put(entry.getKey(), FileUtil.readString(file, StandardCharsets.UTF_8));
                } else {
                    RcsLog.sysLog.warn("地图文件不存在: {}", entry.getValue());
                }
            } catch (Exception e) {
                RcsLog.sysLog.error("地图文件读取失败: {}", entry.getValue(), e);
            }
        }
        return result;
    }
}
