package com.ruinap.adapter.communicate.base;

import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.adapter.communicate.server.handler.impl.BusinessWebSocketHandler;
import com.ruinap.adapter.communicate.server.handler.impl.ConsoleWebSocketHandler;
import com.ruinap.adapter.communicate.server.handler.impl.Vda5050MqttServerHandler;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.enums.netty.ServerRouteEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端属性
 *
 * @author qianye
 * @create 2025-04-29 16:06
 */
@Setter
@Getter
public class ServerAttribute {

    @Autowired
    private VthreadPool vthreadPool;

    /**
     * 路径处理器映射表
     */
    public static final Map<String, IServerHandler> ROUTES = new HashMap<>();

    /*
      初始化路由表，根据 URL 判断该路由是否允许连接
     */
    static {
        // 可以根据需要添加更多路由
        ROUTES.put(ProtocolEnum.WEBSOCKET_SERVER.getProtocol() + ServerRouteEnum.CONSOLE.getRoute(), new ConsoleWebSocketHandler());
//        ROUTES.put(ProtocolEnum.WEBSOCKET_SERVER.getProtocol() + ServerRouteEnum.VISUAL.getRoute(), new VisualWebSocketHandler());
        ROUTES.put(ProtocolEnum.WEBSOCKET_SERVER.getProtocol() + ServerRouteEnum.BUSINESS.getRoute(), new BusinessWebSocketHandler());
//        ROUTES.put(ProtocolEnum.WEBSOCKET_SERVER.getProtocol() + ServerRouteEnum.SIMULATION.getRoute(), new SimulationWebSocketHandler());

        ROUTES.put(ProtocolEnum.MQTT_SERVER.getProtocol() + ServerRouteEnum.MQTT.getRoute(), new Vda5050MqttServerHandler());
    }

    /**
     * 端口
     */
    private Integer port;
    /**
     * 协议枚举
     */
    private ProtocolEnum protocol;
    /**
     * 协议处理器
     */
    private IProtocolOption<ServerBootstrap, NettyServer> protocolOption;
    /**
     * 主线程组，处理连接请求
     */
    private final EventLoopGroup bossGroup = NettyGlobalResources.getBossGroup();
    /**
     * 工作线程组，处理I/O操作
     * 默认CPU核心数*2
     */
    private EventLoopGroup workerGroup = NettyGlobalResources.getWorkerGroup();

    /**
     * 事件处理器
     */
//    private IServerHandler eventHandler;
    public ServerAttribute(IProtocolOption<ServerBootstrap, NettyServer> protocolOption, Integer port, ProtocolEnum protocol) {
        this.port = port;
        this.protocol = protocol;
        this.protocolOption = protocolOption;
        if (protocolOption == null) {
            RcsLog.consoleLog.error("请设置协议处理器");
        }
    }
}
