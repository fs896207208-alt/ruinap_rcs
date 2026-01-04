package com.ruinap.infra.config.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;

/**
 * RCS核心配置类
 *
 * @author qianye
 * @create 2024-10-30 16:22
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreConfig {
    /**
     * 系统配置
     */
    @JsonProperty("rcs_sys")
    private LinkedHashMap<String, String> rcsSys;
    /**
     * 系统配置
     */
    @JsonProperty("rcs_timer")
    private LinkedHashMap<String, LinkedHashMap<String, String>> rcsTimer;
    /**
     * 端口配置
     */
    @JsonProperty("rcs_port")
    private LinkedHashMap<String, Integer> rcsPort;
    /**
     * 线程池配置
     */
    @JsonProperty("rcs_thread_pool")
    private LinkedHashMap<String, Integer> rcsThreadPool;

    /**
     * 算法配置
     */
    @JsonProperty("algorithm_common")
    private LinkedHashMap<String, Integer> algorithmCommon;
}