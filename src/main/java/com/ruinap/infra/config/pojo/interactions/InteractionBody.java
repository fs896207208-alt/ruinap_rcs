package com.ruinap.infra.config.pojo.interactions;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 交互体
 *
 * @author qianye
 * @create 2024-04-15 13:28
 */
@Getter
@Setter
public class InteractionBody implements Serializable {

    /**
     * 交互门
     */
    private List<String> door;

    /**
     * 交互点位
     */
    private String point;
}
