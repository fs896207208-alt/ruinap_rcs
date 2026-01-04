package com.ruinap.infra.log.appender;

import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import org.apache.logging.log4j.Level;
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
import java.util.regex.Pattern;

/**
 * SQL日志附加器
 * <p>
 * 该附加器允许您将日志通过 ConsoleLog 输出到控制台和日志文件中
 * 原理是：将 cn.hutool.db.sql.SqlLog 记录的日志捕获到此追加器中，然后使用 ConsoleLog 输出
 * 注意：必须确保 RcsDSFactory 的 startup 方法开启了 Hutool 的 SQL 日志打印，GlobalDbConfig.setShowSql(true, false, true, Level.DEBUG);
 * <p>
 * SQL日志开关设置：log4j2.xml - SqlAppender - enabled
 *
 * @author qianye
 * @create 2024-09-14 04:44
 */
@Plugin(name = "SqlAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class SqlAppender extends AbstractAppender {
    /**
     * xml配置的 SqlAppender 日志级别
     */
    private final Level targetLevel;
    /**
     * 日志开关：true-开启，false-关闭
     */
    private final boolean enabled;
    /**
     * 正则：匹配任何空白字符（空格、Tab、换行等）
     * \\s+ 表示匹配一个或多个连续的空白字符
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private volatile VthreadPool vthreadPool;

    /**
     * 构造函数
     *
     * @param name             插件名称
     * @param filter           过滤器
     * @param layout           布局
     * @param ignoreExceptions 是否忽略异常
     * @param properties       属性数组
     * @param targetLevel      日志级别
     * @param enabled          日志开关
     */
    protected SqlAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties, Level targetLevel, boolean enabled) {
        // 调用新的构造函数
        super(name, filter, layout, ignoreExceptions, properties);
        // 如果未配置，默认使用 DEBUG
        this.targetLevel = targetLevel != null ? targetLevel : Level.DEBUG;
        this.enabled = enabled;
    }

    /**
     * 处理日志事件
     */
    @Override
    public void append(LogEvent event) {
        // 开关检查：如果配置为 false，直接返回，不做任何处理
        if (!this.enabled) {
            return;
        }
        if (this.vthreadPool == null) {
            this.vthreadPool = SpringContextHolder.getBean(VthreadPool.class);
        }
        // 依然需要判空（因为 Log4j 可能比容器启动得早）
        if (this.vthreadPool == null) {
            return;
        }

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

        // 格式化处理
        String processedMsg = WHITESPACE_PATTERN.matcher(formattedMessage).replaceAll(" ");
        // replace不使用正则，性能更好
        processedMsg = processedMsg.replace("Params", "[Params]");
        //去除首尾空格
        // Hutool 的 SQL 日志经常带有首尾换行，trim() 非常关键，否则会打印出空行
        String finalMsg = processedMsg.trim();

        // 如果处理后是空的（例如只是换行符），直接丢弃，不打印
        if (finalMsg.isEmpty()) {
            return;
        }

        // 4. 异步执行，不要调用.get()
        vthreadPool.runAsync(() -> {
            try {
                // 将处理后的 SQL 转给 ConsoleLog，带上随机数前缀
                RcsLog.consoleLog.log(this.targetLevel, RcsLog.getTemplate(2), RcsLog.randomInt(), finalMsg);
            } catch (Exception e) {
                // 仅仅打印异常堆栈，不要抛出 RuntimeException 导致线程池崩溃
                e.printStackTrace();
            }
        });
    }

    /**
     * 使用 PluginFactory 创建该自定义 Appender
     *
     * @param name             插件名称
     * @param layout           插件布局
     * @param filter           过滤器
     * @param ignoreExceptions 是否忽略异常
     * @param level            日志级别
     * @param enabled          日志开关
     * @return Appender
     */
    @PluginFactory
    public static SqlAppender createAppender(@PluginAttribute("name") String name,
                                             @PluginElement("Layout") Layout<? extends Serializable> layout,
                                             @PluginElement("Filter") Filter filter,
                                             @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                             @PluginAttribute("level") Level level,
                                             @PluginAttribute(value = "enabled") boolean enabled
    ) {
        if (layout == null) {
            // 指定日志输出格式
            layout = PatternLayout.newBuilder().withPattern("%msg").build();
        }
        // 如果你需要传递额外的属性，这里可以设置
        Property[] properties = new Property[0];

        return new SqlAppender(name, filter, layout, ignoreExceptions, properties, level, enabled);
    }


    public void setVthreadPool(VthreadPool vthreadPool) {
        this.vthreadPool = vthreadPool;
    }
}
