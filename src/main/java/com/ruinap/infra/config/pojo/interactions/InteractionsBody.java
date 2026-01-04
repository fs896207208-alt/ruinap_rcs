package com.ruinap.infra.config.pojo.interactions;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 交互点
 */
@Getter
@Setter
public class InteractionsBody implements Serializable {

    /**
     * 交互开始
     */
    private InteractionBody start;

    /**
     * 交互结束
     */
    private InteractionBody end;
}
