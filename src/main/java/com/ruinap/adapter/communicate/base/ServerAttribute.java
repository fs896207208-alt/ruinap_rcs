package com.ruinap.adapter.communicate.base;

import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务端属性
 *
 * @author qianye
 * @create 2025-04-29 16:06
 */
@Setter
@Getter
public class ServerAttribute extends BaseAttribute {

    /**
     * 端口
     */
    private Integer port;
    /**
     * 设备类型 (非必需)
     */
    private LinkEquipmentTypeEnum equipmentType;
    /**
     * 主线程组，处理连接请求
     */
    private final EventLoopGroup bossGroup = NettyGlobalResources.getBossGroup();

    public ServerAttribute(IProtocolOption<ServerBootstrap, NettyServer> protocolOption, Integer port, ProtocolEnum protocol) {
        // 调用基类构造
        super(protocol, protocolOption);
        this.port = port;
        if (protocolOption == null) {
            RcsLog.consoleLog.error("请设置协议处理器");
        }
    }

    public ServerAttribute(IProtocolOption<ServerBootstrap, NettyServer> protocolOption, Integer port, ProtocolEnum protocol, LinkEquipmentTypeEnum equipmentType) {
        this(protocolOption, port, protocol);
        this.equipmentType = equipmentType;
    }
}
