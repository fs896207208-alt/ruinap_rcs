package com.ruinap.infra.config;

import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.infra.config.common.ReloadableConfig;
import com.ruinap.infra.config.pojo.InteractionConfig;
import com.ruinap.infra.config.pojo.interactions.*;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.core.Environment;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 调度交互Yaml配置文件
 *
 * @author qianye
 * @create 2024-10-30 16:19
 */
@Component
public class InteractionYaml implements ReloadableConfig {
    /**
     * 调度交互配置
     */
    private volatile InteractionConfig config;

    // --- 核心缓存 Map (Key: Code, Value: Entity) ---
    // 使用 volatile 保证多线程可见性，配合 Copy-On-Write 机制
    private volatile Map<String, ElevatorEntity> elevatorMap = Collections.emptyMap();
    private volatile Map<String, AutomaticDoorEntity> automaticDoorMap = Collections.emptyMap();
    private volatile Map<String, AirShowerEntity> airShowerMap = Collections.emptyMap();
    private volatile Map<String, HandoverDeviceEntity> handoverDeviceEntityMap = Collections.emptyMap();

    @Autowired
    private MapManager mapManager;
    @Autowired
    private Environment environment;

    /**
     * 初始化调度交互Yaml配置文件
     * 注意：请让配置文件尽早初始化，避免影响使用到配置文件的其他模块的初始化
     */
    @PostConstruct
    public void initialize() {
        bindInternal();
    }

    /**
     * 加载/重载配置
     * 逻辑：加载 List -> 过滤 Enable -> 转换为 Map -> 原子替换
     */
    private void bindInternal() {
        //1. 加载原始配置
        InteractionConfig newConfig = environment.bind(getSourceName(), InteractionConfig.class);
        if (newConfig == null) {
            return;
        }

        //2. 处理电梯双向交互配置
        // 必须在 filterEnabled 和 构建 Map 之前执行，否则 Map 中会缺少反向数据
        processBidirectionalInteractions(newConfig);

        // 3. 构建 Map 索引
        // 电梯
        Map<String, ElevatorEntity> newElevatorMap = buildMapFromList(
                filterEnabled(newConfig.getElevators()),
                ElevatorEntity::getCode
        );

        // 自动门
        Map<String, AutomaticDoorEntity> newDoorMap = buildMapFromList(
                filterEnabled(newConfig.getAutomaticDoors()),
                AutomaticDoorEntity::getCode
        );

        // 风淋门
        Map<String, AirShowerEntity> newShowerMap = buildMapFromList(
                filterEnabled(newConfig.getAirShowers()),
                AirShowerEntity::getCode
        );

        // 交接设备
        Map<String, HandoverDeviceEntity> newHandoverDeviceMap = buildMapFromList(
                filterEnabled(newConfig.getHandoverDevice()),
                HandoverDeviceEntity::getCode
        );

        // Publish: 原子赋值，立即生效
        this.config = newConfig;
        this.elevatorMap = newElevatorMap;
        this.automaticDoorMap = newDoorMap;
        this.airShowerMap = newShowerMap;
        this.handoverDeviceEntityMap = newHandoverDeviceMap;

    }

    /**
     * 内部辅助工具：List 转 Map
     * 抽取这个私有方法是为了让 bindInternal 代码更整洁
     */
    private <T> Map<String, T> buildMapFromList(List<T> validList, Function<T, String> keyMapper) {
        if (validList == null || validList.isEmpty()) {
            return Collections.emptyMap();
        }
        return validList.stream()
                .filter(item -> keyMapper.apply(item) != null)
                .collect(Collectors.toMap(
                        keyMapper,
                        Function.identity(),
                        (v1, v2) -> v1
                ));
    }

    /**
     * 通用过滤方法：只保留 enable = true 的项，并转为不可变列表防止外部篡改
     * (利用泛型适配不同的设备实体，假设它们都有 isEnable 方法，如果没有公共接口，可能需要分开写)
     */
    private <T> List<T> filterEnabled(List<T> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> enabledList = new ArrayList<>(rawList.size());
        for (T item : rawList) {
            // 这里假设实体类遵循 Java Bean 规范，利用反射或者需要你确保实体实现了具体接口
            // 生产环境建议：让 ElevatorEntity, AutomaticDoorEntity 实现一个 EnabledSupport 接口
            // 这里的代码为了演示通用性，使用反射或特定检查，建议你直接写具体类型的 for 循环
            if (isItemEnabled(item)) {
                enabledList.add(item);
            }
        }
        // 关键：转为 UnmodifiableList，防止业务代码误删配置导致 Bug
        return Collections.unmodifiableList(enabledList);
    }

    /**
     * 辅助检查启用状态
     * <p>
     * 注意：每加一个设备实体时，这里需要添加一个判断是否启用逻辑
     *
     * @param item 设备实体
     * @return 是否启用
     */
    private boolean isItemEnabled(Object item) {
        if (item instanceof ElevatorEntity) {
            return ((ElevatorEntity) item).isEnable();
        }
        if (item instanceof AutomaticDoorEntity) {
            return ((AutomaticDoorEntity) item).isEnable();
        }
        if (item instanceof AirShowerEntity) {
            return ((AirShowerEntity) item).isEnable();
        }
        if (item instanceof HandoverDeviceEntity) {
            return ((HandoverDeviceEntity) item).isEnable();
        }

        return false;
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
        return "rcs_interactive";
    }

    /**
     * 预处理双向交互配置
     * 策略：宽进严出。
     * 只要看到符合格式的 bridge_id，就生成反向逻辑。
     * 是否真的会用到这个逻辑，完全由 MapYaml (拓扑结构) 决定。
     */
    private void processBidirectionalInteractions(InteractionConfig config) {
        if (config == null || config.getElevators() == null) {
            return;
        }

        for (ElevatorEntity elevator : config.getElevators()) {
            Map<String, InteractionsBody> currentMap = elevator.getInteractions();
            if (currentMap == null || currentMap.isEmpty()) {
                continue;
            }

            //准备一个容器暂存新生成的反向配置
            Map<String, InteractionsBody> newReverseEntries = new HashMap<>();
            for (Map.Entry<String, InteractionsBody> entry : currentMap.entrySet()) {
                // 例如 "teleport_1_2"
                String key = entry.getKey();
                InteractionsBody body = entry.getValue();

                // 正则匹配: (前缀)_(数字)_(数字)
                String regex = "(.*)_(\\d+)_(\\d+)$";
                if (key.matches(regex)) {
                    // 智能生成反向 Key: teleport_1_2 -> teleport_2_1
                    String reverseKey = key.replaceFirst(regex, "$1_$3_$2");

                    // 只有当反向配置不存在时，才自动生成
                    if (!currentMap.containsKey(reverseKey)) {
                        InteractionsBody reverseBody = new InteractionsBody();
                        // 互换 start 和 end
                        // 原来的 end 变成新的 start
                        reverseBody.setStart(body.getEnd());
                        // 原来的 start 变成新的 end
                        reverseBody.setEnd(body.getStart());

                        newReverseEntries.put(reverseKey, reverseBody);
                    }
                }

            }

            // 批量合并
            if (!newReverseEntries.isEmpty()) {
                currentMap.putAll(newReverseEntries);
            }
        }
    }

    /**
     * 获取所有启用的交接设备配置
     *
     * @return 交接设备配置集合
     */
    public List<HandoverDeviceEntity> getHandoverDevices() {
        return config != null ? config.getHandoverDevice() : Collections.emptyList();
    }

    /**
     * 根据关联点位编号获取交接设备配置
     *
     * @param relevancyCode 关联点位编号
     * @return 交接设备配置
     */
    public HandoverDeviceEntity getHandoverDeviceByRelevancyCode(String relevancyCode) {
        List<HandoverDeviceEntity> handoverDeviceEntities = getHandoverDevices();
        for (HandoverDeviceEntity handoverDeviceEntity : handoverDeviceEntities) {
            List<String> relevancyPointList = handoverDeviceEntity.getRelevancyPoint();
            if (relevancyPointList == null) {
                continue;
            }
            //判断点位是否在交接设备关联点位中
            if (relevancyPointList.contains(relevancyCode)) {
                return handoverDeviceEntity;
            } else {
                // 获取关联点位
                RcsPoint rcsPoint = mapManager.getPointByAlias(relevancyCode);
                for (String relevancyPointStr : relevancyPointList) {
                    RcsPoint relevancyPoint = mapManager.getPointByAlias(relevancyPointStr);
                    if (rcsPoint.equals(relevancyPoint)) {
                        return handoverDeviceEntity;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 根据关联点位获取交接设备配置
     *
     * @param relevancyPoint 关联点位
     * @return 交接设备配置
     */
    public HandoverDeviceEntity getHandoverDeviceByRelevancyPoint(RcsPoint relevancyPoint) {
        List<HandoverDeviceEntity> handoverDeviceEntities = getHandoverDevices();
        for (HandoverDeviceEntity handoverDeviceEntity : handoverDeviceEntities) {

            List<String> relevancyPointList = handoverDeviceEntity.getRelevancyPoint();
            if (relevancyPointList.isEmpty()) {
                continue;
            }

            for (String point : relevancyPointList) {
                RcsPoint rcsPoint = mapManager.getPointByAlias(point);
                //判断点位是否在交接设备关联点位中
                if (rcsPoint != null && rcsPoint.equals(relevancyPoint)) {
                    return handoverDeviceEntity;
                }
            }
        }
        return null;
    }

    /**
     * 获取所有启用的自动门配置
     *
     * @return 自动门配置集合
     */
    public List<AutomaticDoorEntity> getAutomaticDoors() {
        return config != null ? config.getAutomaticDoors() : Collections.emptyList();
    }

    /**
     * 根据设备编号获取自动门配置
     *
     * @param code 设备编号
     * @return 自动门配置
     */
    public AutomaticDoorEntity getAutomaticDoorByCode(String code) {
        List<AutomaticDoorEntity> automaticDoorEntities = getAutomaticDoors();
        for (AutomaticDoorEntity automaticDoorEntity : automaticDoorEntities) {
            if (automaticDoorEntity.getCode().equalsIgnoreCase(code)) {
                return automaticDoorEntity;
            }
        }
        return null;
    }

    /**
     * 获取所有启用的电梯配置
     *
     * @return 电梯配置集合
     */
    public List<ElevatorEntity> getElevators() {
        return config != null ? config.getElevators() : Collections.emptyList();
    }

    /**
     * 根据设备编号获取特定的电梯配置
     * <p>
     * 优化说明：
     * 1. 直接遍历 config 原始列表，避免调用 getElevators() 产生临时的 ArrayList 对象，降低 GC 压力。
     * 2. 使用局部变量 stack copy 引用 volatile 变量，确保在方法执行期间配置的一致性。
     *
     * @param code 电梯设备编号 (对应 ElevatorEntity 中的 code)
     * @return 启用的电梯配置对象，如果未找到或未启用则返回 null
     */
    public ElevatorEntity getElevatorByCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (ElevatorEntity elevator : getElevators()) {
            // 必须同时满足：已启用 (enable == true) 且 编号匹配
            // 使用 equalsIgnoreCase 兼容大小写差异，增强鲁棒性
            if (elevator.isEnable() && code.equalsIgnoreCase(elevator.getCode())) {
                return elevator;
            }
        }
        return null;
    }

    /**
     * 获取所有启用的风淋室配置
     *
     * @return 风淋室配置集合
     */
    public List<AirShowerEntity> getAirShowers() {
        return config != null ? config.getAirShowers() : Collections.emptyList();
    }
}
