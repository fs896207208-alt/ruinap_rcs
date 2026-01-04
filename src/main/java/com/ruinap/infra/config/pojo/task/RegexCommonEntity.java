package com.ruinap.infra.config.pojo.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;

/**
 * 正则配置类
 */
@Getter
@Setter
public class RegexCommonEntity {
    /**
     * 点位别名
     */
    @JsonProperty("task_piont_alias")
    private LinkedHashMap<String, String> taskPointAlias;

    /**
     * 起点动作参数
     */
    @JsonProperty("origin_action_parameter")
    private LinkedHashMap<String, String> originActionParameter;

    /**
     * 终点动作参数
     */
    @JsonProperty("destin_action_parameter")
    private LinkedHashMap<String, String> destinActionParameter;
}
