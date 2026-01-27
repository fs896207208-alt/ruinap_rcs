package com.ruinap.adapter.communicate.base;

import com.ruinap.adapter.communicate.base.protocol.IProtocolOption;
import com.ruinap.adapter.communicate.client.NettyClient;
import com.ruinap.adapter.communicate.client.handler.ClientHandler;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.log.RcsLog;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端属性
 *
 * @author qianye
 * @create 2025-04-29 16:06
 */
@Setter
@Getter
public class ClientAttribute extends BaseAttribute {

    /**
     * WebSocket 服务器地址
     */
    private final URI uri;
    /**
     * 客户端 ID
     */
    private final String clientId;
    /**
     * 设备类型枚举
     */
    private LinkEquipmentTypeEnum equipmentType;
    /**
     * 连接失败次数
     */
    private final String maxConnectFailed;
    /**
     * 客户端处理器
     */
    private ClientHandler handler;
    /**
     * 存储客户端连接上下文
     */
    private ChannelHandlerContext context;
    /**
     * 全局 Map，用于存储客户端 ID 和对应的 异步结果集
     */
    @Setter(AccessLevel.NONE)
    private Map<String, TypedFuture<?>> futures = new ConcurrentHashMap<>();
    /**
     * 存储客户端请求ID
     */
    private AtomicLong requestId = new AtomicLong(0);

    /**
     * 构造方法
     *
     * @param uri              地址
     * @param clientId         客户端ID (请确保唯一)
     * @param equipmentType    设备类型枚举
     * @param maxConnectFailed 最大连接失败次数
     * @param protocol         协议枚举
     * @param protocolOption   协议处理器
     * @param handler          客户端处理器
     */
    public ClientAttribute(URI uri, String clientId, LinkEquipmentTypeEnum equipmentType, String maxConnectFailed,
                           IProtocolOption<Bootstrap, NettyClient> protocolOption,
                           ProtocolEnum protocol, ClientHandler handler) {
        // 调用基类构造
        super(protocol, protocolOption);
        this.uri = uri;
        this.clientId = clientId;
        this.equipmentType = equipmentType;
        this.maxConnectFailed = maxConnectFailed;
        this.handler = handler;
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
        futures.put(key, new TypedFuture<>(future, type));
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
        TypedFuture<?> entry = futures.remove(key);
        // 检查移除的 entry 是否不为空
        if (entry != null) {
            // 检查类型是否匹配
            if (expectedType.equals(entry.type)) {
                // 类型转换，由于 entry 类型已验证，因此这里抑制警告
                @SuppressWarnings("unchecked")
                CompletableFuture<T> future = (CompletableFuture<T>) entry.future;
                // 返回转换后的 CompletableFuture 对象
                return future;
            } else {
                // 类型不匹配，输出日志
                RcsLog.consoleLog.error("找到对应的CompletableFuture对象，但类型不匹配。期望类型: " + expectedType.getName() + ", 实际类型: " + entry.type.getName());
                return null;
            }
        }
        // 如果 entry 为空或类型不匹配，返回 null
        return null;
    }
}