package com.ruinap.adapter.communicate.server.protocol;

import cn.hutool.json.JSONObject;
import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.ServerAttribute;
import com.slamopto.communicate.base.enums.AttributeKeyEnum;
import com.slamopto.communicate.base.protocol.IProtocolOption;
import com.slamopto.communicate.server.NettyServer;
import com.slamopto.communicate.server.handler.IdleEventHandler;
import com.slamopto.log.RcsLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket 协议参数实现
 * 支持标准化 WebSocket 连接 URI
 * 消息内容假设为 WebSocket 文本帧
 *
 * @author qianye
 * @create 2025-04-24 16:43
 */
public class WebSocketOption implements IProtocolOption<ServerBootstrap, NettyServer> {

    /**
     * 设置服务端 Bootstrap 的参数
     *
     * @param bootstrap 服务端 Bootstrap 对象
     */
    @Override
    public void setOption(ServerBootstrap bootstrap) {
        /*
         * 设置TCP参数，连接请求的最大队列长度
         * 作用：设置 TCP 连接请求的最大队列长度
         * 详细说明：
         *      当服务器正在处理现有连接时，新的连接请求会被放入一个等待队列中
         *      SO_BACKLOG 参数定义了该队列的最大长度这里设置为 1024，表示最多可以有 1024 个连接请求在队列中等待处理
         *      如果队列已满，新的连接请求会被拒绝
         * 适用场景：
         *      在高并发场景下，适当增大 SO_BACKLOG 可以减少连接被拒绝的情况
         *      但设置过大可能会导致资源浪费，因此需要根据服务器的实际负载进行调整
         */
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        /*
         * 使用池化的ByteBuf分配器
         * PooledByteBufAllocator 是 Netty 提供的一种内存分配器，它会预先分配一块内存池，并在需要时从池中分配 ByteBuf，使用完毕后将 ByteBuf 归还到池中
         * 这种方式避免了频繁的内存分配和释放，减少了垃圾回收的压力，从而提高了性能
         */
        bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        RcsLog.consoleLog.info("服务端使用 [" + ProtocolEnum.WEBSOCKET_SERVER + "] 协议设置参数");
    }

    /**
     * 设置服务端连接所需的 ChannelHandler
     *
     * @param bootstrap 服务端 Bootstrap 对象
     */
    @Override
    public void setChildOption(ServerBootstrap bootstrap) {
        /*
         * 作用：启用心跳机制，保持长连接
         * 详细说明：
         *      TCP 协议本身提供了 SO_KEEPALIVE 选项，用于检测连接是否仍然有效
         *      当启用 SO_KEEPALIVE 后，如果连接在一段时间内没有数据交换，TCP 会自动发送心跳包来检测对方是否仍然在线
         *      如果对方没有响应，连接会被关闭
         * 适用场景：
         *      适用于需要保持长连接的场景，例如 WebSocket 服务器或实时通信服务
         *      注意：TCP 的心跳机制是操作系统级别的，通常时间间隔较长（默认 2 小时），因此在实际应用中，通常会结合应用层的心跳机制（如 WebSocket 的 Ping/Pong）来更精确地管理连接
         */
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        /*
         * 作用：禁用 Nagle 算法，减少小数据包的延迟
         * 详细说明：
         *      TCP 协议默认启用了 Nagle 算法，该算法会将多个小数据包合并成一个较大的数据包发送，以减少网络拥塞
         *      然而，Nagle 算法会增加小数据包的延迟，尤其是在实时性要求较高的场景中（如实时通信、游戏等）
         *      设置 TCP_NODELAY 为 true 会禁用 Nagle 算法，使得小数据包可以立即发送，从而降低延迟
         * 适用场景：
         *      适用于对实时性要求较高的场景，例如 WebSocket 服务器、实时聊天、在线游戏等
         *      如果对延迟不敏感，可以保持 Nagle 算法启用以减少网络负载
         */
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        // 使用池化的ByteBuf分配器
        bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        RcsLog.consoleLog.info("服务端使用[" + ProtocolEnum.WEBSOCKET_SERVER + "]协议设置子参数");
    }

    /**
     * 创建服务端连接所需的 ChannelHandler 列表
     *
     * @return ChannelHandler集合
     */
    @Override
    public void createHandlers(ChannelPipeline pipeline, NettyServer server) {
        // HTTP编解码器
        pipeline.addLast(new HttpServerCodec());
        // 添加字符串解码器和编码器，指定使用 UTF-8 编码
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        // 将多个HTTP消息组合成一个FullHttpRequest或FullHttpResponse
        pipeline.addLast(new HttpObjectAggregator(65536));
        // 支持异步写入大数据流
        pipeline.addLast(new ChunkedWriteHandler());
        // WebSocket压缩处理器
        pipeline.addLast(new WebSocketServerCompressionHandler());
        // 空闲状态检测（1小时无读写则触发）
        pipeline.addLast(new IdleStateHandler(1, 1, 1, TimeUnit.HOURS));
        // 添加自定义空闲事件处理器
        pipeline.addLast(new IdleEventHandler());

        // 初步请求处理器，进行 URI 判断
        pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
                String uri = req.uri();
                if (uri == null || uri.isEmpty()) {
                    ctx.close();
                    return;
                }
                // 从 URI 中提取服务端ID
                String serverId = uri.substring(uri.lastIndexOf('/') + 1);
                if (serverId.isEmpty()) {
                    ctx.close();
                    return;
                }

                //截取路径
                String path = uri.substring(0, uri.lastIndexOf('/'));
                //获取协议
                ProtocolEnum protocol = server.getAttribute().getProtocol();
                //判断路由是否匹配
                if (ServerAttribute.ROUTES.containsKey(protocol.getProtocol() + path)) {
                    // 将服务端ID存储到 Channel 的 AttributeKey 中
                    ctx.channel().attr(AttributeKeyEnum.SERVER_ID.key()).set(serverId);
                    ctx.channel().attr(AttributeKeyEnum.PATH.key()).set(path);
                    ctx.pipeline().addLast(new WebSocketServerProtocolHandler(uri, null, true, 65536));
                    ctx.pipeline().addLast(server);
                } else {
                    ctx.close();
                    RcsLog.consoleLog.error("WebSocket 服务器未找到匹配路径的处理器4: " + protocol.getProtocol() + path);
                    return;
                }

                //在确认路由和 WebSocket 协议处理器已经添加到管道后，将当前的请求处理器移除。这样当前处理器就不会对后续请求做出处理
                ctx.pipeline().remove(this);
                //将请求传递给下一个处理器。在这里，retain() 保证请求对象不会被垃圾回收，直到它完成所有的处理
                ctx.fireChannelRead(req.retain());
            }
        });
    }

    /**
     * 包装原始消息
     *
     * @param rawMessage 原始消息
     * @return 包装后的消息
     */
    @Override
    public Object wrapMessage(Object rawMessage) {
        // 根据WebSocket协议要求，将原始消息包装为WebSocketFrame
        if (rawMessage instanceof String) {
            // 文本消息使用TextWebSocketFrame包装
            return new TextWebSocketFrame((String) rawMessage);
        } else if (rawMessage instanceof JSONObject) {
            // 文本消息使用TextWebSocketFrame包装
            return new TextWebSocketFrame(((JSONObject) rawMessage).toString());
        } else if (rawMessage instanceof byte[]) {
            // 二进制消息使用BinaryWebSocketFrame包装
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer((byte[]) rawMessage));
        }

        // 处理其他类型或抛出异常
        throw new IllegalArgumentException("WebSocket协议不支持的消息类型: " +
                (rawMessage != null ? rawMessage.getClass().getSimpleName() : "null"));
    }
}
