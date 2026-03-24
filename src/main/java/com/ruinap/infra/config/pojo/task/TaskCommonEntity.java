package com.ruinap.infra.config.pojo.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 任务通用配置类
 */
@Getter
@Setter
public class TaskCommonEntity {
    /**
     * 任务来源
     */
    @JsonProperty("task_source")
    private Integer taskSource;
    /**
     * 任务拍卖AGV数量
     */
    @JsonProperty("auction_top_k")
    private Integer auctionTopK;
    /**
     * 任务分配模式
     */
    @JsonProperty("task_distribute_mode")
    private Integer taskDistributeMode;

    /**
     * 任务起点指定AGV
     */
    @JsonProperty("task_origin_specify")
    private Map<String, String> taskOriginSpecify;

    /**
     * 任务终点指定AGV
     */
    @JsonProperty("task_destin_specify")
    private Map<String, String> taskDestinSpecify;

    /**
     * 任务起点区域指定AGV
     */
    @JsonProperty("task_origin_area")
    private Map<String, String> taskOriginArea;

    /**
     * 任务终点区域指定AGV
     */
    @JsonProperty("task_destin_area")
    private Map<String, String> taskDestinArea;
}
