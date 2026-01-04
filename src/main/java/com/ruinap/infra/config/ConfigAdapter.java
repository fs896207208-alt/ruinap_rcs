package com.ruinap.infra.config;

import cn.hutool.setting.Setting;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.core.Environment;

/**
 * 配置文件适配器
 * 方便以后使用Spring框架后的集成
 *
 * @author qianye
 * @create 2025-12-24 16:20
 */
@Component
public class ConfigAdapter {
    @Autowired
    private Environment environment;

    /**
     * 获取 Hutool Setting 对象
     */
    public Setting getSetting(String sourceName) {
        // 当前实现：调用我们自定义的 Environment
        return environment.getSetting(sourceName);
    }

    /**
     * 绑定 POJO
     */
    public <T> T bind(String sourceName, Class<T> clazz) {
        return environment.bind(sourceName, clazz);
    }
}
