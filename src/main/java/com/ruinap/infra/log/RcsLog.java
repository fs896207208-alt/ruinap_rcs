package com.ruinap.infra.log;

import cn.hutool.core.util.RandomUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;

import java.io.File;

/**
 * 调度日志类
 * debug  指出细粒度信息事件对调试应用程序是非常有帮助的 主要用于开发过程中打印一些运行信息
 * info   消息在粗粒度级别上突出强调应用程序的运行过程
 * warn   表明会出现潜在错误的情形 有些信息不是错误信息 但是也要给程序员的一些提示
 * error  指出虽然发生错误事件 但仍然不影响系统的继续运行
 *
 * @author qianye
 * @create 2024-04-03 9:13
 */

public class RcsLog {

    /**
     * 控制台日志
     * 一般用于开发和运行过程中打印和记录日志，并且会通过Websocket上报给调度客户端
     */
    public static final Logger consoleLog = LogManager.getLogger("Console");
    /**
     * 系统日志
     * 一般用于记录系统运行过程中产生的系统错误及异常信息
     */
    public static final Logger sysLog = LogManager.getLogger("SysLog");
    /**
     * 通信日志
     * 一般用于记录与AGV通信相关的日志信息
     */
    public static final Logger communicateLog = LogManager.getLogger("CommunicateLog");
    /**
     * 通信其他日志
     * 一般用于记录与其他设备通信相关的日志信息
     */
    public static final Logger communicateOtherLog = LogManager.getLogger("CommunicateOtherLog");
    /**
     * 任务日志
     * 一般用于记录任务生命周期内的日志信息
     * <p>
     * 比如：任务开始、任务结束、任务状态变化、任务上报、设备对接日志等
     */
    public static final Logger taskLog = LogManager.getLogger("TaskLog");
    /**
     * 算法日志
     * 一般用于记录程序运行相关的详细日志信息
     * 你可以简单理解为 控制台日志 的升级版，不使用去重过滤器，比控制台日志，记录的更详细更全面
     */
    public static final Logger algorithmLog = LogManager.getLogger("AlgorithmLog");
    /**
     * HTTP日志
     * 一般用于记录HTTP请求相关的日志信息
     */
    public static final Logger httpLog = LogManager.getLogger("HttpLog");
    /**
     * 操作日志
     * 一般用于记录功能操作相关的日志信息
     * <p>
     * 比如：暂停任务、中断任务、开始任务等
     */
    public static final Logger operateLog = LogManager.getLogger("OperateLog");
    /**
     * 关键帧日志
     * 用于记录回放数据帧（如AGV数据、任务快照）
     */
    public static final Logger keyFrameLog = LogManager.getLogger("KeyFrameLog");

    /**
     * 获取关键帧日志文件目录
     *
     * @return 文件目录
     */
    public static String getKeyFrameLogDirectory() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        RollingFileAppender appender = config.getAppender("KeyFrameLogFile");

        if (appender != null) {
            String fileName = appender.getFileName();
            if (fileName != null) {
                File file = new File(fileName);
                // 直接获取父目录
                String parentDir = file.getParent();
                // 统一替换反斜杠为正斜杠（兼容Windows路径）
                return parentDir != null ? parentDir.replace("\\", "/") : null;
            }
        }
        return null;
    }

    /**
     * 获取日志格式模板 (O(1) 复杂度)
     * <p>
     * 格式示例：
     * 1参: "[{}]"
     * 2参: "[{}] {}"
     * 3参: "[{}] {} {}"
     * </p>
     * 如果需要随机数，请在第一个参数调用 randomInt方法
     *
     * @param count 总参数数量
     * @return 模板字符串
     */
    public static String getTemplate(int count) {
        if (count < 0) {
            return "{}";
        }
        // 命中缓存直接返回
        if (count < CACHE_SIZE) {
            return TEMPLATES[count];
        }
        // 未命中缓存（参数超多），动态生成
        return generateTemplate(count);
    }

    /**
     * 获取随机数
     *
     * @return 5位随机数
     */
    public static int randomInt() {
        return RandomUtil.randomInt(10000, 99999);
    }

    /**
     * 模板缓存池 (预计算，零垃圾)
     * 假设最多支持 10 个参数，通常够用了
     * 格式遵循你之前的逻辑：第一个参数 [{}], 后面的 {}
     * * 针对 formatTemplateRandom(3) -> random, agv, msg
     * 对应格式：[{}] [{}] {}
     */
    private static final int CACHE_SIZE = 11;
    private static final String[] TEMPLATES = new String[CACHE_SIZE];

    static {
        // 初始化缓存：循环调用生成方法，复用逻辑
        for (int i = 0; i < CACHE_SIZE; i++) {
            TEMPLATES[i] = generateTemplate(i);
        }
    }

    /**
     * 生成通用模板
     * 注意：传入0 时返回 {}
     * 逻辑：第一个参数用 [] 包裹，后面的用空格分隔
     */
    private static String generateTemplate(int count) {
        if (count <= 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i == 0) {
                sb.append("[{}]");
            } else {
                sb.append(" {}");
            }
        }
        return sb.toString();
    }
}
