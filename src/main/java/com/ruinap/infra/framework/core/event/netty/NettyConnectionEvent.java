package com.ruinap.infra.framework.core.event.netty;

import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.framework.core.event.ApplicationEvent;
import io.netty.channel.Channel;
import lombok.Getter;

/**
 * Netty 通用连接状态事件
 * <p>
 * <strong>核心设计意图：</strong><br>
 * 本事件是通讯模块“事件驱动架构 (EDA)”的核心载体，用于在底层通讯组件（NettyClient/ServerHandler）
 * 和上层管理器（NettyManager）之间传递连接状态变更通知。
 * <p>
 * 它统一了“主动连接（Client）”和“被动接入（Server）”两种场景下的上线/下线行为，
 * 使得 NettyManager 可以通过监听单一事件来维护全局的连接路由表和设备状态，
 * 彻底解耦了业务逻辑与底层网络实现。
 *
 * @author qianye
 * @create 2026-01-23 15:34
 */
@Getter
public class NettyConnectionEvent extends ApplicationEvent {

    /**
     * 连接状态枚举
     * <p>
     * 描述连接当前的生命周期阶段。
     */
    public enum State {
        /**
         * <strong>已连接 (上线)</strong>
         * <p>
         * 触发时机：
         * <ul>
         * <li>TCP 客户端：连接建立成功 (channelActive)</li>
         * <li>WebSocket/MQTT：握手/鉴权成功 (userEventTriggered/handleConnect)</li>
         * </ul>
         * 含义：设备已就绪，可以进行双向通信。
         */
        CONNECTED,

        /**
         * <strong>已断开 (下线)</strong>
         * <p>
         * 触发时机：
         * <ul>
         * <li>连接正常关闭</li>
         * <li>心跳超时或网络异常导致断开 (channelInactive/exceptionCaught)</li>
         * </ul>
         * 含义：设备不可达，需清理路由表和相关资源。
         */
        DISCONNECTED
    }

    /**
     * 连接来源枚举
     * <p>
     * 用于区分连接的建立方式，决定 NettyManager 的处理策略。
     */
    public enum ConnectSource {
        /**
         * <strong>服务端 (Server)</strong>
         * <p>
         * 含义：本系统作为 Server，接收到的外部设备连接（被动接入）。<br>
         * 处理策略：通常只注册到全局路由表 (GlobalChannelMap)，不维护实例引用。
         */
        SERVER,

        /**
         * <strong>客户端 (Client)</strong>
         * <p>
         * 含义：本系统作为 Client，主动发起的对外连接。<br>
         * 处理策略：除了注册路由表外，还需要维护 NettyClient 实例引用 (ActiveClients)，用于断线重连和保活。
         */
        CLIENT
    }

    // ==================== 事件载荷字段 ====================

    /**
     * 设备/客户端 ID
     * <p>
     * 唯一标识一个连接主体（如 AGV 车号、充电桩编号）。
     * 必须保证非空且在同一类型下唯一。
     */
    private final String id;

    /**
     * 设备类型
     * <p>
     * 标识连接所属的业务分类（如 AGV, CHARGE_PILE, VISUAL 等）。
     * 用于区分不同的业务处理逻辑。
     */
    private final LinkEquipmentTypeEnum type;

    /**
     * Netty 底层通道
     * <p>
     * 实际进行数据读写的 Channel 对象。
     * NettyManager 将使用此对象进行消息发送。
     */
    private final Channel channel;

    /**
     * 当前连接状态 (上线/下线)
     */
    private final State state;

    /**
     * 连接来源 (主动/被动)
     */
    private final ConnectSource connectSource;

    /**
     * 构建一个新的连接状态事件
     *
     * @param source        事件触发源 (通常是 NettyClient 实例或 ServerHandler 实例)
     * @param connectSource 连接来源 (CLIENT/SERVER)，用于区分主动/被动连接
     * @param type          设备类型枚举
     * @param id            设备唯一 ID
     * @param channel       Netty Channel 对象 (不能为空)
     * @param state         连接状态 (CONNECTED/DISCONNECTED)
     */
    public NettyConnectionEvent(Object source,
                                ConnectSource connectSource,
                                LinkEquipmentTypeEnum type,
                                String id,
                                Channel channel,
                                State state) {
        super(source);
        this.connectSource = connectSource;
        this.type = type;
        this.id = id;
        this.channel = channel;
        this.state = state;
    }

    /**
     * 返回事件的字符串描述，便于日志调试
     *
     * @return 格式化的事件信息
     */
    @Override
    public String toString() {
        return String.format("NettyEvent{source=%s, type=%s, id='%s', state=%s, channelId=%s}",
                connectSource, type, id, state, (channel != null ? channel.id().asShortText() : "null"));
    }
}