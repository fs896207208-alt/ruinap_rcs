package com.ruinap.adapter.communicate.server.handler;

import com.ruinap.infra.enums.netty.AttributeKeyEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 空闲状态检测处理器
 *
 * @author qianye
 * @create 2025-01-28 23:27
 */
public class IdleEventHandler extends ChannelInboundHandlerAdapter {
    // 允许的最大丢失次数（三振出局）
    private static final int MAX_LOSS_CONNECT_COUNT = 3;
    private final AtomicInteger lossConnectCount = new AtomicInteger(0);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                // 1. 计数器 +1
                int count = lossConnectCount.incrementAndGet();

                if (count > MAX_LOSS_CONNECT_COUNT) {
                    // 2. 超过阈值，真的认为是断开了
                    RcsLog.consoleLog.warn("[心跳超时] 连续 {} 次未收到客户端数据，强制关闭连接: {}", count, ctx.channel().remoteAddress());
                    ctx.close();
                } else {
                    // 3. 未超阈值，发送探测包 (Ping)

                    // [Step 1] 从 Channel 属性中获取当前连接的协议字符串
                    String protocolStr = ctx.channel().attr(AttributeKeyEnum.PROTOCOL.key()).get();
                    // [Step 2] 转为枚举对象
                    ProtocolEnum protocol = ProtocolEnum.fromProtocol(protocolStr);

                    Object pingMsg = null;

                    if (protocol != null) {
                        switch (protocol) {
                            case TCP_CLIENT:
                                // =========================================================
                                // 场景 A: TCP 私有协议 (如: 充电桩, PLC, 自研AGV)
                                // =========================================================
                                // 必须发送 byte[], 因为 Pipeline 里有 ByteArrayEncoder
                                // TODO: 请根据实际设备文档修改此处的 Hex 指令 (例如: 0xFA 0xF5)
                                pingMsg = new byte[]{(byte) 0xFA, (byte) 0xF5};
                                break;

                            case WEBSOCKET_SERVER:
                            case WEBSOCKET_CLIENT:
                                // =========================================================
                                // 场景 B: WebSocket 协议 (如: 上位机, 网页端)
                                // =========================================================
                                // 最佳实践：发送标准的 Ping 帧，不干扰业务层 (TextFrame)
                                // 浏览器/客户端会自动回复 Pong，Netty 底层会自动处理 Pong
                                pingMsg = new PingWebSocketFrame();
                                break;

                            case MQTT_SERVER:
                                // =========================================================
                                // 场景 C: MQTT 协议 (VDA5050 AGV)
                                // =========================================================
                                // MQTT 协议规定：KeepAlive 由客户端(AGV)发起 PINGREQ。
                                // 如果服务端触发了 ReaderIdle，说明 AGV 已经超时未发 PINGREQ 了。
                                // 服务端主动发包探测在 MQTT 中不标准，通常直接断开即可。
                                // 这里我们选择“什么都不做”或“记录日志”，交由下一次 idle > max 直接 close
                                RcsLog.consoleLog.debug("MQTT 协议读空闲，等待客户端主动保活或超时关闭");
                                return;

                            default:
                                RcsLog.consoleLog.warn("未知协议类型 [{}]，无法发送心跳", protocolStr);
                                return;
                        }
                    }

                    // [Step 3] 执行发送并监听结果
                    if (pingMsg != null) {
                        ctx.writeAndFlush(pingMsg).addListener(future -> {
                            if (future.isSuccess()) {
                                RcsLog.consoleLog.debug("心跳探测成功发送 [{}]: {}", protocol, ctx.channel().remoteAddress());
                            } else {
                                RcsLog.communicateLog.warn("心跳探测发送失败 (可能已断线): {}", ctx.channel().remoteAddress());
                            }
                        });

                        RcsLog.consoleLog.warn("连接假死探测: 第 {}/{} 次心跳保活...", count, MAX_LOSS_CONNECT_COUNT);
                    }
                }
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 4. 只要收到任何数据（包括业务数据或 Pong），就重置计数器
        lossConnectCount.set(0);
        super.channelRead(ctx, msg);
    }
}