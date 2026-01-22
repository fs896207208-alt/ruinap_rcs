package com.ruinap.adapter.communicate.base;

import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
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
public class ServerAttribute {

    @Autowired
    private VthreadPool vthreadPool;

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
