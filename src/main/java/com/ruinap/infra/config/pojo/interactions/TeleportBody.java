package com.ruinap.infra.config.pojo.interactions;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 传送点
 */
@Getter
@Setter
public class TeleportBody implements Serializable {

    /**
     * 传送ID
     */
    private String id;

    /**
     * 起点
     */
    private String origin;

    /**
     * 终点
     */
    private String destin;

    /**
     * 是否是双向传送点
     */
    private boolean bidirectional;
}
