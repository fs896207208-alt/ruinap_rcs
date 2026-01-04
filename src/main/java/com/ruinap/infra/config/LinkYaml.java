package com.ruinap.infra.config;

import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.config.pojo.LinkConfig;
import com.ruinap.infra.config.pojo.link.LinkEntity;
import com.ruinap.infra.config.pojo.link.TransferLinkEntity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 调度链接Yaml配置文件
 *
 * @author qianye
 * @create 2024-10-30 16:19
 */
@Component
public class LinkYaml implements ReloadableConfig {
    /**
     * 调度链接配置 (使用 volatile 确保多线程可见性)
     */
    private volatile LinkConfig config;

    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private Environment environment;

    /**
     * 初始化调度链接Yaml配置文件
     * 注意：请让配置文件尽早初始化，避免影响使用到配置文件的其他模块的初始化
     */
    @PostConstruct
    public void initialize() {
        // 委托 Environment 绑定
        bindInternal();
    }

    private void bindInternal() {
        LinkConfig temp = environment.bind("rcs_link", LinkConfig.class);
        if (temp == null) {
            temp = new LinkConfig();
        }
        // 执行特殊合并逻辑
        handlerLinkConfig(temp);
        this.config = temp;
    }

    /**
     * 【内存重载】由外部接口触发
     */
    @Override
    public void rebind() {
        // 纯内存操作，因为GlobalConfigManager已经重新从配置文件读取了最新的数据，这里直接从 Environment 拿最新数据
        bindInternal();
    }

    @Override
    public String getSourceName() {
        return "rcs_link";
    }

    /**
     * 处理配置数据
     * 该方法主要用于处理AGV、充电桩和中转系统的链接配置
     * 它通过合并通用配置和特定设备配置来确保每个设备都使用了一致的配置参数
     */
    private void handlerLinkConfig(LinkConfig config) {
        // 使用反射自动处理所有LinkEntity类型的属性
        Field[] fields = config.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(config);

                if (fieldValue instanceof LinkEntity) {
                    // 处理LinkEntity类型的属性（如agvLink和chargeLink）
                    LinkEntity linkEntity = (LinkEntity) fieldValue;
                    Map<String, String> common = linkEntity.getCommon();
                    if (common != null && !linkEntity.getLinks().isEmpty()) {
                        for (Map.Entry<String, Map<String, String>> entry : linkEntity.getLinks().entrySet()) {
                            Map<String, String> value = entry.getValue();
                            // 动态合并所有通用配置到设备配置
                            for (Map.Entry<String, String> commonEntry : common.entrySet()) {
                                value.putIfAbsent(commonEntry.getKey(), commonEntry.getValue());
                            }
                        }
                    }
                } else if (fieldValue instanceof TransferLinkEntity) {
                    // 处理TransferLinkEntity类型的属性（如transferLink）
                    TransferLinkEntity transferLinkEntity = (TransferLinkEntity) fieldValue;
                    Map<String, String> common = transferLinkEntity.getCommon();
                    Map<String, String> link = transferLinkEntity.getLink();
                    if (common != null && link != null) {
                        // 动态合并所有通用配置到设备配置
                        for (Map.Entry<String, String> commonEntry : common.entrySet()) {
                            link.putIfAbsent(commonEntry.getKey(), commonEntry.getValue());
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("处理配置数据时发生错误", e);
            }
        }
    }

    /**
     * 获取所有AGV配置
     *
     * @return 所有AGV集合，如果没有AGV则返回空集合
     */
    public Map<String, Map<String, String>> getAgvLink() {
        LinkConfig current = config;
        if (current != null && current.getAgvLink() != null && !current.getAgvLink().getLinks().isEmpty()) {
            return current.getAgvLink().getLinks();
        }
        return new HashMap<>(0);
    }

    /**
     * 获取指定AGV配置
     *
     * @param agvCode AGV编号
     * @return AGV配置，如果AGV不存在则返回空集合
     */
    public Map<String, String> getAgvLink(String agvCode) {
        return getAgvLink().get(agvCode);
    }

    /**
     * 获取所有充电桩配置
     *
     * @return 所有充电桩集合，如果没有充电桩则返回空集合
     */
    public Map<String, Map<String, String>> getChargeLink() {
        LinkConfig current = config;
        if (current != null && current.getChargeLink() != null && !current.getChargeLink().getLinks().isEmpty()) {
            return current.getChargeLink().getLinks();
        }
        return new HashMap<>(0);
    }

    /**
     * 获取指定充电桩配置
     *
     * @param chargeCode 充电桩编号
     * @return 充电桩配置，如果充电桩不存在则返回空集合
     */
    public Map<String, String> getChargeLink(String chargeCode) {
        return getChargeLink().get(chargeCode);
    }

    /**
     * 获取中转系统链接配置
     *
     * @return 所有中转系统配置，如果没有中转系统则返回空集合
     */
    public Map<String, String> getTransferLink() {
        LinkConfig current = config;
        if (current != null && current.getTransferLink() != null && !current.getTransferLink().getLink().isEmpty()) {
            return current.getTransferLink().getLink();
        }
        return new HashMap<>(0);
    }
}
