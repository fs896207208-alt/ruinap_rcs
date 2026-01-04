package com.ruinap.infra.monitor;

import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.taobao.arthas.agent.attach.ArthasAgent;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Arthas 诊断工具集成服务
 * <p>
 * 负责在应用启动时自动挂载 Arthas Agent，开启 Web Console 和 Telnet。
 * </p>
 *
 * <h3>⚠️ 生产环境启动要求 (JDK 21+)</h3>
 * 由于 JDK 21 默认禁止动态代理加载 (Self-Attach)，如果需要使用此功能，
 * <strong>必须</strong>在启动脚本中添加以下 JVM 参数，否则会抛出 IOException：
 * <pre>
 * -XX:+EnableDynamicAgentLoading
 * 或者
 * -Djdk.attach.allowAttachSelf=true
 * </pre>
 *
 * <h3>🚀 如何开启</h3>
 * 出于安全考虑，默认不启动。需要在启动参数或配置文件中显式开启：
 * <pre>
 * java -Drcs.arthas.enable=true -XX:+EnableDynamicAgentLoading -jar ruinap_rcs.jar
 * </pre>
 *
 * <h3>🔒 安全警告</h3>
 * 本服务绑定 IP 为 0.0.0.0，且默认关闭了鉴权。
 * 请勿在公网直接暴露此端口 (8563)，建议仅在内网受信任环境使用。
 *
 * @author qianye
 * @create 2025-12-17 10:40
 */
@Component
public class ArthasService implements CommandLineRunner {

    @Autowired
    private CoreYaml coreYaml;
    /**
     * Arthas 开关配置 Key
     */
    private static final String ENABLE_KEY = "rcs.arthas.enable";

    @Override
    public void run(String... args) {
        // 设置账号
        System.setProperty("arthas.username", "admin");
        // 设置密码
        System.setProperty("arthas.password", "123456");
        // 允许外部访问
        System.setProperty("arthas.ip", "0.0.0.0");

        // 1. 检查开关：生产环境默认关闭，只有显式配置为 true 才启动
        String enable = coreYaml.getEnableArthas();

        if (!"true".equalsIgnoreCase(enable)) {
            // 如果没开启，静默跳过，不打印日志打扰视线
            return;
        }
        try {
            startArthas();
        } catch (Exception e) {
            // 2. 智能提示错误原因
            if (e.getMessage() != null && e.getMessage().contains("Can not attach to current VM")) {
                RcsLog.consoleLog.error("Arthas 启动失败！检测到 JDK 21 环境限制。");
                RcsLog.consoleLog.error("请在启动命令中添加 JVM 参数: -XX:+EnableDynamicAgentLoading");
            } else {
                RcsLog.consoleLog.error("Arthas 启动发生未知异常", e);
            }
        }
    }

    private void startArthas() {
        Map<String, String> config = new HashMap<>(5);

        // 应用名称
        config.put("appName", "ruinap_rcs");
        // 允许外部访问 (0.0.0.0)
        config.put("ip", "0.0.0.0");
        // Web 端口
        config.put("httpPort", "8563");
        // Telnet 端口
        config.put("telnetPort", "3658");
        // 启动挂载
        ArthasAgent.attach(config);

        RcsLog.consoleLog.warn("========================= Arthas =========================");
        RcsLog.consoleLog.warn(" Arthas 诊断工具已启动 (JDK 21 Mode)");
        RcsLog.consoleLog.warn(" Web控制台: http://{}:8563/", getLocalIp());
        RcsLog.consoleLog.warn("========================  Arthas  ========================");
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
