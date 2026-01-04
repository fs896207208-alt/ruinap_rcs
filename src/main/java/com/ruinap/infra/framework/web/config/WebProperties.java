package com.ruinap.infra.framework.web.config;

import com.ruinap.infra.framework.annotation.Component;
import lombok.Data;

/**
 * Web 容器路径配置类
 * <p>
 * 生命周期阶段：实例化 -> 属性赋值(从Yaml读取) -> 被注入到 JettyServer
 *
 * @author qianye
 * @create 2025-12-18 15:22
 */
@Component
@Data
public class WebProperties {
    private String staticPath = "/static";
    private String consolePath = "/console";
    private String webappsPath = "/webapps";
    private String h2Path = "/h2";
    private String druidPath = "/druid";
    private String openApiPath = "/openapi";

    // 获取所有系统保留路径前缀（用于 Filter 判断）
    public String[] getSystemPrefixes() {
        return new String[]{
                openApiPath + "/",
                druidPath + "/",
                h2Path + "/",
                staticPath + "/",
                webappsPath + "/",
                consolePath,
                "/favicon.ico"
        };
    }
}
