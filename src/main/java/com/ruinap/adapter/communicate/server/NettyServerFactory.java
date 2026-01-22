package com.ruinap.adapter.communicate.server;


import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.server.protocol.MqttOption;
import com.ruinap.adapter.communicate.server.protocol.WebSocketOption;
import com.ruinap.adapter.communicate.server.registry.ServerHandlerRegistry;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.thread.VthreadPool;

/**
 * Netty服务端工厂类
 *
 * @author qianye
 * @create 2025-04-28 16:44
 */
@Component
public class NettyServerFactory {
    @Autowired
    private CoreYaml coreYaml;
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private ServerHandlerRegistry handlerRegistry;
    @Autowired
    private AlarmManager alarmManager;

    /**
     * 启动所有 Netty 服务端
     */
    public void startServer() {
        vthreadPool.execute(() -> {
            try {
                //获取ws端口
                Integer port = coreYaml.getNettyWebsocketPort();
                //启动WS协议服务端
                IProtocolOption protocol = new WebSocketOption();
                ServerAttribute attribute = new ServerAttribute(protocol, port, ProtocolEnum.WEBSOCKET_SERVER);
                NettyServer nettyServer = new NettyServer(attribute, handlerRegistry, alarmManager, vthreadPool);
                nettyServer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        vthreadPool.execute(() -> {
            try {
                //获取MQTT端口
                Integer port = coreYaml.getNettyMqttPort();
                //启动MQTT协议服务端
                IProtocolOption mqttProtocol = new MqttOption();
                ServerAttribute mqttAttribute = new ServerAttribute(mqttProtocol, port, ProtocolEnum.MQTT_SERVER);
                NettyServer mqttNettyServer = new NettyServer(mqttAttribute, handlerRegistry, alarmManager, vthreadPool);
                mqttNettyServer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
