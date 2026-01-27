package com.ruinap.adapter.communicate.server.registry;

import com.ruinap.adapter.communicate.server.handler.IServerHandler;
import com.ruinap.infra.enums.netty.ProtocolEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.log.RcsLog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端处理器注册中心
 *
 * @author qianye
 * @create 2026-01-22 11:39
 */
@Component
public class ServerHandlerRegistry {
    private final Map<ServerHandleKey, IServerHandler> handlerMap = new ConcurrentHashMap<>();
    @Autowired
    private List<IServerHandler> handlerList;

    @PostConstruct
    public void init() {
        if (handlerList == null || handlerList.isEmpty()) {
            RcsLog.consoleLog.warn("未发现任何 ServerHandler 实现，服务端将无法处理请求！");
            return;
        }

        for (IServerHandler handler : handlerList) {
            // 自动调用 register
            this.register(handler.getProtocol(), handler.getPath(), handler);
        }
    }

    /**
     * 注册处理器
     */
    public void register(ProtocolEnum protocol, String path, IServerHandler handler) {
        ServerHandleKey key = new ServerHandleKey(protocol, path);
        if (handlerMap.containsKey(key)) {
            RcsLog.consoleLog.warn("Handler 覆盖警告: 协议 [{}], 路径 [{}] 已存在处理器", protocol, path);
        }
        handlerMap.put(key, handler);
        RcsLog.consoleLog.info("注册 Handler: 协议 [{}], 路径 [{}] -> {}", protocol, path, handler.getClass().getSimpleName());
    }

    /**
     * 获取处理器
     */
    public IServerHandler getHandler(ProtocolEnum protocol, String path) {
        return handlerMap.get(new ServerHandleKey(protocol, path));
    }

    /**
     * 针对 MQTT 等不需要 Path 的协议的便捷方法
     */
    public IServerHandler getHandler(ProtocolEnum protocol) {
        return getHandler(protocol, "");
    }
}
