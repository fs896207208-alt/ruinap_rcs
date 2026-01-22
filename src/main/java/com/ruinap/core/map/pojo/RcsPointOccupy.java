package com.ruinap.core.map.pojo;

import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.infra.framework.core.event.point.RcsPointOccupyChangeEvent;
import com.ruinap.infra.framework.util.SpringContextHolder;
import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 路径占用类
 *
 * @author qianye
 * @create 2025-01-17 16:30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RcsPointOccupy implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新增 key 字段 (MapKeyUtil生成的唯一ID)
     * 把它作为对象的“身份证”，在对象创建时就注入进去。
     */
    @EqualsAndHashCode.Include
    private Long key;

    private Integer pointId;

    /**
     * 占用者集合
     */
    private final Map<String, Set<PointOccupyTypeEnum>> occupants = new ConcurrentHashMap<>();

    /**
     * 点位是否被占用 (volatile 保证读可见性)
     */
    private volatile boolean physicalBlocked = false;

    /**
     * 显式锁 (使用 RcsLock 替代 ReentrantLock)
     * transient: 序列化时不保存锁的状态，反序列化后需重新初始化
     */
    private transient RcsLock lock = RcsLock.ofReentrant();

    public RcsPointOccupy(Long key, Integer pointId) {
        this.key = key;
        this.pointId = pointId;
    }

    /**
     * 强制占用
     */
    public void setOccupied(String deviceCode, PointOccupyTypeEnum type) {
        // 写操作必须拿到锁
        lock.runInWrite(() -> setOccupiedInternal(deviceCode, type));
        // 2. 发送通知 (锁外执行，性能更好)
        publishChangeEvent(pointId, deviceCode, type, RcsPointOccupyChangeEvent.ChangeType.OCCUPIED);
    }

    /**
     * 申请占用 (核心高并发方法)
     */
    public boolean tryOccupied(String deviceCode, PointOccupyTypeEnum type) {
        // 1. 【性能关键】快排检测 (Fail-Fast)
        // 利用 volatile 的读性能（无锁）。
        if (physicalBlocked && !occupants.containsKey(deviceCode)) {
            return false;
        }

        // 2. 【防惊群】非阻塞锁竞争
        // 使用 RcsLock 的 tryRunInWrite
        // 这里的逻辑是：尝试拿锁，如果拿到了，再执行内部的双重检查逻辑

        // 用于获取 lambda 内部的执行结果
        AtomicBoolean success = new AtomicBoolean(false);

        // 尝试获取锁 (0等待，拿不到立即返回 false)
        boolean acquired = lock.tryRunInWrite(0, TimeUnit.MILLISECONDS, () -> {
            // 3. 【严谨性】拿到锁后的双重检查 (Double Check)
            boolean hasOther = occupants.entrySet().stream()
                    .anyMatch(entry -> !entry.getKey().equals(deviceCode) && !entry.getValue().isEmpty());

            if (!hasOther) {
                // 只有检查通过，才执行写入，并标记成功
                setOccupiedInternal(deviceCode, type);
                success.set(true);
            }
        });

        // 必须是“拿到了锁”且“内部逻辑执行成功”才算成功
        boolean result = acquired && success.get();
        // 3. 只有确实成功占用，才发送通知
        if (result) {
            publishChangeEvent(pointId, deviceCode, type, RcsPointOccupyChangeEvent.ChangeType.OCCUPIED);
        }
        return result;
    }

    /**
     * 释放占用
     *
     * @param deviceCode 设备编号
     * @param type       占用类型
     * @return true=确实释放了(状态发生了改变); false=原本就没锁(无事发生)
     */
    public boolean release(String deviceCode, PointOccupyTypeEnum type) {
        // 用于从 lambda 内部提取结果
        AtomicBoolean changed = new AtomicBoolean(false);

        lock.runInWrite(() -> {
            Set<PointOccupyTypeEnum> types = occupants.get(deviceCode);
            if (types != null) {
                // Set.remove 返回 true 表示元素存在且被移除
                boolean removed = types.remove(type);

                if (removed) {
                    // 标记状态已变更
                    changed.set(true);
                    // 如果该设备没锁了，清理 Map key
                    if (types.isEmpty()) {
                        occupants.remove(deviceCode);
                    }
                    // 重新计算物理阻塞位
                    recalculateBlockedStatus();
                }
            }
        });

        boolean result = changed.get();
        // 只有状态发生改变（避免重复释放发消息），才发布事件
        if (result) {
            publishChangeEvent(pointId, deviceCode, type, RcsPointOccupyChangeEvent.ChangeType.RELEASED);
        }
        return result;
    }

    /**
     * 判断当前点位是否包含指定的占用类型
     * (无需锁，利用 ConcurrentHashMap 的弱一致性，适合高频读取)
     *
     * @param type 待查询的占用类型
     * @return true 包含该类型
     */
    public boolean containsType(PointOccupyTypeEnum type) {
        // 1. 快速检查：如果没有物理阻塞（没有任何占用），直接返回 false
        // 利用 volatile 的读性能，避免遍历 Map
        if (!physicalBlocked) {
            return false;
        }

        // 2. 遍历 Values 检查
        // 虽然是 O(N)，但一个点的占用者通常极少（往往 < 5），所以性能极高
        for (Set<PointOccupyTypeEnum> types : occupants.values()) {
            if (types.contains(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前点位是否被指定设备占用
     *
     * @param deviceCode 设备编号
     * @return true=被占用 false=未占用
     */
    public boolean getDeviceOccupyState(String deviceCode) {
        Set<PointOccupyTypeEnum> types = occupants.get(deviceCode);
        return types != null && !types.isEmpty();
    }

    /**
     * 获取指定设备的占用类型集合
     *
     * @param deviceCode 设备编号
     * @return 占用类型集合 (副本或不可变视图，防止外部修改)
     */
    public Set<PointOccupyTypeEnum> getDeviceOccupyTypes(String deviceCode) {
        Set<PointOccupyTypeEnum> types = occupants.get(deviceCode);
        // JDK 10+ Set.copyOf 生成不可变集合
        return types != null ? Set.copyOf(types) : Set.of();
    }

    /**
     * 内部状态更新 (必须在锁内调用)
     */
    private void setOccupiedInternal(String deviceCode, PointOccupyTypeEnum type) {
        occupants.computeIfAbsent(deviceCode, k -> ConcurrentHashMap.newKeySet()).add(type);
        recalculateBlockedStatus();
    }

    /**
     * 更新缓存状态
     */
    private void recalculateBlockedStatus() {
        // 只要有任何占用，physicalBlocked 即为 true (严格互斥)
        this.physicalBlocked = !occupants.isEmpty();
    }

    /**
     * 自定义反序列化逻辑
     * Java 原生序列化机制会自动调用此方法
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // 1. 恢复默认数据
        in.defaultReadObject();
        // 2. 恢复锁对象 (关键！)
        this.lock = RcsLock.ofReentrant();
    }

    /**
     * 辅助方法：安全发布事件
     *
     * @param pointId    点位
     * @param deviceCode 设备编号
     * @param type       占用类型
     * @param changeType 改变类型
     */
    private void publishChangeEvent(Integer pointId, String deviceCode, PointOccupyTypeEnum type, RcsPointOccupyChangeEvent.ChangeType changeType) {
        try {
            SpringContextHolder.publishEvent(new RcsPointOccupyChangeEvent(
                    this,
                    pointId,
                    deviceCode,
                    type,
                    changeType
            ));
        } catch (Exception e) {
            // 不让事件通知的异常影响核心业务流程（如锁的释放）
            RcsLog.algorithmLog.error("事件发布异常", e);
        }
    }
}