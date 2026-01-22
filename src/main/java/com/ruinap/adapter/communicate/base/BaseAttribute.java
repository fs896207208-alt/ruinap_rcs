package com.ruinap.adapter.communicate.base;

import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import io.netty.channel.EventLoopGroup;
import lombok.Getter;
import lombok.Setter;

/**
 * 通信属性基类
 *
 * @author qianye
 * @create 2026-01-22 17:17
 */
@Getter
@Setter
public abstract class BaseAttribute {

    /**
     * 协议类型 (TCP, MQTT, WebSocket, etc.)
     */
    protected ProtocolEnum protocol;

    /**
     * 协议配置策略
     */
    protected IProtocolOption protocolOption;

    /**
     * 工作线程组 (Worker Group)
     * Client 和 Server 都需要处理 IO 读写
     */
    protected final EventLoopGroup workerGroup = NettyGlobalResources.getWorkerGroup();

    public BaseAttribute(ProtocolEnum protocol, IProtocolOption protocolOption) {
        this.protocol = protocol;
        this.protocolOption = protocolOption;
    }
}
