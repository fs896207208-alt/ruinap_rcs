package com.ruinap.infra.command.agv.registry;

import com.ruinap.infra.command.agv.factory.AgvCommandFactory;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.log.RcsLog;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令工厂注册表
 *
 * @author qianye
 * @create 2026-01-13 18:01
 */
@Component
public class CommandFactoryRegistry {
    /**
     * 注册表
     */
    private final Map<String, AgvCommandFactory> registry = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 初始化方法
     * 容器在创建 Bean 并完成 @Autowired 注入后，会自动调用此方法
     */
    @PostConstruct
    public void init() {
        // 向容器询问：把所有实现了 AgvCommandFactory 的 Bean 给我
        Map<String, AgvCommandFactory> factoryBeans = applicationContext.getBeansOfType(AgvCommandFactory.class);

        if (factoryBeans == null || factoryBeans.isEmpty()) {
            RcsLog.consoleLog.warn("警告: 指令注册表中未发现任何 AGV 协议工厂实现");
            return;
        }

        // 遍历并注册
        factoryBeans.forEach((beanName, factory) -> {
            String protocol = factory.getProtocol();
            if (registry.containsKey(protocol)) {
                RcsLog.sysLog.warn("协议冲突: Protocol={}, 忽略组件: {}", protocol, beanName);
            } else {
                registry.put(protocol, factory);
                RcsLog.sysLog.info("已加载指令协议: [{}] -> {}", protocol, beanName);
            }
        });
    }

    /**
     * 获取指令工厂
     *
     * @param protocol 协议
     * @return AGV指令工厂
     */
    public AgvCommandFactory getFactory(String protocol) {
        return Optional.ofNullable(registry.get(protocol))
                .orElseThrow(() -> new IllegalArgumentException("不支持的协议: " + protocol));
    }
}
