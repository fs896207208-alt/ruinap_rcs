package com.ruinap.adapter.communicate.server.handler;

import com.slamopto.log.RcsLog;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 空闲状态检测处理器
 *
 * @author qianye
 * @create 2025-01-28 23:27
 */
public class IdleEventHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE || event.state() == IdleState.WRITER_IDLE || event.state() == IdleState.ALL_IDLE) {
                // 如果客户端长时间没有读数据，认为客户端已断开，关闭连接
                RcsLog.consoleLog.warn(RcsLog.formatTemplate("客户端长时间未发送数据，关闭连接：" + ctx.channel().remoteAddress()));
                ctx.close(); // 关闭连接
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}