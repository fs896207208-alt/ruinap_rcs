package com.ruinap.infra.config.common;

/**
 * 【接口】可重载配置
 * 标记一个 Bean 支持从 Environment 重新绑定数据
 *
 * @author qianye
 * @create 2025-12-24 18:02
 */
public interface ReloadableConfig {
    /**
     * 重新绑定数据（从 Environment 更新自身状态）
     * 注意：实现此方法时，不需要再调用 environment.scanAndLoad()，
     * 因为 GlobalManager 会统一做这件事。
     */
    void rebind();

    /**
     * 获取配置源名称
     *
     * @return
     */
    String getSourceName();
}
