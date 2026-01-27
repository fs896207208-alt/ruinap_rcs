package com.ruinap.adapter.communicate;

import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.client.NettyClient;
import com.ruinap.adapter.communicate.client.NettyClientFactory;
import com.ruinap.adapter.communicate.client.handler.ClientHandler;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.NettyServerFactory;
import com.ruinap.adapter.communicate.server.protocol.MqttOption;
import com.ruinap.adapter.communicate.server.protocol.WebSocketOption;
import com.ruinap.infra.async.AsyncService;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.enums.netty.BrandEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.enums.netty.ServerRouteEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.EventListener;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.framework.core.event.netty.NettyConnectionEvent;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Netty 管理类
 *
 * @author qianye
 * @create 2026-01-23 10:33
 */
@Component
public class NettyManager {
    @Autowired
    private NettyClientFactory clientFactory;
    @Autowired
    private NettyServerFactory serverFactory;
    @Autowired
    private CoreYaml coreYaml;
    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private AsyncService asyncService;

    /**
     * 全局统一连接池
     * Key: Format "EQUIPMENT_TYPE::ID" (e.g., "AGV::1001", "CHARGE_PILE::CP_01")
     * Value: 底层 Netty Channel
     */
    private final Map<String, Channel> globalChannelMap = new ConcurrentHashMap<>();
    /**
     * 维护运行中的 Server 实例 (Key: Port)
     */
    private final Map<Integer, NettyServer> runningServers = new ConcurrentHashMap<>();
    /**
     * 维护运行中的 Client 实例 (Key: ConnectionKey)
     */
    private final Map<String, NettyClient> activeClients = new ConcurrentHashMap<>();

    // ========================================================================
    // 事件监听处理
    // ========================================================================

    /**
     * 统一连接状态事件监听器
     * <p>
     * 核心逻辑：
     * 1. <strong>通用处理</strong>：无论 Client 还是 Server，只要上线就注册到全局路由表，下线就移除。
     * 2. <strong>差异处理</strong>：如果是 Client (主动连接)，还需要额外维护 activeClients 列表。
     * 3. <strong>冲突处理</strong>：检测 ID 冲突，踢掉旧连接（防止僵尸连接）。
     */
    @EventListener
    public void handleConnectionEvent(NettyConnectionEvent event) {
        String id = event.getId();
        LinkEquipmentTypeEnum type = event.getType();
        Channel channel = event.getChannel();
        NettyConnectionEvent.State state = event.getState();
        NettyConnectionEvent.ConnectSource source = event.getConnectSource();

        // 1. 基础防御：忽略无效的身份信息
        if (id == null || type == null) {
            return;
        }

        // 生成统一资源 Key (例如 "AGV::1001")
        String key = generateKey(type, id);

        // ====================================================================
        //  处理上线 (CONNECTED)
        // ====================================================================
        if (state == NettyConnectionEvent.State.CONNECTED) {

            // 【通用动作】注册路由 & 冲突检测 (这是你刚才缺失的关键逻辑！)
            // put 方法会返回 Map 中该 Key 之前对应的值 (旧连接)
            Channel oldChannel = globalChannelMap.put(key, channel);

            // 如果存在旧连接，且旧连接还活着，且 ID 不等于新连接 ID -> 说明是冲突/重复登录
            if (oldChannel != null && oldChannel.isActive() && !oldChannel.id().equals(channel.id())) {
                RcsLog.consoleLog.warn("ID冲突 [{}]，强制踢出旧连接: {}", key, oldChannel.id());
                oldChannel.close(); // 必须关闭，否则会泄露
            }

            // 【客户端特有动作】维护实例引用
            // 只有主动连接的 Client 才需要维护对象引用，防止被 GC，并用于断线重连逻辑
            if (source == NettyConnectionEvent.ConnectSource.CLIENT) {
                if (event.getSource() instanceof NettyClient) {
                    activeClients.put(key, (NettyClient) event.getSource());
                }
            }

            RcsLog.consoleLog.info("NettyManager: [{}] 设备上线注册 [{}]", source, key);
        }

        // ====================================================================
        //  处理下线 (DISCONNECTED)
        // ====================================================================
        else if (state == NettyConnectionEvent.State.DISCONNECTED) {

            // 【通用动作】移除路由 (双重检查机制)
            // computeIfPresent 是原子操作，线程安全
            globalChannelMap.computeIfPresent(key, (k, existingChannel) -> {
                // 只有当 Map 里的 Channel ID 等于当前断开的 Channel ID 时，才移除。
                // 防止场景：断线(Event A) -> 重连成功(Event B, 写入Map) -> Event A 延迟到达(误删新连接)
                if (existingChannel.id().equals(channel.id())) {
                    return null; // 返回 null 代表移除该 Key
                }
                return existingChannel; // 返回原值 代表保留 (说明 Map 里已经是新连接了)
            });

            // 【客户端特有动作】移除实例引用
            if (source == NettyConnectionEvent.ConnectSource.CLIENT) {
                activeClients.computeIfPresent(key, (k, client) -> {
                    // 同样进行安全检查：防止移除了正在运行的新 Client 实例
                    // 判断 Client 持有的 Context 是否为空，或者 Channel 是否活跃
                    if (client.getAttribute().getContext() == null ||
                            !client.getAttribute().getContext().channel().isActive()) {
                        return null; // 移除
                    }
                    return client; // 保留
                });
            }

            RcsLog.consoleLog.info("NettyManager: [{}] 设备下线移除 [{}]", source, key);
        }
    }

    // ========================================================================
    // 1. 服务端启动逻辑
    // ========================================================================

    private void startServers() {
        // 1. 启动 WebSocket 服务
        vthreadPool.execute(() -> {
            try {
                // 获取ws端口
                Integer port = coreYaml.getNettyWebsocketPort();
                if (port != null) {
                    IProtocolOption protocol = new WebSocketOption();
                    ServerAttribute attribute = new ServerAttribute(protocol, port, ProtocolEnum.WEBSOCKET_SERVER);
                    startServerInternal(attribute);
                }
            } catch (Exception e) {
                RcsLog.consoleLog.error("WebSocket Server 启动失败", e);
                // throw new RuntimeException(e); // 建议记录日志后不抛出，以免阻断其他流程
            }
        });

        // 2. 启动 MQTT 服务
        vthreadPool.execute(() -> {
            try {
                // 获取MQTT端口
                Integer port = coreYaml.getNettyMqttPort();
                if (port != null) {
                    IProtocolOption mqttProtocol = new MqttOption();
                    ServerAttribute mqttAttribute = new ServerAttribute(mqttProtocol, port, ProtocolEnum.MQTT_SERVER);
                    startServerInternal(mqttAttribute);
                }
            } catch (Exception e) {
                RcsLog.consoleLog.error("MQTT Server 启动失败", e);
            }
        });
    }

    private void startServerInternal(ServerAttribute attribute) {
        if (runningServers.containsKey(attribute.getPort())) {
            return;
        }
        // 使用工厂创建实例
        NettyServer server = serverFactory.create(attribute);
        server.start();
        runningServers.put(attribute.getPort(), server);
        RcsLog.consoleLog.info("服务端启动成功 Port: {}, Protocol: {}", attribute.getPort(), attribute.getProtocol());
    }

    /**
     * 根据协议获取服务端实例
     */
    public NettyServer getServer(ProtocolEnum protocolEnum) {
        for (NettyServer server : runningServers.values()) {
            if (server.getAttribute().getProtocol() == protocolEnum) {
                return server;
            }
        }
        return null;
    }

    /**
     * 根据协议和端口获取服务端实例
     *
     * @param protocolEnum 协议类型
     * @param port         端口
     * @return 服务端实例
     */
    public NettyServer getServer(ProtocolEnum protocolEnum, Integer port) {
        if (port == null) {
            return null;
        }

        // 1. 直接通过 Key (Port) 获取，性能最高 (O(1))
        NettyServer server = runningServers.get(port);
        // 2. 如果找到了，再校验一下协议是否匹配 (防止端口对应了其他协议的服务)
        if (server != null && server.getAttribute().getProtocol() == protocolEnum) {
            return server;
        }
        return null;
    }

    /**
     * 获取所有运行中的服务端
     */
    public Collection<NettyServer> getAllServers() {
        return runningServers.values();
    }

    /**
     * 发送消息给所有指定路由的服务端连接 (广播)
     *
     * @param protocolEnum 协议类型
     * @param routeEnum    路由匹配规则
     * @param message      消息内容
     */
    public void sendMessageAll(ProtocolEnum protocolEnum, ServerRouteEnum routeEnum, Object message) {
        // 1. 获取对应的 Server 实例
        NettyServer server = getServer(protocolEnum);
        if (server == null) {
            RcsLog.consoleLog.warn("广播失败: 未找到协议 [{}] 对应的服务端实例", protocolEnum);
            return;
        }

        // 2. 遍历该实例的连接路径
        // 注意：paths 是 ConcurrentHashMap，遍历是安全的
        for (Map.Entry<String, String> entry : server.getPaths().entrySet()) {
            String serverId = entry.getKey();
            String route = entry.getValue();

            // 路由匹配
            if (route != null && route.equalsIgnoreCase(routeEnum.getRoute())) {
                ChannelHandlerContext ctx = server.getContexts().get(serverId);
                if (ctx != null && ctx.channel().isActive()) {
                    // 使用 Server 自身的协议选项进行消息包装
                    ctx.writeAndFlush(server.getAttribute().getProtocolOption().wrapMessage(message));
                }
            }
        }
    }

    // ========================================================================
    // 2. 客户端启动逻辑
    // ========================================================================

    private void startClients() {
        // 启动AGV客户端
        startAgvClient();

        // 启动充电桩客户端
        startChargeClient();

        // 启动中转系统客户端
        startTransferClient();
    }

    /**
     * 启动AGV客户端 (保留原 NettyClientFactory 逻辑)
     */
    private void startAgvClient() {
        //获取所有AGV配置
        Map<String, Map<String, String>> agvLink = linkYaml.getAgvLink();
        if (agvLink == null) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        //遍历所有AGV配置
        for (Map.Entry<String, Map<String, String>> entry : agvLink.entrySet()) {
            String clientId = entry.getKey();
            Map<String, String> value = entry.getValue();

            //通信协议
            String pact = value.get("pact");
            ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
            if (protocolEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的通信协议 [" + pact + "] 获取不到枚举数据");
                continue;
            }

            //设备种类
            String equipmentType = value.get("equipment_type");
            LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
            if (linkEquipmentTypeEnum == null) {
                RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的设备种类 [" + equipmentType + "] 获取不到枚举数据");
                continue;
            }

            // 【关键】使用 clientFactory 获取 HandlerFactory 进行校验
            Supplier<ClientHandler> handlerSupplier = clientFactory.getHandlerFactory(linkEquipmentTypeEnum);
            if (handlerSupplier == null) {
                // 保留原日志：AGV使用 communicateLog.error
                RcsLog.communicateLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "AGV配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器工厂");
                continue;
            }

            //地址
            String url = value.get("url");
            if (url == null || url.isEmpty()) {
                continue;
            }
            URI uri = URI.create(url);

            String connectFailed = value.get("connect_failed");
            String enable = value.get("enable");
            String emulation = value.get("emulation");

            //判断客户端是否在线
            boolean onlineClient = this.isClientOnline(equipmentType, clientId);
            if (!onlineClient && "true".equals(enable) && "false".equals(emulation)) {
                // 获取协议配置
                IProtocolOption protocolOption = clientFactory.getProtocolOption(protocolEnum);
                if (protocolOption == null) {
                    continue;
                }

                // 创建任务
                Supplier<CompletableFuture<Void>> taskSupplier = asyncService.runAsync(() -> {
                    try {
                        doStartClient(protocolEnum, protocolOption, uri, linkEquipmentTypeEnum, clientId, connectFailed, handlerSupplier.get()).join();
                    } catch (Exception e) {
                        RcsLog.consoleLog.error("设备 [{}] 启动异常", clientId, e);
                    }
                });
                futures.add(taskSupplier.get());
            }
        }

        // 等待所有AGV启动任务完成 (不阻塞主线程，等待所有连接尝试结束)
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                RcsLog.consoleLog.error("AGV 批量启动过程中发生异常", e);
            }
        }
    }

    /**
     * 启动充电桩客户端 (保留原 NettyClientFactory 逻辑)
     */
    private void startChargeClient() {
        Map<String, Map<String, String>> chargeLink = linkYaml.getChargeLink();
        if (chargeLink == null) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : chargeLink.entrySet()) {
            String clientId = entry.getKey();
            Map<String, String> value = entry.getValue();

            String pact = value.get("pact");
            ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
            if (protocolEnum == null) {
                continue;
            }

            String brand = value.get("brand");
            BrandEnum brandEnum = BrandEnum.fromBrand(brand);
            if (brandEnum == null) {
                continue;
            }

            String equipmentType = value.get("equipment_type");
            LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
            if (linkEquipmentTypeEnum == null) {
                continue;
            }

            // 获取 Handler
            Supplier<ClientHandler> handlerSupplier = clientFactory.getHandlerFactory(linkEquipmentTypeEnum);
            if (handlerSupplier == null) {
                continue;
            }

            String url = value.get("url");
            if (url == null) {
                continue;
            }
            URI uri = URI.create(url);

            String connectFailed = value.get("connect_failed");
            String enable = value.get("enable");
            String emulation = value.get("emulation");

            boolean onlineClient = this.isClientOnline(equipmentType, clientId);
            if (!onlineClient && "true".equals(enable) && "false".equals(emulation)) {
                IProtocolOption protocolOption = clientFactory.getProtocolOption(protocolEnum);
                if (protocolOption != null) {
                    Supplier<CompletableFuture<Void>> taskSupplier = asyncService.runAsync(() -> {
                        try {
                            doStartClient(protocolEnum, protocolOption, uri, linkEquipmentTypeEnum, clientId, connectFailed, handlerSupplier.get()).join();
                        } catch (Exception e) {
                            RcsLog.consoleLog.error("设备 [{}] 启动异常", clientId, e);
                        }
                    });
                    futures.add(taskSupplier.get());
                }
            }
        }
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                RcsLog.consoleLog.error("充电桩批量启动过程中发生异常", e);
            }
        }
    }

    /**
     * 启动中转系统客户端 (保留原 NettyClientFactory 逻辑)
     */
    private void startTransferClient() {
        Map<String, String> transferLink = linkYaml.getTransferLink();
        if (transferLink == null) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        String clientId = transferLink.get("code");
        String pact = transferLink.get("pact");
        ProtocolEnum protocolEnum = ProtocolEnum.fromProtocol(pact);
        if (protocolEnum == null) {
            return;
        }

        String equipmentType = transferLink.get("equipment_type");
        LinkEquipmentTypeEnum linkEquipmentTypeEnum = LinkEquipmentTypeEnum.fromEquipmentType(equipmentType);
        if (linkEquipmentTypeEnum == null) {
            return;
        }

        // 获取 Handler
        Supplier<ClientHandler> handlerSupplier = clientFactory.getHandlerFactory(linkEquipmentTypeEnum);
        if (handlerSupplier == null) {
            // 保留原日志：Transfer使用 algorithmLog.error
            RcsLog.algorithmLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "Transfer配置的设备种类 [" + linkEquipmentTypeEnum + "] 获取不到处理器工厂");
            return;
        }

        String url = transferLink.get("url");
        if (url == null) {
            return;
        }
        URI uri = URI.create(url);

        String connectFailed = transferLink.get("connect_failed");
        String enable = transferLink.get("enable");
        String emulation = transferLink.get("emulation");

        boolean onlineClient = this.isClientOnline(equipmentType, clientId);
        if (!onlineClient && "true".equals(enable) && "false".equals(emulation)) {
            IProtocolOption protocolOption = clientFactory.getProtocolOption(protocolEnum);
            if (protocolOption != null) {
                Supplier<CompletableFuture<Void>> taskSupplier = asyncService.runAsync(() -> {
                    try {
                        doStartClient(protocolEnum, protocolOption, uri, linkEquipmentTypeEnum, clientId, connectFailed, handlerSupplier.get()).join();
                    } catch (Exception e) {
                        RcsLog.consoleLog.error("设备 [{}] 启动异常", clientId, e);
                    }
                });
                futures.add(taskSupplier.get());
            }
        }

        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                RcsLog.consoleLog.error("中转设备启动过程中发生异常", e);
            }
        }
    }

    /**
     * 内部通用启动方法
     */
    private CompletableFuture<Boolean> doStartClient(ProtocolEnum protocol, IProtocolOption protocolOption, URI uri,
                                                     LinkEquipmentTypeEnum equipmentType, String clientId,
                                                     String connectFailed, ClientHandler handler) {
        // 1. 构建 POJO
        ClientAttribute clientAttribute = new ClientAttribute(uri, clientId, equipmentType, connectFailed, protocolOption, protocol, handler);

        // 2. 调用 Factory 创建实例
        NettyClient client = clientFactory.create(clientAttribute);

        String key = generateKey(equipmentType, clientId);

        // 3. 启动
        return client.start().thenApply(success -> {
            if (success) {
                // 启动成功，放入管理 Map
                activeClients.put(key, client);
                RcsLog.consoleLog.info("客户端启动成功: {}", key);
            } else {
                RcsLog.consoleLog.error("客户端启动失败: {}", key);
            }
            // 【关键】必须返回 boolean 值，传递给外部调用者
            return success;
        });
    }

    // ========================================================================
    // 3. 统一发送接口 & 注册接口
    // ========================================================================

    /**
     * 判断设备是否在线
     * <p>
     * 只要连接在 globalChannelMap 中且 Channel 活跃，即视为在线。
     * 支持 Client 模式和 Server 模式的所有连接。
     *
     * @param equipmentType 设备种类
     * @param clientId      客户端ID
     * @return 是否在线
     */
    public boolean isClientOnline(String equipmentType, String clientId) {
        String key = generateKey(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), clientId);
        Channel channel = globalChannelMap.get(key);
        return channel != null && channel.isActive();
    }

    public boolean isClientOnline(LinkEquipmentTypeEnum type, String clientId) {
        return isClientOnline(type.getEquipmentType(), clientId);
    }

    /**
     * 获取客户端的计数器
     *
     * @param equipmentType 设备类型
     * @param clientId      客户端ID
     * @return 当前 +1 的计数
     */
    public Long getClientCounter(String equipmentType, String clientId) {
        String key = generateKey(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), clientId);
        NettyClient client = activeClients.get(key);
        if (client == null) {
            return 0L;
        }
        AtomicLong atomicLong = client.getAttribute().getRequestId();
        return atomicLong != null ? atomicLong.incrementAndGet() : 0L;
    }

    /**
     * 关闭所有客户端连接
     *
     * @return 关闭状态 true：关闭成功 false：关闭失败
     */
    public void closeClient() {
        //获取客户端属性
        Collection<NettyClient> clients = activeClients.values();
        for (NettyClient client : clients) {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * 关闭客户端连接
     *
     * @param equipmentType 设备类型
     * @param clientId      客户端ID
     * @return 关闭状态 true：关闭成功 false：关闭失败
     */
    public boolean closeClient(String equipmentType, String clientId) {
        String key = generateKey(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), clientId);
        NettyClient client = activeClients.get(key);
        if (client != null) {
            client.shutdown();
            activeClients.remove(key);
            return true;
        }
        return false;
    }

    /**
     * 统一发送消息 (Fire and Forget)
     */
    public void sendMessage(LinkEquipmentTypeEnum type, String id, Object msg) {
        String key = generateKey(type, id);

        // 1. 优先尝试 Client 模式 (主动连接)
        NettyClient client = activeClients.get(key);
        if (client != null) {
            client.sendMessage(msg);
            return;
        }

        // 2. 尝试 Server 模式 (被动连接)
        Channel channel = globalChannelMap.get(key);
        if (channel != null && channel.isActive()) {
            // 查找归属的 Server 实例
            NettyServer server = null;

            // 方式 A: 属性查找 (O(1))
            if (channel.hasAttr(NettyServer.SERVER_REF_KEY)) {
                server = channel.attr(NettyServer.SERVER_REF_KEY).get();
            }

            // 方式 B: 遍历查找 (兜底)
            if (server == null) {
                String fullChannelId = channel.id().toString();
                for (NettyServer s : runningServers.values()) {
                    if (s.getChannelIds().containsKey(fullChannelId)) {
                        server = s;
                        break;
                    }
                }
            }

            if (server != null) {
                // 调用新加的 void 重载方法，确保经过 wrapMessage 处理
                server.sendMessage(id, msg);
            } else {
                // 实在找不到 Server，只能死马当活马医，直接发（可能会因为未包装而出错，但总比不发好）
                // 建议打个 Warn 日志
                RcsLog.communicateLog.warn("未找到归属Server，尝试直接发送原生消息: {}", key);
                channel.writeAndFlush(msg);
            }
        } else {
            RcsLog.communicateLog.warn("发送失败，设备离线或未注册: {}", key);
        }
    }

    /**
     * 指定协议发送消息 (Fire and Forget)
     * <p>
     * 场景：明确知道设备是通过某种协议（如 MQTT）接入的，直接指定协议和ID进行发送。
     * 这种方式不依赖 globalChannelMap 的 Type::ID 索引，而是直接去 Server 内部查找。
     *
     * @param protocol 协议类型 (如 MQTT, WEBSOCKET_SERVER)
     * @param id       设备ID (对于 MQTT 是 ClientId，对于 WebSocket 是连接ID或业务ID)
     * @param msg      消息内容
     */
    public void sendMessage(ProtocolEnum protocol, String id, Object msg) {
        if (protocol == null || id == null) {
            RcsLog.communicateLog.warn("发送失败: 协议或ID为空");
            return;
        }

        boolean sent = false;

        // 1. 遍历所有运行中的 Server
        for (NettyServer server : runningServers.values()) {
            // 2. 筛选出协议匹配的 Server
            if (server.getAttribute().getProtocol() == protocol) {

                // 3. 尝试从 Server 内部的 Context Map 中查找连接
                // 注意：这里依赖 Server 内部的 Key 生成策略 (通常是 ClientID)
                ChannelHandlerContext ctx = server.getContexts().get(id);

                if (ctx != null && ctx.channel().isActive()) {
                    // 4. 找到了活跃连接，直接发送
                    server.sendMessage(id, msg);
                    sent = true;

                    // 找到一个就停止 (通常 ID 在同协议下是唯一的)
                    break;
                }
            }
        }

        if (!sent) {
            // 只有当所有该协议的 Server 都找不到这个 ID 时才报警告
            RcsLog.communicateLog.warn("发送失败: 在协议 [{}] 的服务端未找到活跃设备 [{}]", protocol, id);
        }
    }

    /**
     * 统一发送消息 (同步等待响应)
     * <p>
     * 限制：目前仅支持本系统作为 Client 主动发起的连接。
     * 原因：Server 端接入的连接没有 NettyClient 包装器来维护 Future 映射。
     */
    public <T> CompletableFuture<T> sendMessage(LinkEquipmentTypeEnum type, String id, Long requestId, Object msg, Class<T> responseType) {
        String key = generateKey(type, id);

        // 1. 优先尝试 Client 模式 (主动连接)
        NettyClient client = activeClients.get(key);
        if (client != null) {
            return client.sendMessage(requestId, msg, responseType);
        }

        // 2. 尝试 Server 模式 (被动连接)
        // 只要在 globalChannelMap 里，说明连接是存在的
        Channel channel = globalChannelMap.get(key);
        if (channel != null && channel.isActive()) {

            // 【核心逻辑】找到该 Channel 归属的 NettyServer
            NettyServer server = null;

            // 方式 A: 优先尝试从 Channel 属性中获取 (依赖上一轮的 Dependency Injection 优化)
            if (channel.hasAttr(NettyServer.SERVER_REF_KEY)) {
                server = channel.attr(NettyServer.SERVER_REF_KEY).get();
            }

            // 方式 B: 兜底查找 (如果方式 A 未生效，或者连接是很早建立的)
            // 遍历所有运行中的 Server，检查谁管理这个 Channel ID
            if (server == null) {
                String fullChannelId = channel.id().toString();
                for (NettyServer s : runningServers.values()) {
                    // NettyServer 维护了 channelIds (FullID -> ShortID) 映射
                    if (s.getChannelIds().containsKey(fullChannelId)) {
                        server = s;
                        break;
                    }
                }
            }

            if (server != null) {
                // 调用我们在 NettyServer 中新增的重载方法
                return server.sendMessage(id, requestId, msg, responseType);
            } else {
                RcsLog.consoleLog.error("严重数据不一致: Channel 存在但找不到归属的 Server 实例. Key: {}", key);
            }
        }

        // 3. 失败处理
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("设备未连接或已离线: " + key));
        return future;
    }

    /**
     * 指定协议发送消息 (同步等待响应)
     * <p>
     * 场景：明确知道设备是通过某种协议（如 MQTT）接入的，直接指定协议和ID进行发送。
     * 相比自动路由，这种方式更精准，能避免 ID 冲突问题。
     *
     * @param protocol     协议类型 (如 MQTT, WEBSOCKET_SERVER)
     * @param id           设备ID (Client ID)
     * @param requestId    请求ID (用于全链路追踪)
     * @param msg          消息内容 (Object 类型)
     * @param responseType 响应类型 Class
     * @param <T>          响应泛型
     * @return CompletableFuture 异步结果
     */
    public <T> CompletableFuture<T> sendMessage(ProtocolEnum protocol, String id, Long requestId, Object msg, Class<T> responseType) {
        // 1. 基础校验
        if (protocol == null || id == null) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("发送失败: 协议或ID为空"));
            return future;
        }

        // 2. 遍历所有运行中的 Server
        for (NettyServer server : runningServers.values()) {
            // 3. 筛选出协议匹配的 Server
            if (server.getAttribute().getProtocol() == protocol) {

                // 4. 尝试从 Server 内部的 Context Map 中查找连接
                // 直接利用 Server 维护的 ClientID -> ChannelHandlerContext 映射
                ChannelHandlerContext ctx = server.getContexts().get(id);

                if (ctx != null && ctx.channel().isActive()) {
                    // 5. 找到了活跃连接，调用 NettyServer 的同步发送方法
                    return server.sendMessage(id, requestId, msg, responseType);
                }
            }
        }

        // 6. 兜底处理：未找到匹配的 Server 或 Connection
        RcsLog.communicateLog.warn("发送失败: 在协议 [{}] 的服务端未找到活跃设备 [{}]", protocol, id);

        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException(
                String.format("设备未连接或已离线. Protocol: %s, ID: %s", protocol, id)
        ));
        return future;
    }

    @PreDestroy
    public void shutdown() {
        RcsLog.consoleLog.info("正在关闭通讯模块...");
        activeClients.values().forEach(NettyClient::shutdown);
        runningServers.values().forEach(NettyServer::shutdown);
    }

    private String generateKey(LinkEquipmentTypeEnum type, String id) {
        if (type == null) {
            return "UNKNOWN ::" + id;
        }
        return type.name() + "::" + id;
    }
}
