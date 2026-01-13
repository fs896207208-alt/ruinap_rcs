package com.ruinap.infra.command.agv.basis.registry;

import com.slamopto.command.agv.basis.factory.AgvCommandFactory;
import com.slamopto.command.agv.slamopto.SlamoptoTcpFactory;
import com.slamopto.command.agv.slamopto.SlamoptoWebSocketFactory;
import com.slamopto.common.enums.BrandEnum;
import com.slamopto.common.enums.ProtocolEnum;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册表管理工厂
 *
 * @author qianye
 * @create 2025-02-24 18:45
 */
public class CommandFactoryRegistry {
    /**
     * 复合Key类（品牌+协议）
     */
    private static class BrandProtocolKey {
        /**
         * 品牌
         */
        private final String brand;
        /**
         * 协议
         */
        private final String protocol;

        /**
         * 构造函数
         *
         * @param brand    品牌
         * @param protocol 协议
         */
        public BrandProtocolKey(String brand, String protocol) {
            this.brand = brand;
            this.protocol = protocol;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BrandProtocolKey that = (BrandProtocolKey) o;
            return Objects.equals(brand, that.brand) && Objects.equals(protocol, that.protocol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(brand, protocol);
        }
    }

    /**
     * 工厂注册表
     */
    private static final Map<BrandProtocolKey, AgvCommandFactory> REGISTRY = new ConcurrentHashMap<>();

    /**
     * 注册工厂
     *
     * @param brand    品牌
     * @param protocol 协议
     * @param factory  工厂
     */
    public static void registerFactory(String brand, String protocol, AgvCommandFactory factory) {
        REGISTRY.put(new BrandProtocolKey(brand, protocol), factory);
    }

    /**
     * 初始化
     */
    public static void initialize() {
        // 注册司岚品牌的所有协议
        CommandFactoryRegistry.registerFactory(BrandEnum.SLAMOPTO.getBrand(), ProtocolEnum.WEBSOCKET_CLIENT.getProtocol(), new SlamoptoWebSocketFactory());
        CommandFactoryRegistry.registerFactory(BrandEnum.SLAMOPTO.getBrand(), ProtocolEnum.TCP_CLIENT.getProtocol(), new SlamoptoTcpFactory());

        // 注册其他品牌的所有协议
    }

    /**
     * 获取工厂
     *
     * @param brand    品牌
     * @param protocol 协议
     * @return 工厂
     */
    public static AgvCommandFactory getFactory(String brand, String protocol) {
        /**
         * 注册表获取工厂
         */
        AgvCommandFactory factory = REGISTRY.get(new BrandProtocolKey(brand, protocol));
        if (factory == null) {
            throw new IllegalArgumentException("未知的 品牌/协议: " + brand + "/" + protocol);
        }
        return factory;
    }
}
