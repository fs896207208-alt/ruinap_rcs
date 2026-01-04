package com.ruinap.infra.config.pojo.link;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 链接配置实体
 *
 * @author qianye
 * @create 2025-02-16 17:11
 */
@Getter
@Setter
public class LinkEntity {
    /**
     * 通用配置
     */
    private Map<String, String> common;
    /**
     * 链接集合
     */
    private Map<String, Map<String, String>> links;
}
