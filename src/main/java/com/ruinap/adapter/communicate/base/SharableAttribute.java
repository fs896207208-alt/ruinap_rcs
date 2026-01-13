package com.ruinap.adapter.communicate.base;

import com.slamopto.common.VthreadPool;
import com.slamopto.communicate.client.NettyClient;
import com.slamopto.communicate.server.NettyServer;
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
    /**
     * 工作线程组，处理I/O操作
     */
    public static EventLoopGroup workerGroup = new NioEventLoopGroup(VthreadPool.getDaemonThread("swvt-"));

    /**
     * 停止工作线程组
     */
    public static void shutdown() {
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
