package com.ruinap.adapter.communicate.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.IBaseClient;
import com.ruinap.adapter.communicate.base.TypedFuture;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.infra.async.OrderedTaskDispatcher;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.core.event.netty.NettyConnectionEvent;
import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty客户端实现
 *
 * @author qianye
 * @create 2025-05-09 15:23
 */
public class NettyClient extends SimpleChannelInboundHandler<Object> implements IBaseClient {
    private final OrderedTaskDispatcher taskDispatcher;
    private final CoreYaml coreYaml;
    private final AlarmManager alarmManager;
    private final VthreadPool vthreadPool;

    /**
     * 客户端属性
     */
    @Getter
    private final ClientAttribute attribute;
    /**
     * 存储连接失败计数器
     */
    private final AtomicInteger failedCounter = new AtomicInteger(0);

    /**
     * 构造函数
     *
     */
    public NettyClient(ClientAttribute attribute, OrderedTaskDispatcher taskDispatcher, CoreYaml coreYaml, AlarmManager alarmManager, VthreadPool vthreadPool) {
        this.attribute = attribute;
        this.coreYaml = coreYaml;
        this.alarmManager = alarmManager;
        this.taskDispatcher = taskDispatcher;
        this.vthreadPool = vthreadPool;
    }

    /**
     * 启动客户端
     */
    @Override
    public CompletableFuture<Boolean> start() {
        CompletableFuture<Boolean> startFuture = new CompletableFuture<>();
        try {
            Bootstrap b = new Bootstrap();
            //设置共享的事件循环组
            b.group(attribute.getWorkerGroup());
            b.channel(NioSocketChannel.class);
            //  调用 Protocol 接口的方法设置参数
            attribute.getProtocolOption().setOption(b);
            //  调用 Protocol 接口的方法设置子参数
            attribute.getProtocolOption().setChildOption(b);
            // 添加日志处理器
            //b.handler(new LoggingHandler(LogLevel.INFO));
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    // 在 Channel 初始化时设置属性
                    ch.attr(AttributeKeyEnum.CLIENT_ID.key()).set(attribute.getClientId());
                    ch.attr(AttributeKeyEnum.PROTOCOL.key()).set(attribute.getProtocol().getProtocol());
                    // 获取通道管道
                    ChannelPipeline pipeline = ch.pipeline();
                    // 调用 Protocol 接口的方法获取 Handler 列表，并添加到 Pipeline 中
                    attribute.getProtocolOption().createHandlers(pipeline, NettyClient.this);
                }
            });

            // 异步连接到服务器（不阻塞）
            ChannelFuture connectFuture = b.connect(attribute.getUri().getHost(), attribute.getUri().getPort());
            // 监听连接结果
            connectFuture.addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    // 标记 Future 完成（成功）
                    startFuture.complete(true);
                } else {
                    // 标记 Future 异常（失败）
                    startFuture.completeExceptionally(future.cause());
                }
            });
        } catch (Exception e) {
            // 初始化阶段异常直接标记失败
            startFuture.completeExceptionally(e);
        }

        // 返回 Future
        return startFuture;
    }

    /**
     * 关闭客户端
     */
    @Override
    public void shutdown() {
        //由于线程组是单例的，所以不需要在此释放资源
        if (attribute.getContext() != null) {
            attribute.getContext().close();
        }
    }

    /**
     * 发送消息
     *
     * @param message 消息
     * @param <T>     消息类型
     */
    @Override
    public <T> void sendMessage(T message) {
        // 获取客户端的连接上下文
        ChannelHandlerContext ctx = attribute.getContext();
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
    public <T> CompletableFuture<T> sendMessage(Long requestId, Object message, Class<T> responseType) {
        CompletableFuture<T> future = new CompletableFuture<>();
        String clientId = attribute.getClientId();
        String equipmentType = attribute.getEquipmentType().getEquipmentType();
        ChannelHandlerContext ctx = attribute.getContext();

        String key2 = StrUtil.format("{}_{}", equipmentType, clientId);
        if (ctx == null || !ctx.channel().isActive()) {
            String errorMsg = String.format("[%s] 服务端未连接或通道未激活", key2);
            //记录日志
            if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
                RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key2, errorMsg);
            } else {
                RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key2, errorMsg);
            }
            future.completeExceptionally(new IllegalStateException(errorMsg));
            return future;
        }

        String key3 = StrUtil.format("{}_{}_{}", equipmentType, clientId, requestId);
        try {
            // 存储带类型信息的Future
            attribute.putFuture(key3, future, responseType);
            EventLoop eventLoop = ctx.channel().eventLoop();

            // 设置超时处理
            long timeoutSeconds = Long.parseLong(coreYaml.getNettyFutureTimeout());
            ScheduledFuture<?> timeoutScheduledFuture = eventLoop.schedule(() -> {
                if (future.completeExceptionally(new TimeoutException("等待服务端响应超时"))) {
                    attribute.getFutures().remove(key3);
                    //记录日志
                    if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
                        RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key2, "请求超时");
                    } else {
                        RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key2, "请求超时");
                    }
                }
            }, timeoutSeconds, TimeUnit.SECONDS);

            future.whenComplete((res, ex) -> {
                timeoutScheduledFuture.cancel(false);
                attribute.getFutures().remove(key3);
            });

            // 发送消息（自动包装为协议格式）
            ctx.writeAndFlush(attribute.getProtocolOption().wrapMessage(message)).addListener(f -> {
                if (!f.isSuccess() && !future.isDone()) {
                    future.completeExceptionally(f.cause());
                }
            });

            return future;
        } catch (Exception e) {
            attribute.getFutures().remove(key3);
            String errorMsg = String.format("消息发送异常: %s", e.getMessage());
            //记录日志
            if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
                RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key2, errorMsg);
            } else {
                RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key2, errorMsg);
            }
            future.completeExceptionally(new RuntimeException(errorMsg, e));
            return future;
        }
    }

    /**
     * Netty连接事件
     *
     * @param channel 通道
     * @param state   状态
     */
    private void publishEvent(Channel channel, NettyConnectionEvent.State state) {
        NettyConnectionEvent event = new NettyConnectionEvent(
                // source
                this,
                // 【身份：客户端】
                NettyConnectionEvent.ConnectSource.CLIENT,
                // type
                attribute.getEquipmentType(),
                // id
                attribute.getClientId(),
                // channel
                channel,
                // state
                state
        );
        // 【使用静态工具发布】
        SpringContextHolder.publishEvent(event);
    }
    //********************************************************* Netty生命周期开始 *********************************************************
    //********************************************************* Netty生命周期开始 *********************************************************

    /**
     * 处理通道激活事件
     *
     * @param ctx 上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 从 Channel 的 Attribute 中获取客户端 ID
        Channel channel = ctx.channel();
        String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        //如果客户端ID不为空，则记录客户端上下文
        if (clientId != null && !clientId.isEmpty()) {
            //重置连接失败计数器
            this.failedCounter.set(0);
            //添加到客户端列表
            String key = StrUtil.format("{}_{}", attribute.getEquipmentType().getEquipmentType(), clientId);
            // 如果是纯 TCP 协议，连接建立即视为上线。
            if (attribute.getProtocol() == ProtocolEnum.TCP_CLIENT) {
                publishEvent(ctx.channel(), NettyConnectionEvent.State.CONNECTED);
            }
            // 将客户端上下文存储到全局 Map 中
            attribute.setContext(ctx);
            attribute.setRequestId(new AtomicLong(0));

            // 连接建立时触发
            RcsLog.consoleLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), key, "channelActive 已连接到 " + attribute.getProtocol().getProtocol() + " 服务器: " + ctx.channel().remoteAddress());
            RcsLog.communicateLog.info(RcsLog.getTemplate(3), RcsLog.randomInt(), key, "channelActive 已连接到 " + attribute.getProtocol().getProtocol() + " 服务器: " + ctx.channel().remoteAddress());
        }

        super.channelActive(ctx);
    }

    /**
     * 处理用户自定义事件
     * <p>
     * HANDSHAKE_COMPLETE 事件，表示握手已经完成
     *
     * @param ctx 上下文
     * @param evt 事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        //调用事件处理器方法
        JSONObject entries = attribute.getHandler().userEventTriggered(ctx, evt, attribute);
        if (entries != null) {
            Boolean handshakeComplete = entries.getBool("handshake_complete", false);
            if (handshakeComplete) {
                // 从 Channel 的 Attribute 中获取客户端 ID
                Channel channel = ctx.channel();
                String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
                //重置连接失败计数器
                int count = this.failedCounter.incrementAndGet();
                //添加到客户端列表
                String key = attribute.getEquipmentType().getEquipmentType() + "_" + clientId;
                // WebSocket 握手成功，发布上线事件
                publishEvent(ctx.channel(), NettyConnectionEvent.State.CONNECTED);
                // 将客户端上下文存储到全局 Map 中
                attribute.setContext(ctx);
                attribute.setRequestId(new AtomicLong(0));

                String equipmentType = attribute.getEquipmentType().getEquipmentType();
                //记录日志
                if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
                    RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key, "已记录 " + attribute.getProtocol().getProtocol() + " 服务器上下文");
                } else {
                    RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), key, "已记录 " + attribute.getProtocol().getProtocol() + " 服务器上下文");
                }
            }
        }

        // 将事件传递给下一个处理器，确保流水线中的其他处理器也能接收到这个用户事件
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 用于处理接收到的消息
     *
     * @param ctx   上下文
     * @param frame 数据帧
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object frame) throws Exception {
        taskDispatcher.dispatch(attribute.getClientId(), () -> {
            try {
                // 在虚拟线程中执行耗时的业务 Handler
                attribute.getHandler().channelRead0(ctx, frame, attribute);
            } catch (Exception e) {
                // 捕获业务异常，防止线程静默退出
                RcsLog.communicateLog.error("客户端业务处理异常", e);
                attribute.getHandler().exceptionCaught(ctx, e, attribute);
            }
        });
    }

    /**
     * 当连接关闭或通道失效时触发。
     * 用于资源清理、重连机制或记录连接关闭状态
     *
     * @param ctx 上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 从 Channel 的 Attribute 中获取客户端 ID
        Channel channel = ctx.channel();
        String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        String equipmentType = attribute.getEquipmentType().getEquipmentType();
        String logPrefix = equipmentType + clientId;
        // 连接关闭时触发
        RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), clientId, "与 " + attribute.getProtocol() + " 服务器断开连接: " + ctx.channel().remoteAddress());
        //记录日志
        if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
            RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "与服务器断开连接: " + ctx.channel().remoteAddress());
        } else {
            RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "与服务器断开连接: " + ctx.channel().remoteAddress());
        }
        if (clientId != null && !clientId.isEmpty()) {
            // 删除客户端上下文
            attribute.setContext(null);
            Map<String, TypedFuture<?>> futures = attribute.getFutures();
            if (futures != null && !futures.isEmpty()) {
                // 构造一个通用的异常对象
                Exception disconnectEx = new IllegalStateException("连接已断开，请求强制终止: " + ctx.channel().remoteAddress());

                int count = 0;
                // 直接遍历 Values，避免使用 key 查找，效率更高
                for (TypedFuture<?> typedFuture : futures.values()) {
                    if (typedFuture != null && typedFuture.getFuture() != null && !typedFuture.getFuture().isDone()) {
                        // 强制完成异常
                        typedFuture.getFuture().completeExceptionally(disconnectEx);
                        count++;
                    }
                }
                // 清空 Map
                futures.clear();

                RcsLog.consoleLog.warn("[{}] 断线清理: 已终止 {} 个挂起的同步请求", logPrefix, count);
            }
            // 【关键策略】断开即下线
            publishEvent(ctx.channel(), NettyConnectionEvent.State.DISCONNECTED);
            attribute.setRequestId(new AtomicLong(0L));
            //记录日志
            if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
                RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "服务器断开连接，上下文已删除");
            } else {
                RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "服务器断开连接，上下文已删除");
            }

            RcsLog.consoleLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "服务器断开连接，上下文已删除");
            taskDispatcher.unregister(clientId);
            //触发告警
            alarmManager.triggerAlarm(equipmentType + clientId, AlarmCodeEnum.E12003, "rcs");
        }

        //调用事件处理器方法
        attribute.getHandler().channelInactive(ctx);
    }

    /**
     * 触发时机：当 Channel 从 EventLoop 中注销时调用。
     * 用途：清理与 Channel 相关的资源。
     * 注意：此时 Channel 可能已关闭或未绑定。
     * <p>
     * 当关闭连接时，第二个调用channelUnregistered
     *
     * @param ctx 上下文
     */
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // 从 Channel 的 Attribute 中获取客户端 ID
        Channel channel = ctx.channel();
        String protocol = channel.attr(AttributeKeyEnum.PROTOCOL.key()).get();
        String clientId = attribute.getClientId();
        String equipmentType = attribute.getEquipmentType().getEquipmentType();

        //获取连接失败计数器
        int count = this.failedCounter.incrementAndGet();

        // 记录日志
        String logMsg = StrUtil.format("Channel 注销, 当前累计重试次数: {}", count);
        if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
            RcsLog.communicateLog.warn(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, logMsg);
        } else {
            RcsLog.communicateOtherLog.warn(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, logMsg);
        }

        // 判断是否超过最大重试次数
        int maxRetries = 0;
        try {
            maxRetries = Integer.parseInt(attribute.getMaxConnectFailed());
        } catch (NumberFormatException e) {
            // 默认值兜底
            maxRetries = 3;
        }

        if (count >= maxRetries) {
            RcsLog.consoleLog.error("达到最大重连次数 ({})，触发连接失败回调: {}", maxRetries, clientId);

            // 回调连接失败结果
            attribute.getHandler().connectionFailed(null, attribute);

            // 重置计数器，以免下次连接时直接报错
            this.failedCounter.set(0);
        }

        //调用事件处理器方法
        attribute.getHandler().channelUnregistered(ctx);
    }

    /**
     * 捕获异常时调用，处理错误并决定是否关闭连接
     *
     * @param ctx   上下文
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 从 Channel 的 Attribute 中获取客户端 ID
        Channel channel = ctx.channel();
        String clientId = channel.attr(AttributeKeyEnum.CLIENT_ID.key()).get();
        String equipmentType = attribute.getEquipmentType().getEquipmentType();
        // 处理异常
        cause.printStackTrace();
        //记录日志
        if (LinkEquipmentTypeEnum.isEnumByCode(LinkEquipmentTypeEnum.AGV, equipmentType)) {
            RcsLog.communicateLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "client_error", cause.getMessage());
        } else {
            RcsLog.communicateOtherLog.error(RcsLog.getTemplate(3), RcsLog.randomInt(), equipmentType + clientId, "client_error", cause.getMessage());
        }

        //调用事件处理器方法
        attribute.getHandler().exceptionCaught(ctx, cause, attribute);
    }
    //********************************************************* Netty生命周期结束 *********************************************************
    //********************************************************* Netty生命周期结束 *********************************************************
}
