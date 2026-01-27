package com.ruinap.adapter.communicate.server.handler;

import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.base.handler.IBaseServerHandler;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import io.netty.channel.ChannelHandlerContext;

/**
 * 服务端处理接口
 *
 * @author qianye
 * @create 2025-04-29 15:14
 */
public interface IServerHandler extends IBaseServerHandler {

    /**
     * 触发时机：当 Handler 从 ChannelPipeline 中移除时调用
     * 用途：清理 Handler 级别的资源
     * <p>
     * 当关闭连接时，第三个调用handlerRemoved
     *
     * @param ctx 上下文
     */
    void handlerRemoved(ChannelHandlerContext ctx, ServerAttribute attribute);

    /**
     * 获取该处理器绑定的协议
     */
    ProtocolEnum getProtocol();

    /**
     * 获取该处理器绑定的路径 (无路径协议可返回空字符串)
     */
    default String getPath() {
        return "";
    }
}
