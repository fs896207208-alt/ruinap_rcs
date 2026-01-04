package com.ruinap.infra.log.appender;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * WebSocket客户端日志附加器
 * <p>
 * 该附加器允许您将日志通过 WebSocket 发送日志到客户端
 *
 * @author qianye
 * @create 2024-09-14 04:44
 */
@Plugin(name = "WsClientAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class WsClientAppender extends AbstractAppender {

    /**
     * 构造函数
     *
     * @param name             插件名称
     * @param filter           过滤器
     * @param layout           布局
     * @param ignoreExceptions 是否忽略异常
     * @param properties       属性数组
     */
    protected WsClientAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
        // 调用新的构造函数
        super(name, filter, layout, ignoreExceptions, properties);
    }

    /**
     * 处理日志事件
     */
    @Override
    public void append(LogEvent event) {
        Layout<? extends Serializable> layout = getLayout();
        String formattedMessage;

        // 【优化核心】：判断布局是否为字符串布局（PatternLayout 是 StringLayout）
        // 如果是，直接获取 String，避免 "Layout -> byte[] -> String" 的双重转换和内存分配
        if (layout instanceof StringLayout) {
            formattedMessage = ((StringLayout) layout).toSerializable(event);
        } else {
            // 兜底逻辑：如果不是 StringLayout（极少情况），才进行字节转换
            byte[] bytes = layout.toByteArray(event);
            formattedMessage = new String(bytes, StandardCharsets.UTF_8);
        }

        // 调用后续处理（你已明确会异步处理此方法，此处仅负责传递）
        callAdditionalMethod(formattedMessage);
    }

    /**
     * 接收格式化后的消息
     *
     * @param formattedMessage 格式化后的消息
     */
    private void callAdditionalMethod(String formattedMessage) {
        // 处理格式化后，需要使用异步去发网络请求，立刻释放 Log4j 线程
//        NettyServer.sendMessageAll(ProtocolEnum.WEBSOCKET_SERVER, ServerRouteEnum.CONSOLE, formattedMessage);
    }

    /**
     * 使用 PluginFactory 创建该自定义 Appender
     *
     * @param name             插件名称
     * @param layout           插件布局
     * @param filter           过滤器
     * @param ignoreExceptions 是否忽略异常
     * @return
     */
    @PluginFactory
    public static WsClientAppender createAppender(@PluginAttribute("name") String name,
                                                  @PluginElement("Layout") Layout<? extends Serializable> layout,
                                                  @PluginElement("Filter") Filter filter,
                                                  @PluginAttribute("ignoreExceptions") boolean ignoreExceptions) {
        if (layout == null) {
            // 指定日志输出格式
            layout = PatternLayout.newBuilder()
                    .withPattern("[%level] %msg%n")
                    .build();
        }
        // 如果你需要传递额外的属性，这里可以设置
        Property[] properties = new Property[0];

        return new WsClientAppender(name, filter, layout, ignoreExceptions, properties);
    }


}
