package com.ruinap.adapter.communicate.client;


import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.client.handler.ClientHandler;
import com.ruinap.adapter.communicate.client.handler.impl.TcpClientHandler;
import com.ruinap.adapter.communicate.client.handler.impl.WebSocketClientHandler;
import com.ruinap.adapter.communicate.client.protocol.TcpOption;
import com.ruinap.adapter.communicate.client.protocol.WebSocketOption;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.infra.async.AsyncService;
import com.ruinap.infra.async.OrderedTaskDispatcher;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.enums.netty.BrandEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;

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
@Component
public class NettyClientFactory {

    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private AsyncService asyncService;
    @Autowired
    private CoreYaml coreYaml;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private OrderedTaskDispatcher taskDispatcher;
    /**
     * 通信协议选项
     */
    private static final Map<ProtocolEnum, IProtocolOption> PROTOCOL_OPTION_MAP = new HashMap<>();
    /**
     * 协议处理器工厂
     * 作用：存储的是“生产 Handler 的工厂方法”，而不是 Handler 实例本身
     */
    private static final Map<LinkEquipmentTypeEnum, Supplier<ClientHandler>> PROTOCOL_HANDLER_FACTORY = new HashMap<>();

    static {
        // 初始化协议选项
        PROTOCOL_OPTION_MAP.put(ProtocolEnum.WEBSOCKET_CLIENT, new WebSocketOption());
        PROTOCOL_OPTION_MAP.put(ProtocolEnum.TCP_CLIENT, new TcpOption());

        // 初始化协议处理器
        PROTOCOL_HANDLER_FACTORY.put(LinkEquipmentTypeEnum.AGV, WebSocketClientHandler::new);
        PROTOCOL_HANDLER_FACTORY.put(LinkEquipmentTypeEnum.TRANSFER, WebSocketClientHandler::new);
        PROTOCOL_HANDLER_FACTORY.put(LinkEquipmentTypeEnum.CHARGE_PILE, TcpClientHandler::new);
    }

    /**
     * 启动所有 Netty 客户端
     */
    public void startClient() {

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
    private void startAgvClient() {
        //获取所有AGV配置
        Map<String, Map<String, String>> agvLink = linkYaml.getAgvLink();
        List<Supplier<CompletableFuture<Void>>> tasks = new ArrayList<>();
        //遍历所有AGV配置
        for (Map.Entry<String, Map<String, String>> entry : agvLink.entrySet()) {
            //客户端ID
            String clientId = entry.getKey();
            Map<String, String> value = entry.getValue();
            //通信协议
            String pact = value.get("pact");
            if (pact == null || pact.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置缺少通信协议数据");
                continue;
            }
            // 获取通信协议
            ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
            if (protocolEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的通信协议 [" + pact + "] 获取不到枚举数据");
                continue;
            }

            //设备种类
            String equipmentType = value.get("equipment_type");
            if (equipmentType == null || equipmentType.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置缺少设备种类数据");
                continue;
            }
            LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
            if (linkEquipmentTypeEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的设备种类 [" + equipmentType + "] 获取不到枚举数据");
                continue;
            }

            Supplier<ClientHandler> handlerSupplier = PROTOCOL_HANDLER_FACTORY.get(linkEquipmentTypeEnum);
            if (handlerSupplier == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器工厂");
                continue;
            }

            //地址
            String url = value.get("url");
            if (url == null || url.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置缺少地址数据");
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
                    RcsLog.algorithmLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的通信协议 [" + pact + "] 获取不到通信协议选项数据");
                    continue;
                }

                // 创建任务生成器
                Supplier<CompletableFuture<Void>> taskSupplier = asyncService.runAsync(() -> {
                    // 注意这里：handlerSupplier.get()
                    startClient(protocolEnum, protocolOption, uri, linkEquipmentTypeEnum, clientId, connectFailed, handlerSupplier.get());
                });
                // 添加任务
                tasks.add(taskSupplier);
            } else if (!"false".equals(emulation)) {
                //开启仿真模式

            }
        }

        // 严格按顺序执行
        asyncService.executeStrictlySequential(tasks);
    }

    /**
     * 启动充电桩客户端
     */
    private void startChargeClient() {
        //获取所有充电桩配置
        Map<String, Map<String, String>> chargeLink = linkYaml.getChargeLink();
        List<Supplier<CompletableFuture<Void>>> tasks = new ArrayList<>();
        //遍历所有充电桩配置
        for (Map.Entry<String, Map<String, String>> entry : chargeLink.entrySet()) {
            //客户端ID
            String clientId = entry.getKey();
            Map<String, String> value = entry.getValue();
            //通信协议
            String pact = value.get("pact");
            if (pact == null || pact.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置缺少通信协议数据");
                continue;
            }
            // 获取通信协议
            ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
            if (protocolEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置的通信协议 [" + pact + "] 获取不到枚举数据");
                continue;
            }

            //品牌
            String brand = value.get("brand");
            if (brand == null || brand.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置缺少品牌数据");
                continue;
            }
            BrandEnum brandEnum = BrandEnum.fromBrand(brand);
            if (brandEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置的品牌 [" + brand + "] 获取不到枚举数据");
                continue;
            }

            //设备种类
            String equipmentType = value.get("equipment_type");
            if (equipmentType == null || equipmentType.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置缺少设备种类数据");
                continue;
            }
            LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
            if (linkEquipmentTypeEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置的设备种类 [" + equipmentType + "] 获取不到枚举数据");
                continue;
            }

            //地址
            String url = value.get("url");
            if (url == null || url.isEmpty()) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置缺少地址数据");
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
                    RcsLog.algorithmLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置的通信协议 [" + pact + "] 获取不到通信协议选项数据");
                    continue;
                }

                Supplier<ClientHandler> handlerSupplier = PROTOCOL_HANDLER_FACTORY.get(linkEquipmentTypeEnum);
                if (handlerSupplier == null) {
                    RcsLog.algorithmLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "ChargePile配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器工厂");
                    continue;
                }

                // 创建任务生成器
                Supplier<CompletableFuture<Void>> taskSupplier = asyncService.runAsync(() -> {
                    startClient(protocolEnum, protocolOption, uri, linkEquipmentTypeEnum, clientId, connectFailed, handlerSupplier.get());
                });
                // 添加任务
                tasks.add(taskSupplier);
            } else if (!"false".equals(emulation)) {
                //开启仿真模式

            }
        }
        // 严格按顺序执行
        asyncService.executeStrictlySequential(tasks);
    }

    /**
     * 启动中转系统客户端
     */
    private void startTransferClient() {
        //获取所有中转系统配置
        Map<String, String> transferLink = linkYaml.getTransferLink();
        List<Supplier<CompletableFuture<Void>>> tasks = new ArrayList<>();
        //客户端ID
        String clientId = transferLink.get("code");
        //通信协议
        String pact = transferLink.get("pact");
        if (pact == null || pact.isEmpty()) {
            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置缺少通信协议数据");
            return;
        }
        // 获取通信协议
        ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
        if (protocolEnum == null) {
            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置的通信协议 [" + pact + "] 获取不到枚举数据");
            return;
        }

        //设备种类
        String equipmentType = transferLink.get("equipment_type");
        if (equipmentType == null || equipmentType.isEmpty()) {
            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置缺少设备种类数据");
            return;
        }
        LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
        if (linkEquipmentTypeEnum == null) {
            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置的设备种类 [" + equipmentType + "] 获取不到枚举数据");
            return;
        }

        //地址
        String url = transferLink.get("url");
        if (url == null || url.isEmpty()) {
            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置缺少地址数据");
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
                RcsLog.algorithmLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置的通信协议 [" + pact + "] 获取不到通信协议选项数据");
                return;
            }

            Supplier<ClientHandler> handlerSupplier = PROTOCOL_HANDLER_FACTORY.get(linkEquipmentTypeEnum);
            if (handlerSupplier == null) {
                RcsLog.algorithmLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器工厂");
                return;
            }

            // 创建任务生成器
            Supplier<CompletableFuture<Void>> taskSupplier = asyncService.runAsync(() -> {
                startClient(protocolEnum, protocolOption, uri, linkEquipmentTypeEnum, clientId, connectFailed, handlerSupplier.get());
            });
            // 添加任务
            tasks.add(taskSupplier);
        } else if (!"false".equals(emulation)) {
            //开启仿真模式

        }
        // 严格按顺序执行
        asyncService.executeStrictlySequential(tasks);
    }

    /**
     * 创建客户端
     *
     * @param protocol       通信协议
     * @param protocolOption 通信协议配置
     * @param uri            地址
     * @param equipmentType  设备种类枚举
     * @param clientId       客户端ID
     * @param connectFailed  连接失败次数
     * @param handler        处理器
     * @return 是否成功
     */
    private CompletableFuture<Boolean> startClient(ProtocolEnum protocol, IProtocolOption protocolOption, URI uri, LinkEquipmentTypeEnum equipmentType, String clientId, String connectFailed, ClientHandler handler) {
        //创建客户端属性
        ClientAttribute clientAttribute = new ClientAttribute(uri, clientId, equipmentType, connectFailed, protocolOption, protocol, handler);
        //启动客户端
        return new NettyClient(clientAttribute, taskDispatcher, coreYaml, alarmManager, vthreadPool).start();
    }

}
