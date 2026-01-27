package com.ruinap.adapter.communicate.client;


import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.client.handler.ClientHandler;
import com.ruinap.adapter.communicate.client.handler.impl.TcpClientHandler;
import com.ruinap.adapter.communicate.client.handler.impl.WebSocketClientHandler;
import com.ruinap.adapter.communicate.client.protocol.TcpOption;
import com.ruinap.adapter.communicate.client.protocol.WebSocketOption;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.util.HashMap;
import java.util.Map;
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
    private CoreYaml coreYaml;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private VthreadPool vthreadPool;

    /**
     * 业务线程组
     */
    private static final EventExecutorGroup BUSINESS_GROUP = new DefaultEventExecutorGroup(16);
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
     * 核心工厂方法
     * 负责将 Spring 管理的 Bean (如 AlarmManager, TaskDispatcher) 注入到 NettyClient 中
     */
    public NettyClient create(ClientAttribute attribute) {
        return new NettyClient(attribute, BUSINESS_GROUP, coreYaml, alarmManager, vthreadPool);
    }

    /**
     * 获取协议配置选项 (用于 Manager 校验)
     */
    public IProtocolOption getProtocolOption(ProtocolEnum protocol) {
        return PROTOCOL_OPTION_MAP.get(protocol);
    }

    /**
     * 获取 Handler 工厂 (用于 Manager 校验)
     */
    public Supplier<ClientHandler> getHandlerFactory(LinkEquipmentTypeEnum type) {
        return PROTOCOL_HANDLER_FACTORY.get(type);
    }
}
