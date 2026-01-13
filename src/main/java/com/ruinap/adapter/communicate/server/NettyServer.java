package com.ruinap.adapter.communicate.server;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.slamopto.common.enums.ProtocolEnum;
import com.slamopto.communicate.base.IBaseServer;
import com.slamopto.communicate.base.ServerAttribute;
import com.slamopto.communicate.base.TypedFuture;
import com.slamopto.communicate.base.enums.AttributeKeyEnum;
import com.slamopto.communicate.base.enums.ServerRouteEnum;
import com.slamopto.communicate.server.handler.IServerHandler;
import com.slamopto.db.business.AlarmManage;
import com.slamopto.db.enums.AlarmCodeEnum;
import com.slamopto.log.RcsLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty服务端实现
 * - 服务端共用一个 WorkerGroup
 * - 每个服务端和客户端拥有自己的 BossGroup
 * - 封装事件处理和消息分发，通过高级 API 暴露
 *
 * @author qianye
 * @create 2025-04-24 14:48
 */
@ChannelHandler.Sharable
public class NettyServer extends SimpleChannelInboundHandler<Object> implements IBaseServer {

    /**
     * 存储服务端对象
     */
    private static final Map<String, NettyServer> SERVERS = new ConcurrentHashMap<>();

    /**
     * 存储服务端路径连接
     */
    @Getter
    private static final Map<String, String> PATHS = new ConcurrentHashMap<>();

    /**
     * 存储服务端连接上下文
     */
    @Getter
    private static final Map<String, ChannelHandlerContext> CONTEXTS = new ConcurrentHashMap<>();

    /**
     * 存储服务端通道ID
     */
    @Getter
    private static final Map<String, String> CHANNEL_IDS = new ConcurrentHashMap<>();

    /**
     * 全局 Map，用于存储服务端 ID 和对应的 CompletableFuture
     */
    private static final Map<String, TypedFuture<?>> FUTURES = new ConcurrentHashMap<>();

    /**
     * 存储服务端请求ID
     */
    @Getter
    private static final Map<String, AtomicLong> REQUESTIDS = new ConcurrentHashMap<>();

    /**
     * 服务端属性
     */
    @Getter
    private final ServerAttribute attribute;

    /**
     * 服务端构造函数
     *
     * @param attribute 属性
     */
    public NettyServer(ServerAttribute attribute) {
        this.attribute = attribute;
    }

    /**
     * 向集合中添加一个带有类型的CompletableFuture对象
     * 此方法允许在异步操作中存储有关未来的值及其预期类型的详细信息
     *
     * @param key    用于标识CompletableFuture的键
     * @param future 代表一个异步操作的CompletableFuture对象
     * @param type   期望从CompletableFuture中获取的值的类型
     */
    public <T> void putFuture(String key, CompletableFuture<T> future, Class<T> type) {
        FUTURES.put(key, new TypedFuture<>(future, type));
    }

    /**
     * 根据键移除并返回对应的 CompletableFuture 对象
     * 此方法用于在给定键匹配时从 futures 中移除并返回对应的 CompletableFuture 对象
     * 如果键不存在或类型不匹配，则返回 null
     *
     * @param key          键，用于标识要移除的 CompletableFuture 对象
     * @param expectedType 期望的类型，用于验证 entry 的类型
     * @param <T>          泛型参数，表示 CompletableFuture 的类型
     * @return 如果找到匹配的键和类型，则返回对应的 CompletableFuture<T> 对象；否则返回 null
     */
    public <T> CompletableFuture<T> removeFuture(String key, Class<T> expectedType) {
        // 从 futures 中移除键对应的 TypedFuture 对象
        TypedFuture<?> entry = FUTURES.remove(key);
        // 检查移除的 entry 是否不为空且类型与 expectedType 匹配
        if (entry != null && expectedType.equals(entry.type)) {
            // 类型转换，由于 entry 类型已验证，因此这里抑制警告
            @SuppressWarnings("unchecked")
            CompletableFuture<T> future = (CompletableFuture<T>) entry.future;
            // 返回转换后的 CompletableFuture 对象
            return future;
        }
        // 如果 entry 为空或类型不匹配，返回 null
        return null;
    }

    /**
     * 启动服务端
     */
    @Override
    public CompletableFuture<Void> start() {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(attribute.getBossGroup(), attribute.getWorkerGroup())
                    // 使用NIO传输
                    .channel(NioServerSocketChannel.class);
            //  调用 Protocol 接口的方法设置参数
            attribute.getProtocolOption().setOption(b);
            //  调用 Protocol 接口的方法设置子参数
            attribute.getProtocolOption().setChildOption(b);
            // 添加日志处理器，如果你想知道Netty内部的处理过程，可以取消注释
//            b.handler(new LoggingHandler(LogLevel.INFO));
            b.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    // 调用 Protocol 接口的方法获取 Handler 列表，并添加到 Pipeline 中
                    attribute.getProtocolOption().createHandlers(pipeline, NettyServer.this);
                }
            });

            // 绑定端口并启动服务器
            ChannelFuture future = b.bind(attribute.getPort()).sync();
            //获取协议字符串
            String protocolStr = attribute.getProtocol().getProtocol();
            SERVERS.put(protocolStr, this);
            RcsLog.consoleLog.info(attribute.getProtocol() + " 服务启动成功，端口: " + attribute.getPort());

            // 等待服务器通道关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 关闭线程组
            attribute.getBossGroup().shutdownGracefully();
        }
        return null;
    }

    /**
     * 关闭服务端
     */
    @Override
    public void shutdown() {
        for (ChannelHandlerContext server : CONTEXTS.values()) {
            server.close();
        }
        attribute.getBossGroup().shutdownGracefully();
    }

    /**
     * 获取所有服务端
     *
     * @return 所有服务端
     */
    public static List<NettyServer> getServer() {
        return SERVERS.values().stream().toList();
    }

    /**
     * 获取服务端
     *
     * @param protocolEnum 协议枚举
     * @return 服务端
     */
    public static NettyServer getServer(ProtocolEnum protocolEnum) {
        return SERVERS.get(protocolEnum.getProtocol());
    }

    @Override
    public void sendMessage(String serverId, Object message) {
        // 获取客户端的连接上下文
        ChannelHandlerContext ctx = CONTEXTS.get(serverId);
        if (ctx != null) {
            // 发送消息给客户端
            ctx.writeAndFlush(attribute.getProtocolOption().wrapMessage(message));
        }
    }

    /**
     * 发送消息并等待响应
     *
     * @param requestId 请求ID
     * @param message   消息
     * @param <T>       消息类型
     * @return 响应结果
     */
    @Override
    public <T> CompletableFuture<T> sendMessage(String serverId, Long requestId, T message, Class<T> responseType) {
        CompletableFuture<T> future = new CompletableFuture<>();

        ChannelHandlerContext ctx = CONTEXTS.get(serverId);
        if (ctx == null || !ctx.channel().isActive()) {
            RcsLog.communicateOtherLog.error(RcsLog.formatTemplateRandom(serverId, " 服务端未连接或通道未激活"));
            future.completeExceptionally(new IllegalStateException("[" + serverId + "] 服务端未连接或通道未激活"));
            return future;
        }

        String key = StrUtil.format("{}_{}", serverId, requestId);
        try {
            // 存储带类型信息的Future
            putFuture(key, future, responseType);
            EventLoop eventLoop = ctx.channel().eventLoop();

            // 可配置超时时间，例如从属性获取
            long timeoutSeconds = 3;
            ScheduledFuture<?> timeoutScheduledFuture = eventLoop.schedule(() -> {
                if (future.completeExceptionally(new TimeoutException("等待服务端响应超时"))) {
                    FUTURES.remove(key);
                    RcsLog.communicateOtherLog.error(RcsLog.formatTemplateRandom(serverId, requestId + "请求超时"));
                }
            }, timeoutSeconds, TimeUnit.SECONDS);

            future.whenComplete((res, ex) -> {
                timeoutScheduledFuture.cancel(false);
                FUTURES.remove(key);
            });

            ctx.writeAndFlush(attribute.getProtocolOption().wrapMessage(message)).addListener(f -> {
                if (!f.isSuccess() && !future.isDone()) {
                    future.completeExceptionally(f.cause());
                }
            });

        } catch (Exception e) {
            FUTURES.remove(key);
            String errorMsg = String.format("消息发送异常: %s", e.getMessage());
            RcsLog.communicateOtherLog.error(RcsLog.formatTemplateRandom(serverId, errorMsg));
            future.completeExceptionally(new RuntimeException(errorMsg, e));
        }
        return future;
    }

    /**
     * 发送消息给所有指定路由的服务端
     *
     * @param routeEnum 路由枚举
     * @param message   消息
     */
    public static void sendMessageAll(ProtocolEnum protocolEnum, ServerRouteEnum routeEnum, Object message) {
        for (Map.Entry<String, String> entry : PATHS.entrySet()) {
            String serverId = entry.getKey();
            String route = entry.getValue();
            if (route.equalsIgnoreCase(routeEnum.getRoute())) {
                // 获取客户端的连接上下文
                ChannelHandlerContext ctx = CONTEXTS.get(serverId);
                if (ctx != null) {
                    NettyServer server = NettyServer.getServer(protocolEnum);
                    // 发送消息给客户端
                    ctx.writeAndFlush(server.getAttribute().getProtocolOption().wrapMessage(message));
                }
            }
        }
    }

    //********************************************************* Netty生命周期开始 *********************************************************
    //********************************************************* Netty生命周期开始 *********************************************************

    /**
     * 触发时机：当用户自定义事件触发时调用。
     * 用途：处理Netty内置事件、用户自定义事件等。
     *
     * @param ctx 上下文
     * @param evt 事件对象
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 从上下文中获取Channel
        Channel channel = ctx.channel();
        // 从 Channel 的 AttributeKey 中获取服务端路径
        String path = channel.attr(AttributeKeyEnum.PATH.key()).get();
        //获取事件处理器
        IServerHandler serverEventHandler = ServerAttribute.ROUTES.get(this.attribute.getProtocol().getProtocol() + path);
        if (serverEventHandler == null) {
            ctx.close();
            RcsLog.consoleLog.error("Netty 服务器未找到匹配路径的处理器: " + this.attribute.getProtocol().getProtocol() + path);
            return;
        }
        // 调用事件处理器的 userEventTriggered 方法
        JSONObject entries = serverEventHandler.userEventTriggered(ctx, evt, attribute);
        if (entries != null) {
            //获取握手状态
            Boolean handshakeComplete = entries.getBool("handshake_complete", false);
            if (handshakeComplete) {
                // 获取服务端通道ID
                String id = channel.id().toString();
                String serverId = channel.attr(AttributeKeyEnum.SERVER_ID.key()).get();
                //添加到服务端列表
                CONTEXTS.putIfAbsent(serverId, ctx);
                PATHS.putIfAbsent(serverId, path);
                CHANNEL_IDS.putIfAbsent(id, serverId);
                REQUESTIDS.putIfAbsent(serverId, new AtomicLong(0));
                //记录日志
                RcsLog.consoleLog.info(RcsLog.formatTemplateRandom(serverId, "服务端连接成功"));
            }
        }

        // 将事件传递给下一个处理器，确保其他处理器也能接收到这个用户事件
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 触发时机：当从 Channel 读取到数据时调用（核心方法）
     * 用途：处理特定类型的消息（泛型 I 指定消息类型）
     * 特点：自动释放消息对象（通过 ReferenceCountUtil.release(msg)）
     *
     * @param ctx   上下文
     * @param frame 数据帧
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object frame) {
        // 从上下文中获取Channel
        Channel channel = ctx.channel();
        // 从 Channel 的 AttributeKey 中获取服务端路径
        String path = channel.attr(AttributeKeyEnum.PATH.key()).get();
        if (path == null) {
            path = "";
        }
        //获取事件处理器
        IServerHandler serverEventHandler = ServerAttribute.ROUTES.get(this.attribute.getProtocol().getProtocol() + path);
        if (serverEventHandler == null) {
            ctx.close();
            RcsLog.consoleLog.error("Netty 服务器未找到匹配路径的处理器: " + this.attribute.getProtocol().getProtocol() + path);
            return;
        }
        //  调用事件处理器的 channelRead0 方法
        serverEventHandler.channelRead0(ctx, frame, attribute);
    }

    /**
     * 触发时机：当 Handler 从 ChannelPipeline 中移除时调用。
     * 用途：清理 Handler 级别的资源。
     * <p>
     * 当关闭连接时，第三个调用handlerRemoved
     *
     * @param ctx 上下文
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // 从上下文中获取Channel
        Channel channel = ctx.channel();
        // 从 Channel 的 AttributeKey 中获取服务端路径
        String path = channel.attr(AttributeKeyEnum.PATH.key()).get();

        // 从 Channel 的 AttributeKey 中获取服务端ID
        String serverId = channel.attr(AttributeKeyEnum.SERVER_ID.key()).get();
        // 从 Channel 中获取服务端ID
        String id = channel.id().toString();
        if (CHANNEL_IDS.containsKey(id)) {
            CHANNEL_IDS.remove(id);
            CONTEXTS.remove(serverId);
            PATHS.remove(serverId);
            REQUESTIDS.remove(serverId);
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(serverId, "关闭服务端连接并清理资源"));
        } else {
            RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(serverId, "关闭服务端连接"));
        }

        if (!path.contains(ServerRouteEnum.VISUAL.getRoute())) {
            //触发告警
            AlarmManage.triggerAlarm(serverId, AlarmCodeEnum.E12004, "rcs");
        }

        //获取事件处理器
        IServerHandler serverEventHandler = ServerAttribute.ROUTES.get(this.attribute.getProtocol().getProtocol() + path);
        if (serverEventHandler == null) {
            ctx.close();
            RcsLog.consoleLog.error("Netty 服务器未找到匹配路径的处理器: " + this.attribute.getProtocol().getProtocol() + path);
            return;
        }
        // 调用事件处理器的 handlerRemoved 方法
        serverEventHandler.handlerRemoved(ctx, attribute);

        //Netty是链式处理的，将事件传递到下一个 ChannelHandler，如果不调用则事件会终止在当前ChannelHandler
        super.handlerRemoved(ctx);
    }

    /**
     * 触发时机：当处理过程中发生异常时调用
     * 用途：处理异常（如 IO 错误、解码失败等），需记录日志或关闭连接
     * 注意：若不重写此方法，默认行为是关闭 Channel
     *
     * @param ctx   上下文
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

        // 从上下文中获取Channel
        Channel channel = ctx.channel();
        // 从 Channel 的 AttributeKey 中获取服务端路径
        String path = channel.attr(AttributeKeyEnum.PATH.key()).get();
        // 从 Channel 的 AttributeKey 中获取服务端ID
        String serverId = channel.attr(AttributeKeyEnum.SERVER_ID.key()).get();
        //记录日志
        RcsLog.consoleLog.error(RcsLog.formatTemplate(serverId, "server_error", cause.getMessage()));

        //获取事件处理器
        IServerHandler serverEventHandler = ServerAttribute.ROUTES.get(this.attribute.getProtocol().getProtocol() + path);
        if (serverEventHandler == null) {
            ctx.close();
            RcsLog.consoleLog.error("Netty 服务器未找到匹配路径的处理器: " + this.attribute.getProtocol().getProtocol() + path);
            return;
        }
        // 调用事件处理器的 exceptionCaught 方法
        serverEventHandler.exceptionCaught(ctx, cause, attribute);
    }
    //********************************************************* Netty生命周期结束 *********************************************************
    //********************************************************* Netty生命周期结束 *********************************************************
}
