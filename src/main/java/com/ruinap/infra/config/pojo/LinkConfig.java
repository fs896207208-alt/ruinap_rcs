package com.ruinap.infra.config.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruinap.infra.config.pojo.link.LinkEntity;
import com.ruinap.infra.config.pojo.link.TransferLinkEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * RCS链接配置类
 *
 * @author qianye
 * @create 2024-10-30 16:22
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkConfig {

    /**
     * AGV配置
     */
    @JsonProperty("agv_link")
    private LinkEntity agvLink;

    /**
     * 充电桩配置
     */
    @JsonProperty("charge_link")
    private LinkEntity chargeLink;

    /**
     * 中转系统配置
     */
    @JsonProperty("transfer_link")
    private TransferLinkEntity transferLink;
}