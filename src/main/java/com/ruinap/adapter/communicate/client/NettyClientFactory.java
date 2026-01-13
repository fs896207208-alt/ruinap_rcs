package com.ruinap.adapter.communicate.client;

import com.slamopto.common.async.AsyncUtils;
import com.slamopto.common.config.LinkYaml;
import com.slamopto.common.enums.BrandEnum;
import com.slamopto.common.enums.LinkEquipmentTypeEnum;
import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.ClientAttribute;
import com.slamopto.communicate.base.protocol.IProtocolOption;
import com.slamopto.communicate.client.handler.ClientHandler;
import com.slamopto.communicate.client.handler.impl.TcpClientHandler;
import com.slamopto.communicate.client.handler.impl.WebSocketClientHandler;
import com.slamopto.communicate.client.protocol.TcpOption;
import com.slamopto.communicate.client.protocol.WebSocketOption;
import com.slamopto.log.RcsLog;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Netty客户端工厂类
 *
 * @author qianye
 * @create 2025-05-09 14:13
 */
public class NettyClientFactory {

    /**
     * 通信协议选项
     */
    private static final Map<ProtocolEnum, IProtocolOption> PROTOCOL_OPTION_MAP = new HashMap<>();
    /**
     * 协议处理器
     */
    private static final Map<LinkEquipmentTypeEnum, ClientHandler> PROTOCOL_HANDLER_MAP = new HashMap<>();

    static {
        // 初始化协议选项
        PROTOCOL_OPTION_MAP.put(ProtocolEnum.WEBSOCKET_CLIENT, new WebSocketOption());
        PROTOCOL_OPTION_MAP.put(ProtocolEnum.TCP_CLIENT, new TcpOption());

        // 初始化协议处理器
        PROTOCOL_HANDLER_MAP.put(LinkEquipmentTypeEnum.AGV, new WebSocketClientHandler());
        PROTOCOL_HANDLER_MAP.put(LinkEquipmentTypeEnum.TRANSFER, new WebSocketClientHandler());
        PROTOCOL_HANDLER_MAP.put(LinkEquipmentTypeEnum.CHARGE_PILE, new TcpClientHandler());
    }

    /**
     * 启动所有 Netty 客户端
     */
    public static void startClient() {

        //启动AGV客户端
        startAgvClient();

        //启动充电桩客户端
        startChargeClient();

        //启动中转系统客户端
        startTransferClient();
    }

    /**
     * 启动AGV客户端
     */
    private static void startAgvClient() {
        //获取所有AGV配置
        Map<String, Map<String, String>> agvLink = LinkYaml.getAgvLink();
        List<Supplier<CompletableFuture<Void>>> tasks = new ArrayList<>();
        //遍历所有AGV配置
        for (Map.Entry<String, Map<String, String>> entry : agvLink.entrySet()) {
            //客户端ID
            String clientId = entry.getKey();
            Map<String, String> value = entry.getValue();
            //通信协议
            String pact = value.get("pact");
            if (pact == null || pact.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置缺少通信协议数据"));
                continue;
            }
            // 获取通信协议
            ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
            if (protocolEnum == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置的通信协议 [" + pact + "] 获取不到枚举数据"));
                continue;
            }

            //品牌
            String brand = value.get("brand");
            if (brand == null || brand.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置缺少品牌数据"));
                continue;
            }
            BrandEnum brandEnum = BrandEnum.fromBrand(brand);
            if (brandEnum == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置的品牌 [" + brand + "] 获取不到枚举数据"));
                continue;
            }

            //设备种类
            String equipmentType = value.get("equipment_type");
            if (equipmentType == null || equipmentType.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置缺少设备种类数据"));
                continue;
            }
            LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
            if (linkEquipmentTypeEnum == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置的设备种类 [" + equipmentType + "] 获取不到枚举数据"));
                continue;
            }
            ClientHandler clientHandler = PROTOCOL_HANDLER_MAP.get(linkEquipmentTypeEnum);
            if (clientHandler == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器数据"));
                continue;
            }

            //地址
            String url = value.get("url");
            if (url == null || url.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("AGV配置缺少地址数据"));
                continue;
            }
            URI uri = URI.create(url);

            //连接失败次数
            String connectFailed = value.get("connect_failed");
            //启用状态
            String enable = value.get("enable");
            //仿真模式
            String emulation = value.get("emulation");

            //判断客户端是否在线
            boolean onlineClient = NettyClient.isClientOnline(equipmentType, clientId);
            if (!onlineClient && "true".equals(enable) && "false".equals(emulation)) {
                //获取通信协议选项
                IProtocolOption protocolOption = PROTOCOL_OPTION_MAP.get(protocolEnum);
                if (protocolOption == null) {
                    RcsLog.algorithmLog.error(RcsLog.formatTemplateRandom("AGV配置的通信协议 [" + pact + "] 获取不到通信协议选项数据"));
                    continue;
                }

                // 创建任务生成器
                Supplier<CompletableFuture<Void>> taskSupplier = AsyncUtils.runAsync(() -> {
                    startClient(protocolEnum, protocolOption, brandEnum, uri, linkEquipmentTypeEnum, clientId, connectFailed, clientHandler);
                });
                // 添加任务
                tasks.add(taskSupplier);
            } else if (!"false".equals(emulation)) {
                //开启仿真模式

            }
        }

        // 严格按顺序执行
        AsyncUtils.executeStrictlySequential(tasks);
    }

    /**
     * 启动充电桩客户端
     */
    private static void startChargeClient() {
        //获取所有充电桩配置
        Map<String, Map<String, String>> chargeLink = LinkYaml.getChargeLink();
        List<Supplier<CompletableFuture<Void>>> tasks = new ArrayList<>();
        //遍历所有充电桩配置
        for (Map.Entry<String, Map<String, String>> entry : chargeLink.entrySet()) {
            //客户端ID
            String clientId = entry.getKey();
            Map<String, String> value = entry.getValue();
            //通信协议
            String pact = value.get("pact");
            if (pact == null || pact.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置缺少通信协议数据"));
                continue;
            }
            // 获取通信协议
            ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
            if (protocolEnum == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置的通信协议 [" + pact + "] 获取不到枚举数据"));
                continue;
            }

            //品牌
            String brand = value.get("brand");
            if (brand == null || brand.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置缺少品牌数据"));
                continue;
            }
            BrandEnum brandEnum = BrandEnum.fromBrand(brand);
            if (brandEnum == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置的品牌 [" + brand + "] 获取不到枚举数据"));
                continue;
            }

            //设备种类
            String equipmentType = value.get("equipment_type");
            if (equipmentType == null || equipmentType.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置缺少设备种类数据"));
                continue;
            }
            LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
            if (linkEquipmentTypeEnum == null) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置的设备种类 [" + equipmentType + "] 获取不到枚举数据"));
                continue;
            }

            //地址
            String url = value.get("url");
            if (url == null || url.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("ChargePile配置缺少地址数据"));
                continue;
            }
            URI uri = URI.create(url);

            //连接失败次数
            String connectFailed = value.get("connect_failed");
            //启用状态
            String enable = value.get("enable");
            //仿真模式
            String emulation = value.get("emulation");

            //判断客户端是否在线
            boolean onlineClient = NettyClient.isClientOnline(equipmentType, clientId);
            if (!onlineClient && "true".equals(enable) && "false".equals(emulation)) {
                //获取通信协议选项
                IProtocolOption protocolOption = PROTOCOL_OPTION_MAP.get(protocolEnum);
                if (protocolOption == null) {
                    RcsLog.algorithmLog.error(RcsLog.formatTemplateRandom("ChargePile配置的通信协议 [" + pact + "] 获取不到通信协议选项数据"));
                    continue;
                }
                ClientHandler clientHandler = PROTOCOL_HANDLER_MAP.get(linkEquipmentTypeEnum);
                if (clientHandler == null) {
                    RcsLog.algorithmLog.error(RcsLog.formatTemplateRandom("ChargePile配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器数据"));
                    continue;
                }

                // 创建任务生成器
                Supplier<CompletableFuture<Void>> taskSupplier = AsyncUtils.runAsync(() -> {
                    startClient(protocolEnum, protocolOption, brandEnum, uri, linkEquipmentTypeEnum, clientId, connectFailed, clientHandler);
                });
                // 添加任务
                tasks.add(taskSupplier);
            } else if (!"false".equals(emulation)) {
                //开启仿真模式

            }
        }
        // 严格按顺序执行
        AsyncUtils.executeStrictlySequential(tasks);
    }

    /**
     * 启动中转系统客户端
     */
    private static void startTransferClient() {
        //获取所有中转系统配置
        Map<String, String> transferLink = LinkYaml.getTransferLink();
        List<Supplier<CompletableFuture<Void>>> tasks = new ArrayList<>();
        //客户端ID
        String clientId = transferLink.get("code");
        //通信协议
        String pact = transferLink.get("pact");
        if (pact == null || pact.isEmpty()) {
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("Transfer配置缺少通信协议数据"));
            return;
        }
        // 获取通信协议
        ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
        if (protocolEnum == null) {
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("Transfer配置的通信协议 [" + pact + "] 获取不到枚举数据"));
            return;
        }

        //设备种类
        String equipmentType = transferLink.get("equipment_type");
        if (equipmentType == null || equipmentType.isEmpty()) {
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("Transfer配置缺少设备种类数据"));
            return;
        }
        LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
        if (linkEquipmentTypeEnum == null) {
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("Transfer配置的设备种类 [" + equipmentType + "] 获取不到枚举数据"));
            return;
        }

        //地址
        String url = transferLink.get("url");
        if (url == null || url.isEmpty()) {
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom("Transfer配置缺少地址数据"));
            return;
        }
        URI uri = URI.create(url);

        //连接失败次数
        String connectFailed = transferLink.get("connect_failed");
        //启用状态
        String enable = transferLink.get("enable");
        //仿真模式
        String emulation = transferLink.get("emulation");

        //判断客户端是否在线
        boolean onlineClient = NettyClient.isClientOnline(equipmentType, clientId);
        if (!onlineClient && "true".equals(enable) && "false".equals(emulation)) {
            //获取通信协议选项
            IProtocolOption protocolOption = PROTOCOL_OPTION_MAP.get(protocolEnum);
            if (protocolOption == null) {
                RcsLog.algorithmLog.error(RcsLog.formatTemplateRandom("Transfer配置的通信协议 [" + pact + "] 获取不到通信协议选项数据"));
                return;
            }
            ClientHandler clientHandler = PROTOCOL_HANDLER_MAP.get(linkEquipmentTypeEnum);
            if (clientHandler == null) {
                RcsLog.algorithmLog.error(RcsLog.formatTemplateRandom("Transfer配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器数据"));
                return;
            }

            // 创建任务生成器
            Supplier<CompletableFuture<Void>> taskSupplier = AsyncUtils.runAsync(() -> {
                startClient(protocolEnum, protocolOption, BrandEnum.SLAMOPTO, uri, linkEquipmentTypeEnum, clientId, connectFailed, clientHandler);
            });
            // 添加任务
            tasks.add(taskSupplier);
        } else if (!"false".equals(emulation)) {
            //开启仿真模式

        }
        // 严格按顺序执行
        AsyncUtils.executeStrictlySequential(tasks);
    }

    /**
     * 创建客户端
     *
     * @param protocol       通信协议
     * @param protocolOption 通信协议配置
     * @param brand          品牌枚举
     * @param uri            地址
     * @param equipmentType  设备种类枚举
     * @param clientId       客户端ID
     * @param connectFailed  连接失败次数
     * @param handler        处理器
     * @return 是否成功
     */
    private static CompletableFuture<Boolean> startClient(ProtocolEnum protocol, IProtocolOption protocolOption, BrandEnum brand, URI uri, LinkEquipmentTypeEnum equipmentType, String clientId, String connectFailed, ClientHandler handler) {
        //创建客户端属性
        ClientAttribute clientAttribute = new ClientAttribute(uri, clientId, connectFailed, brand, equipmentType, protocol, protocolOption, handler);
        //启动客户端
        return new NettyClient(clientAttribute).start();
    }

}
