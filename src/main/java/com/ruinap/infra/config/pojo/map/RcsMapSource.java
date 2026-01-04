package com.ruinap.infra.config.pojo.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;

/**
 * 地图源配置类
 *
 * @author qianye
 * @create 2025-12-25 10:23
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RcsMapSource {
    /**
     * 数据源类型
     */
    @JsonProperty("source_type")
    private String sourceType;

    /**
     * 地图文件集合
     */
    @JsonProperty("source_file")
    private LinkedHashMap<Integer, String> sourceFile;
}
