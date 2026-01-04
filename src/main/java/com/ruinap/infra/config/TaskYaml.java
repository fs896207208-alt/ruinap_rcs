package com.ruinap.infra.config;

import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.config.pojo.TaskConfig;
import com.ruinap.infra.config.pojo.task.ChargeCommonEntity;
import com.ruinap.infra.config.pojo.task.StandbyCommonEntity;
import com.ruinap.infra.config.pojo.task.TaskCommonEntity;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.core.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * 调度任务Yaml配置文件
 *
 * @author qianye
 * @create 2024-10-30 16:19
 */
@Component
public class TaskYaml implements ReloadableConfig {
    /**
     * 调度任务配置 (使用 volatile 确保多线程可见性)
     */
    private volatile TaskConfig config;
    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private Environment environment;

    /**
     * 初始化调度任务Yaml配置文件
     * 注意：请让配置文件尽早初始化，避免影响使用到配置文件的其他模块的初始化
     */
    @PostConstruct
    public void initialize() {
        bindInternal();
    }

    private void bindInternal() {
        this.config = environment.bind("rcs_task", TaskConfig.class);
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
        return "rcs_task";
    }

    /**
     * 获取 任务分配模式
     * <p>
     * 如果配置文件中没有配置，则返回默认值 0
     *
     * @return 任务分配模式
     */
    public Integer getTaskDistributeMode() {
        // 获取引用快照
        TaskConfig current = config;
        if (current == null || current.getTaskCommon() == null) {
            return 0;
        }
        Integer taskDistributeMode = current.getTaskCommon().getTaskDistributeMode();
        return taskDistributeMode == null ? 0 : taskDistributeMode;
    }

    /**
     * 获取 点位别名
     * <p>
     * 如果配置文件中没有配置，则返回默认值 空集合
     *
     * @return 点位别名
     */
    public Map<String, String> getTaskPiontAlias() {
        // 获取引用快照
        TaskConfig current = config;
        if (current == null || current.getRegexCommon() == null) {
            return new HashMap<>(0);
        }
        Map<String, String> taskPointAlias = current.getRegexCommon().getTaskPointAlias();
        return taskPointAlias.isEmpty() ? new HashMap<>() : taskPointAlias;
    }

    /**
     * 获取 起点动作参数
     * <p>
     * 如果配置文件中没有配置，则返回默认值 空集合
     *
     * @return 起点动作参数
     */
    public Map<String, String> getOriginActionParameter() {
        TaskConfig current = config;
        if (current == null || current.getRegexCommon() == null) {
            return new HashMap<>(0);
        }
        Map<String, String> originActionParameter = current.getRegexCommon().getOriginActionParameter();
        if (originActionParameter == null || originActionParameter.isEmpty()) {
            originActionParameter = new HashMap<>(0);
        }
        return originActionParameter;
    }

    /**
     * 获取 终点动作参数
     * <p>
     * 如果配置文件中没有配置，则返回默认值 空集合
     *
     * @return 终点动作参数
     */
    public Map<String, String> getDestinActionParameter() {
        TaskConfig current = config;
        if (current == null || current.getRegexCommon() == null) {
            return new HashMap<>(0);
        }
        Map<String, String> destinActionParameter = current.getRegexCommon().getDestinActionParameter();
        if (destinActionParameter == null || destinActionParameter.isEmpty()) {
            destinActionParameter = new HashMap<>(0);
        }
        return destinActionParameter;
    }

    /**
     * 获取充电配置
     *
     * @return 充电配置
     */
    public ChargeCommonEntity getChargeCommon() {
        TaskConfig current = config;
        return current.getChargeCommon();
    }

    /**
     * 获取待命配置
     *
     * @return 待命配置
     */
    public StandbyCommonEntity getStandbyCommon() {
        TaskConfig current = config;
        return current.getStandbyCommon();
    }

    /**
     * 获取任务配置
     *
     * @return 任务配置
     */
    public TaskCommonEntity getTaskCommon() {
        TaskConfig current = config;
        return current.getTaskCommon();
    }
}
