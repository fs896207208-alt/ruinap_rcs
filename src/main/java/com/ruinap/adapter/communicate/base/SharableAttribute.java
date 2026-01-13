package com.ruinap.adapter.communicate.base;

import com.ruinap.adapter.communicate.client.NettyClient;
import com.ruinap.adapter.communicate.server.NettyServer;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Data;

import java.util.List;

/**
 * 共享属性
 *
 * @author qianye
 * @create 2025-04-24 16:04
 */
@Data
public class SharableAttribute {

    @Autowired
    private VthreadPool vthreadPool;

    /**
     * 工作线程组，处理I/O操作
     */
    public EventLoopGroup workerGroup = new NioEventLoopGroup(vthreadPool.getDaemonThread("swvt-"));

    /**
     * 停止工作线程组
     */
    public void shutdown() {
        if (workerGroup != null) {
            //停止之前先将服务端和客户端关闭
            List<NettyServer> servers = NettyServer.getServer();
            for (NettyServer server : servers) {
                server.shutdown();
                server.getAttribute().getBossGroup().shutdownGracefully();
            }
            NettyClient.closeClient();
            workerGroup.shutdownGracefully();
        }
    }
}
