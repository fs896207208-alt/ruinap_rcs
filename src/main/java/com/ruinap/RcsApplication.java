package com.ruinap;

import cn.hutool.core.util.StrUtil;
import com.ruinap.infra.framework.annotation.EnableAsync;
import com.ruinap.infra.framework.annotation.EnableScheduling;
import com.ruinap.infra.framework.annotation.EnableTransaction;
import com.ruinap.infra.framework.boot.SpringBootApplication;
import com.ruinap.infra.framework.core.AnnotationConfigApplicationContext;
import com.ruinap.infra.log.RcsLog;

/**
 * 调度系统启动类 (Refactored for LCLM)
 * <p>
 * 现在的启动类非常干净。它不再负责具体的初始化逻辑（如连接数据库、启动线程池），
 * 而是充当一个“引导者”，负责启动 IoC 容器。
 * 具体的业务逻辑由各个组件 (@Component) 自行管理。
 *
 * @author qianye
 * @create 2025-11-28 11:18
 */
@SpringBootApplication
// 开启异步执行能力 (@Async 生效)
@EnableAsync
// 开启事务管理能力 (@Transactional 生效)
@EnableTransaction
// 开启定时任务能力 (@RcsScheduled、@RcsCron 生效)
@EnableScheduling
public class RcsApplication {

    /**
     * 版本号
     * 版本号在软件开发和发布过程中扮演着重要的角色，它能够帮助开发者进行版本控制，帮助用户了解软件的功能和特性，并处理兼容性问题
     * X主版本号：当软件的大量功能发生重大改变或者升级时，主版本号会发生改变。例如，从 V1 升级到 V2
     * Y次版本号：当软件增加新的功能或者修改现有功能时，次版本号会发生改变。例如，从 V2.0 升级到 V2.1
     * Z修订号：当软件修复程序中的错误或者问题时，修订号会发生改变。例如，从 V2.1.0 升级到 V2.1.1
     */
    private static final String VERSION = "v4.0.0";

    /**
     * 格式化版本号
     *
     * @return版本号
     */
    public static String formatVersion() {
        return StrUtil.format("{}.{}.{}",
                StrUtil.padPre(StrUtil.blankToDefault(StrUtil.splitTrim(RcsApplication.VERSION, ".").get(0), "0"), 2, '0'),
                StrUtil.padPre(StrUtil.blankToDefault(StrUtil.splitTrim(RcsApplication.VERSION, ".").get(1), "0"), 2, '0'),
                StrUtil.padPre(StrUtil.blankToDefault(StrUtil.splitTrim(RcsApplication.VERSION, ".").get(2), "0"), 2, '0')
        );
    }

    /**
     * 打印启动程序的Banner
     */
    public static void printlnStartBanner() {
        StringBuilder banner = new StringBuilder();
        banner.append("\n*********************** 项目启动 ***********************\n")
                .append("* ██████╗  ██╗   ██╗ ██╗ ███╗   ██╗  █████╗  ██████╗  *\n")
                .append("* ██╔══██╗ ██║   ██║ ██║ ████╗  ██║ ██╔══██╗ ██╔══██╗ *\n")
                .append("* ██████╔╝ ██║   ██║ ██║ ██╔██╗ ██║ ███████║ ██████╔╝ *\n")
                .append("* ██╔══██╗ ██║   ██║ ██║ ██║╚██╗██║ ██╔══██║ ██╔═══╝  *\n")
                .append("* ██║  ██║ ╚██████╔╝ ██║ ██║ ╚████║ ██║  ██║ ██║      *\n")
                .append("* ╚═╝  ╚═╝  ╚═════╝  ╚═╝ ╚═╝  ╚═══╝ ╚═╝  ╚═╝ ╚═╝      *\n")
                .append(StrUtil.format("*                 version : {}                  *\n", formatVersion()))
                .append("*******************************************************\n");
        RcsLog.algorithmLog.info(banner);
        RcsLog.consoleLog.info(banner);
        RcsLog.sysLog.info(banner);
        RcsLog.communicateLog.info(banner);
        RcsLog.communicateOtherLog.info(banner);
        RcsLog.taskLog.info(banner);
        RcsLog.operateLog.info(banner);
        RcsLog.httpLog.info(banner);
    }

    /**
     * 打印启动成功的Banner
     */
    private static void printlnSuccessBanner() {
        StringBuilder banner = new StringBuilder();
        banner.append("\n******************** 启动成功 ********************\n")
                .append("* ██████  ██    ██ ██ ███    ██  █████  ██████  *\n")
                .append("* ██   ██ ██    ██ ██ ████   ██ ██   ██ ██   ██ *\n")
                .append("* ██████  ██    ██ ██ ██ ██  ██ ███████ ██████  *\n")
                .append("* ██   ██ ██    ██ ██ ██  ██ ██ ██   ██ ██      *\n")
                .append("* ██   ██  ██████  ██ ██   ████ ██   ██ ██      *\n")
                .append(StrUtil.format("*              version : {}               *\n", formatVersion()))
                .append("*************************************************\n");
        RcsLog.algorithmLog.info(banner);
        RcsLog.consoleLog.info(banner);
        RcsLog.sysLog.info(banner);
        RcsLog.communicateLog.info(banner);
        RcsLog.communicateOtherLog.info(banner);
        RcsLog.taskLog.info(banner);
        RcsLog.operateLog.info(banner);
        RcsLog.httpLog.info(banner);
    }

    /**
     * 程序启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 【启动内核】初始化 LCLM (IoC) 容器
        // 直接传入当前类，容器会自动读取 @ComponentScan，扫描当前包
        // 并按照 @Order 顺序执行初始化 (@PostConstruct) 和启动任务 (CommandLineRunner)。
        new AnnotationConfigApplicationContext(RcsApplication.class);
        // 打印成功横幅
        printlnSuccessBanner();
    }
}
