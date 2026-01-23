package com.ruinap.adapter.communicate.server;


import com.ruinap.adapter.communicate.base.ServerAttribute;
import com.ruinap.adapter.communicate.server.registry.ServerHandlerRegistry;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.thread.VthreadPool;

/**
 * Netty服务端工厂类
 *
 * @author qianye
 * @create 2025-04-28 16:44
 */
@Component
public class NettyServerFactory {
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private ServerHandlerRegistry handlerRegistry;
    @Autowired
    private AlarmManager alarmManager;

    /**
     * 通用创建方法
     * 这里的逻辑是：只负责 new 对象，不负责 start。
     * start 由 NettyManager 统一调用。
     */
    public NettyServer create(ServerAttribute attribute) {
        // 将 Spring 管理的依赖注入进去
        return new NettyServer(attribute, handlerRegistry, alarmManager, vthreadPool);
    }
}
