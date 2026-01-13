package com.ruinap.adapter.communicate.server;

import com.slamopto.common.VthreadPool;
import com.slamopto.common.config.CoreYaml;
import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.ServerAttribute;
import com.slamopto.communicate.base.protocol.IProtocolOption;
import com.slamopto.communicate.server.protocol.MqttOption;
import com.slamopto.communicate.server.protocol.WebSocketOption;

/**
 * Netty服务端工厂类
 *
 * @author qianye
 * @create 2025-04-28 16:44
 */
public class NettyServerFactory {

    /**
     * 启动所有 Netty 服务端
     */
    public static void startServer() {
        VthreadPool.execute(() -> {
            try {
                //获取ws端口
                Integer port = CoreYaml.getNettyWebsocketPort();
                //启动WS协议服务端
                IProtocolOption protocol = new WebSocketOption();
                ServerAttribute attribute = new ServerAttribute(protocol, port, ProtocolEnum.WEBSOCKET_SERVER);
                NettyServer nettyServer = new NettyServer(attribute);
                nettyServer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        VthreadPool.execute(() -> {
            try {
                //获取MQTT端口
                Integer port = CoreYaml.getNettyMqttPort();
                //启动MQTT协议服务端
                IProtocolOption mqttProtocol = new MqttOption();
                ServerAttribute mqttAttribute = new ServerAttribute(mqttProtocol, port, ProtocolEnum.MQTT_SERVER);
                NettyServer mqttNettyServer = new NettyServer(mqttAttribute);
                mqttNettyServer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
