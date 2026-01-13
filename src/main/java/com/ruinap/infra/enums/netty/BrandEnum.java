package com.ruinap.infra.enums.netty;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 品牌枚举类
 *
 * @author qianye
 * @create 2025-02-21 16:37
 */
@Getter
@AllArgsConstructor
public enum BrandEnum {
    /**
     * 司岚
     */
    SLAMOPTO("slamopto"),

    /**
     * 顶尖
     */
    DINGJIAN("dingjian");

    /**
     * 品牌对象
     * <p>
     * -- GETTER --
     * 获取品牌对象
     */
    private final String brand;

    /**
     * 根据品牌字符串获取对应的品牌对象
     *
     * @param brand 品牌字符串
     * @return 品牌对象
     */
    public static BrandEnum fromBrand(String brand) {
        for (BrandEnum tempBrand : values()) {
            if (tempBrand.brand.equalsIgnoreCase(brand)) {
                return tempBrand;
            }
        }
        return null;
    }
}
