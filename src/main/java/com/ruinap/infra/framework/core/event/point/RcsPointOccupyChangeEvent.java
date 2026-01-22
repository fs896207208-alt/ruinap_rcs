package com.ruinap.infra.framework.core.event.point;

import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.infra.framework.core.event.ApplicationEvent;
import lombok.Getter;

/**
 * 点位占用变更事件
 *
 * @author qianye
 * @create 2026-01-20 10:52
 */
@Getter
public class RcsPointOccupyChangeEvent extends ApplicationEvent {

    /**
     * 变更类型：占用 or 释放
     */
    public enum ChangeType {
        /**
         * 占用
         */
        OCCUPIED,
        /**
         * 释放
         */
        RELEASED
    }

    /**
     * 点位编号
     */
    private final Integer pointId;
    /**
     * 设备编号
     */
    private final String deviceCode;
    /**
     * 占用类型
     */
    private final PointOccupyTypeEnum occupyType;
    /**
     * 变更类型
     */
    private final ChangeType changeType;

    public RcsPointOccupyChangeEvent(Object source, Integer pointId, String deviceCode, PointOccupyTypeEnum occupyType, ChangeType changeType) {
        super(source);
        this.pointId = pointId;
        this.deviceCode = deviceCode;
        this.occupyType = occupyType;
        this.changeType = changeType;
    }

    @Override
    public String toString() {
        return "PointEvent{point=" + pointId + ", dev=" + deviceCode + ", type=" + changeType + "}";
    }
}
