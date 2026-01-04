package com.ruinap.infra.config.pojo.interactions;

import lombok.Getter;
import lombok.Setter;

/**
 * 交互点
 */
@Getter
@Setter
public class AirShowerInteractionBody {

    /**
     * 交互开始
     */
    private InteractionBody start;

    /**
     * 交互结束
     */
    private InteractionBody end;
}
