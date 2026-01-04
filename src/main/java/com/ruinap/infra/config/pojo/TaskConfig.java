package com.ruinap.infra.config.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruinap.infra.config.pojo.task.ChargeCommonEntity;
import com.ruinap.infra.config.pojo.task.RegexCommonEntity;
import com.ruinap.infra.config.pojo.task.StandbyCommonEntity;
import com.ruinap.infra.config.pojo.task.TaskCommonEntity;
import lombok.Getter;
import lombok.Setter;


/**
 * RCS任务配置类
 *
 * @author qianye
 * @create 2024-10-30 16:22
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskConfig {
    /**
     * 任务配置
     */
    @JsonProperty("task_common")
    private TaskCommonEntity taskCommon;

    /**
     * 正则配置
     */
    @JsonProperty("regex_common")
    private RegexCommonEntity regexCommon;

    /**
     * 待机配置
     */
    @JsonProperty("standby_common")
    private StandbyCommonEntity standbyCommon;

    /**
     * 充电配置
     */
    @JsonProperty("charge_common")
    private ChargeCommonEntity chargeCommon;
}

