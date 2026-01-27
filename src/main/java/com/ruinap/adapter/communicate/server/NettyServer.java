package com.ruinap.adapter.communicate.server;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.IBaseServer;
import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.base.TypedFuture;
import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.adapter.communicate.server.registry.ServerHandlerRegistry;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.enums.netty.ServerRouteEnum;
import com.ruinap.infra.framework.core.event.netty.NettyConnectionEvent;
import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.Getter;

import java.util.Map;
import java.util.Set;
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

    private final AlarmManager alarmManager;
    private final VthreadPool vthreadPool;
    @Getter
    private final ServerHandlerRegistry handlerRegistry;
    /**
     * 服务端属性
     */
    @Getter
    private final ServerAttribute attribute;

    // ================== 实例级状态 (每个端口独享) ==================
    /**
     * 存储服务端路径连接
     */
    @Getter
    private final Map<String, String> paths = new ConcurrentHashMap<>();
    /**
     * 存储服务端连接上下文
     */
    @Getter
    private final Map<String, ChannelHandlerContext> contexts = new ConcurrentHashMap<>();
    /**
     * 存储服务端通道ID
     */
    @Getter
    private final Map<String, String> channelIds = new ConcurrentHashMap<>();
    /**
     * 全局 Map，用于存储服务端 ID 和对应的 CompletableFuture
     */
    private final Map<String, TypedFuture<?>> futures = new ConcurrentHashMap<>();
    /**
     * 记录每个连接正在等待的 RequestId 集合
     */
    private final Map<String, Set<String>> channelPendingRequests = new ConcurrentHashMap<>();
    /**
     * 存储服务端请求ID
     */
    @Getter
    private final Map<String, AtomicLong> requestIds = new ConcurrentHashMap<>();

    /**
     * NettyServer实例
     */
    public static final AttributeKey<NettyServer> SERVER_REF_KEY = AttributeKey.valueOf("NETTY_SERVER_REF");

    /**
     * 服务端构造函数
     *
     * @param attribute       属性
     * @param handlerRegistry 处理器注册表
     * @param alarmManager    告警管理器
     * @param vthreadPool     虚拟线程池
     */
    public NettyServer(ServerAttribute attribute, ServerHandlerRegistry handlerRegistry, AlarmManager alarmManager, VthreadPool vthreadPool) {
        this.attribute = attribute;
        this.handlerRegistry = handlerRegistry;
        this.alarmManager = alarmManager;
        this.vthreadPool = vthreadPool;
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
        this.futures.put(key, new TypedFuture<>(future, type));
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
        TypedFuture<?> entry = this.futures.remove(key);
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
                    LinkEquipmentTypeEnum equipmentType = attribute.getEquipmentType();
                    if (equipmentType != null) {
                        //在初始化时绑定设备类型到 Channel 属性，方便后续获取
                        ch.attr(AttributeKeyEnum.EQUIPMENT_TYPE.key()).set(equipmentType.getEquipmentType());
                    }
                    // 设置协议属性
                    ch.attr(AttributeKeyEnum.PROTOCOL.key()).set(attribute.getProtocol().getProtocol());

                    //将当前 NettyServer 实例绑定到 Channel 属性中
                    ch.attr(SERVER_REF_KEY).set(NettyServer.this);

                    ChannelPipeline pipeline = ch.pipeline();
                    // 调用 Protocol 接口的方法获取 Handler 列表，并添加到 Pipeline 中
                    attribute.getProtocolOption().createHandlers(pipeline, NettyServer.this);
                }
            });

            // 绑定端口并启动服务器
            ChannelFuture future = b.bind(attribute.getPort()).sync();
            RcsLog.consoleLog.info(attribute.getProtocol() + " 服务启动成功，端口: " + attribute.getPort());

            // 等待服务器通道关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            // 1.捕获中断异常，记录为 INFO 日志，而不是抛出 RuntimeException
            RcsLog.consoleLog.info("{} 服务收到停止信号，正在关闭...", attribute.getProtocol());
            // 2. 恢复中断状态
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 3. 其他真正的异常才抛出
            RcsLog.consoleLog.error("服务端运行异常", e);
            throw new RuntimeException(e);
        } finally {
            // 关闭线程组
            attribute.getBossGroup().shutdownGracefully();
            RcsLog.consoleLog.info("{} 服务已停止，资源释放完毕", attribute.getProtocol());
        }
        return null;
    }

    /**
     * 关闭服务端
     */
    @Override
    public void shutdown() {
        for (ChannelHandlerContext server : this.contexts.values()) {
            server.close();
        }
        attribute.getBossGroup().shutdownGracefully();
    }

    // ================== 业务逻辑 (操作实例变量) ==================
    @Override
    public void sendMessage(String serverId, Object message) {
        // 获取客户端的连接上下文
        ChannelHandlerContext ctx = this.contexts.get(serverId);
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
    public <T> CompletableFuture<T> sendMessage(String serverId, Long requestId, Object message, Class<T> responseType) {
        CompletableFuture<T> future = new CompletableFuture<>();

        ChannelHandlerContext ctx = this.contexts.get(serverId);
        if (ctx == null || !ctx.channel().isActive()) {
            RcsLog.communicateOtherLog.error("{} 服务端未连接或通道未激活", serverId);
            future.completeExceptionally(new IllegalStateException("[" + serverId + "] 服务端未连接或通道未激活"));
            return future;
        }

        String key = StrUtil.format("{}_{}", serverId, requestId);
        // 获取 Channel 的短 ID (用于倒排索引 Key)
        String channelId = ctx.channel().id().asShortText();
        try {
            // 存储带类型信息的Future
            putFuture(key, future, responseType);
            //存入倒排索引 (建立 Channel -> RequestKey 的关联)
            channelPendingRequests.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(key);

            EventLoop eventLoop = ctx.channel().eventLoop();

            // 可配置超时时间，例如从属性获取
            long timeoutSeconds = 3;
            ScheduledFuture<?> timeoutScheduledFuture = eventLoop.schedule(() -> {
                if (future.completeExceptionally(new TimeoutException("等待服务端响应超时"))) {
                    // 清理资源
                    this.futures.remove(key);
                    // 同步移除倒排索引
                    removePendingKey(channelId, key);
                    RcsLog.communicateOtherLog.error("{} {}请求超时", serverId, requestId);
                }
            }, timeoutSeconds, TimeUnit.SECONDS);

            future.whenComplete((res, ex) -> {
                timeoutScheduledFuture.cancel(false);
                this.futures.remove(key);
                // 同步移除倒排索引
                removePendingKey(channelId, key);
            });

            ctx.writeAndFlush(attribute.getProtocolOption().wrapMessage(message)).addListener(f -> {
                if (!f.isSuccess() && !future.isDone()) {
                    future.completeExceptionally(f.cause());
                }
            });

        } catch (Exception e) {
            this.futures.remove(key);
            // 同步移除倒排索引
            removePendingKey(channelId, key);
            String errorMsg = String.format("消息发送异常: %s", e.getMessage());
            RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), serverId, errorMsg);
            future.completeExceptionally(new RuntimeException(errorMsg, e));
        }
        return future;
    }

    /**
     * 发送消息并等待响应 (直接使用 Channel，绕过 ID 查找，更安全)
     * * @param channel      连接通道
     *
     * @param requestId    请求ID
     * @param message      消息内容
     * @param responseType 响应类型
     * @return Future
     */
    public <T> CompletableFuture<T> sendMessage(Channel channel, Long requestId, T message, Class<T> responseType) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // 1. 基础校验：先判空，再判活
        if (channel == null || !channel.isActive()) {
            String errorMsg = "连接失效或通道未激活";

            // 【修复点】只有 channel 不为 null 时才能去取 ID
            String tempId = "UNKNOWN_CHANNEL";
            if (channel != null) {
                tempId = channel.id().asShortText();
                // 尝试获取业务 ID
                if (channel.hasAttr(AttributeKeyEnum.CLIENT_ID.key())) {
                    String attrId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
                    if (attrId != null) {
                        tempId = attrId;
                    }
                }
            }

            RcsLog.communicateOtherLog.error("{} {}", tempId, errorMsg);
            future.completeExceptionally(new IllegalStateException(errorMsg));
            return future;
        }

        // 2. 生成 Key 和 ChannelId
        // 注意：这里使用 Channel 的短 ID 作为倒排索引的 Key，这与 handlerRemoved 中的清理逻辑必须保持一致
        String channelId = channel.id().asShortText();
        // 这里的 serverId 仅用于日志，尽量获取真实的业务 ID
        String serverId = channel.hasAttr(AttributeKeyEnum.CLIENT_ID.key()) ?
                channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get() : channelId;

        String requestKey = StrUtil.format("{}_{}", serverId, requestId);

        try {
            // 3. 注册 Future
            putFuture(requestKey, future, responseType);
            // 4. 注册倒排索引 (用于断线快速清理)
            channelPendingRequests.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(requestKey);

            EventLoop eventLoop = channel.eventLoop();

            // 5. 超时控制
            long timeoutSeconds = 3L;
            ScheduledFuture<?> timeoutScheduledFuture = eventLoop.schedule(() -> {
                if (future.completeExceptionally(new TimeoutException("等待服务端响应超时"))) {
                    this.futures.remove(requestKey);
                    removePendingKey(channelId, requestKey);
                    RcsLog.communicateOtherLog.error("{} {}请求超时", serverId, requestId);
                }
            }, timeoutSeconds, TimeUnit.SECONDS);

            // 6. 清理回调
            future.whenComplete((res, ex) -> {
                timeoutScheduledFuture.cancel(false);
                this.futures.remove(requestKey);
                removePendingKey(channelId, requestKey);
            });

            // 7. 执行发送
            // 注意：Server 端通常由 Handler 处理编码，或者 pipeline 里有编码器。
            // 这里直接 writeAndFlush 包装后的消息
            channel.writeAndFlush(attribute.getProtocolOption().wrapMessage(message)).addListener(f -> {
                if (!f.isSuccess() && !future.isDone()) {
                    future.completeExceptionally(f.cause());
                }
            });

        } catch (Exception e) {
            this.futures.remove(requestKey);
            removePendingKey(channelId, requestKey);
            String errorMsg = String.format("消息发送异常: %s", e.getMessage());
            RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), serverId, errorMsg);
            future.completeExceptionally(new RuntimeException(errorMsg, e));
        }
        return future;
    }

    /**
     * 辅助方法：安全移除倒排索引中的 Key
     */
    private void removePendingKey(String channelId, String key) {
        Set<String> keys = channelPendingRequests.get(channelId);
        if (keys != null) {
            keys.remove(key);
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
        if (path == null) {
            path = "";
        }
        //获取事件处理器
        IServerHandler serverEventHandler = handlerRegistry.getHandler(attribute.getProtocol(), path);
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
                this.contexts.putIfAbsent(serverId, ctx);
                this.paths.putIfAbsent(serverId, path);
                this.channelIds.putIfAbsent(id, serverId);
                this.requestIds.putIfAbsent(serverId, new AtomicLong(0));
                //记录日志
                RcsLog.consoleLog.info("{} 服务端连接成功", serverId);

                //发布上线事件
                publishEvent(channel, serverId, NettyConnectionEvent.State.CONNECTED);
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
        IServerHandler serverEventHandler = handlerRegistry.getHandler(attribute.getProtocol(), path);
        if (serverEventHandler == null) {
            ctx.close();
            RcsLog.consoleLog.error("Netty 服务器未找到匹配路径的处理器: " + this.attribute.getProtocol().getProtocol() + path);
            return;
        }

        //  引用计数保留
        // 因为处理逻辑切换到了另一个线程，必须手动增加引用计数，防止 Netty 在本方法返回后自动释放
        if (frame instanceof ReferenceCounted) {
            ((ReferenceCounted) frame).retain();
        }

        // 将业务逻辑提交给虚拟线程池，释放 IO 线程
        //  调用事件处理器的 channelRead0 方法
        vthreadPool.execute(() -> {
            try {
                serverEventHandler.channelRead0(ctx, frame, attribute);
            } catch (Throwable e) {
                // 捕获 Throwable，防止 Error 导致线程静默死亡
                RcsLog.communicateLog.error("服务端业务处理严重异常", e);
                exceptionCaught(ctx, e);
            } finally {
                // 【关键修复 3】: 业务处理完毕（或发生异常）后，手动释放引用
                if (frame instanceof ReferenceCounted) {
                    ReferenceCountUtil.safeRelease(frame);
                }
            }
        });
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
        String fullId = channel.id().toString();
        String shortId = channel.id().asShortText();

        // 从 Channel 的 AttributeKey 中获取属性
        String path = "";
        if (channel.hasAttr(AttributeKeyEnum.PATH.key())) {
            path = channel.attr(AttributeKeyEnum.PATH.key()).get();
        }
        String serverId = "UNKNOWN";
        if (channel.hasAttr(AttributeKeyEnum.SERVER_ID.key())) {
            serverId = channel.attr(AttributeKeyEnum.SERVER_ID.key()).get();
        }

        // ====================================================================
        // O(1) 快速清理该连接挂起的同步请求
        // ====================================================================
        Set<String> pendingKeys = channelPendingRequests.remove(shortId);
        if (pendingKeys != null && !pendingKeys.isEmpty()) {
            Exception disconnectEx = new IllegalStateException("连接已断开，请求强制终止: " + serverId);
            int count = 0;
            for (String key : pendingKeys) {
                // 直接通过 Key 移除 Future，无需遍历整个 Map
                TypedFuture<?> typedFuture = this.futures.remove(key);
                if (typedFuture != null && typedFuture.getFuture() != null && !typedFuture.getFuture().isDone()) {
                    typedFuture.getFuture().completeExceptionally(disconnectEx);
                    count++;
                }
            }
            if (count > 0) {
                RcsLog.consoleLog.warn("[{}] 断线清理: 已强制终止 {} 个挂起的同步请求", serverId, count);
            }
        }

        // ====================================================================
        // 资源清理逻辑
        // ====================================================================
        // 检查是否是已登录的连接
        if (this.channelIds.containsKey(fullId)) {
            this.channelIds.remove(fullId);
            this.contexts.remove(serverId);
            if (serverId != null) {
                this.paths.remove(serverId);
                this.requestIds.remove(serverId);
            }

            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("{} 关闭服务端连接并清理资源", serverId));

            //发布下线事件
            publishEvent(channel, serverId, NettyConnectionEvent.State.DISCONNECTED);
        } else {
            RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), StrUtil.format("{} 关闭服务端连接(未完全注册状态)", serverId));
        }

        if (path != null && !path.contains(ServerRouteEnum.VISUAL.getRoute())) {
            //触发告警
            alarmManager.triggerAlarm(serverId, AlarmCodeEnum.E12004, "rcs");
        }

        //获取事件处理器并联动
        if (path != null) {
            IServerHandler serverEventHandler = handlerRegistry.getHandler(attribute.getProtocol(), path);
            if (serverEventHandler != null) {
                // 调用事件处理器的 handlerRemoved 方法
                serverEventHandler.handlerRemoved(ctx, attribute);
            }
        }

        //Netty是链式处理的，将事件传递到下一个 ChannelHandler
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
        RcsLog.consoleLog.error("{} server_error {}", serverId, cause.getMessage());

        //获取事件处理器
        IServerHandler serverEventHandler = handlerRegistry.getHandler(attribute.getProtocol(), path);
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

    // ================== 私有辅助方法：发布事件 ==================

    /**
     * 发布连接事件到 NettyManager
     */
    private void publishEvent(Channel channel, String serverId, NettyConnectionEvent.State state) {
        // 尝试获取设备类型
        // 1. 优先从 Channel 属性获取 (start方法里设置的)
        LinkEquipmentTypeEnum type = null;
        String typeStr = channel.attr(AttributeKeyEnum.EQUIPMENT_TYPE.key()).get();
        if (typeStr != null) {
            type = LinkEquipmentTypeEnum.fromEquipmentType(typeStr);
        }

        // 2. 如果 Channel 里没存，尝试从 ServerAttribute 获取 (端口级别的配置)
        if (type == null && attribute.getEquipmentType() != null) {
            type = attribute.getEquipmentType();
        }

        // 3. 如果还是没有，根据协议进行兜底推断
        if (type == null) {
            // VDA5050 MQTT 协议默认归类为 AGV
            if (attribute.getProtocol() == ProtocolEnum.MQTT_SERVER) {
                type = LinkEquipmentTypeEnum.AGV;
            }
        }

        if (type != null && serverId != null) {
            NettyConnectionEvent event = new NettyConnectionEvent(
                    this,
                    NettyConnectionEvent.ConnectSource.SERVER,
                    type,
                    serverId,
                    channel,
                    state
            );
            SpringContextHolder.publishEvent(event);
        } else {
            RcsLog.consoleLog.warn("无法发布连接事件，缺少身份信息: ID={}, Type={}", serverId, type);
        }
    }
}
