package com.ruinap.core.equipment.pojo;

import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import lombok.Getter;
import lombok.Setter;

/**
 * AGV参数类
 *
 * @author qianye
 * @create 2025-03-03 16:31
 */
@Getter
@Setter
public class RcsAgvAttribute {
    /**
     * 协议枚举
     */
    private ProtocolEnum protocol;
    /**
     * 协议枚举
     */
    private LinkEquipmentTypeEnum equipmentType;
}
