package com.ruinap.core.business;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import com.ruinap.persistence.repository.AlarmDB;

/**
 * 告警信息管理器
 * <p>
 * 核心逻辑：
 * 1. 唯一性：EquipmentCode + AlarmCode 唯一。多源触发只视为同一个告警的持续刷新。
 * 2. 自动恢复：利用 TimedCache 监听器，超时未上报自动更新数据库为结束。
 * 3. 异步IO：所有数据库操作由虚拟线程异步执行。
 *
 * @author AGV System Engineer
 */
@Service
public class AlarmManager {

    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private AlarmDB alarmDB;

    /**
     * 告警超时时间 (毫秒)
     * <p>
     * 业务定义：如果 AGV 持续上报同一个告警（心跳），缓存会不断续期。
     * 当超过 5000ms 未收到该告警上报时，认为故障已消除，触发 onRemove 监听器自动更新 DB 状态。
     */
    private static final long ALARM_TIMEOUT_MS = 5000L;

    /**
     * 活跃告警缓存
     * <p>
     * 1. 线程安全：Hutool 的 TimedCache 底层基于 StampedCache 或 Lock，本身是线程安全的。
     * 2. 易变性：使用 volatile 确保初始化时的可见性
     * Key: AlertKey (设备号 + 告警码)
     * Value: Boolean (仅作为存在性标识，无实际业务含义)
     */
    private volatile TimedCache<AlertKey, Boolean> alarmCache;

    /**
     * 系统初始化
     * 利用 容器 生命周期，确保在对外服务前缓存已就绪。
     */
    @PostConstruct
    public void init() {
        // 创建带超时机制的缓存
        this.alarmCache = CacheUtil.newTimedCache(ALARM_TIMEOUT_MS);

        // 设置监听器：核心逻辑 -> 告警自动恢复
        // 当缓存过期（timeout）或被显式移除（remove）时触发
        this.alarmCache.setListener((key, cachedObject) -> {
            // 提交给虚拟线程池异步执行，防止阻塞 Timer 线程
            vthreadPool.execute(() -> {
                updateAlarmStatus(key.equipmentCode(), key.code());
            });
        });

        // 启动定时清理任务（每秒检查一次过期元素）
        // 注意：不调用此方法，过期元素只会在下次访问时被移除，导致 Listener 延迟触发。
        this.alarmCache.schedulePrune(1000);
        RcsLog.consoleLog.info("AlarmManager 初始化完成 (JDK21 Virtual Threads Mode)");
    }

    /**
     * 系统平滑关闭 (Graceful Shutdown)
     */
    @PreDestroy
    public void shutdown() {
        if (alarmCache == null) {
            return;
        }
        try {
            // 1. 停止定时清理任务，防止新任务产生
            alarmCache.cancelPruneSchedule();

            // 2. 移除监听器
            // 关键点：在清空缓存前必须移除监听器，否则 clear() 会触发所有告警的 onRemove，
            // 导致数据库里被错误地更新为 "state=1" (自然恢复)，而实际上应该是 "state=2" (系统关闭)。
            alarmCache.setListener(null);

            // 3. 清空内存缓存
            alarmCache.clear();

            // 4. 执行全局状态更新：将所有未结告警标记为 "系统关闭"(state=2)
            // 使用虚拟线程执行，但注意：在 Spring 销毁阶段，需确保线程池未被关闭。
            // 生产环境建议此处使用 CountDownLatch 同步等待更新完成，防止 JVM 进程直接退出。
            updateAllActiveAlarmsToSystemClosed();

            RcsLog.consoleLog.info("AlarmManager 已安全关闭，所有活跃告警已标记为 SYSTEM_CLOSED");
        } catch (Exception e) {
            RcsLog.sysLog.error("AlarmManager 关闭时发生异常", e);
        }
    }

    /**
     * 触发/上报告警
     * <p>
     * 高并发入口方法。
     *
     * @param equipmentCode 设备编号 (Required)
     * @param taskGroup     任务组 (Optional)
     * @param taskCode      任务号 (Optional)
     * @param alarmEnum     告警枚举详情 (Required)
     * @param param         动态参数 (如：电机温度=85度)
     * @param source        告警来源 (AGV/WCS/Traffic)
     */
    public void triggerAlarm(String equipmentCode, String taskGroup, String taskCode, AlarmCodeEnum alarmEnum, String param, String source) {
        // 1. 参数校验
        if (StrUtil.isBlank(equipmentCode) || alarmEnum == null) {
            return;
        }

        // 2. 状态检查 (极罕见情况：Bean初始化失败或未完成)
        if (alarmCache == null) {
            RcsLog.sysLog.error("严重错误：AlarmManager 未初始化，告警丢失！Equipment: {}", equipmentCode);
            return;
        }

        AlertKey key = new AlertKey(equipmentCode, alarmEnum.code);

        // 3. 缓存操作 (无锁化核心)
        // 逻辑说明：
        // containsKey(key) 检查是否已存在。
        // put(key, true)   无论是否存在，都放入缓存。
        //   - 如果已存在：put 会刷新最后访问时间（续期），实现“防抖”。
        //   - 如果不存在：put 会新增记录。

        // 性能权衡 (Trade-off):
        // 这里存在微小的竞态条件 (Race Condition)：
        // 线程A check false -> 线程B check false -> 线程A put -> 线程B put -> 线程A insert -> 线程B insert
        // 后果：数据库可能插入两条重复的 state=0 告警。
        // 决策：AGV 业务容忍极低概率的重复日志，但不容忍 synchronized 造成的全局阻塞。
        // 优化建议：数据库表 rcs_alarm 建议对 (name, code, state=0) 建立唯一索引，利用 DB 唯一性约束自动去重。

        boolean isExisting = alarmCache.containsKey(key);
        alarmCache.put(key, Boolean.TRUE);

        // 4. 新告警入库
        if (!isExisting) {
            // 只有当这是第一条告警时，才发起数据库 IO
            // 利用虚拟线程异步执行，主线程立即返回，不阻塞 AGV 心跳响应
            vthreadPool.execute(() -> saveAlarmToDb(equipmentCode, taskGroup, taskCode, alarmEnum, param, source));
        }
    }

    // ==================== 重载便捷方法 ====================

    /**
     * 触发/上报告警
     *
     * @param equipmentCode 设备编号
     * @param alarmEnum     告警枚举
     * @param param         告警参数
     * @param source        告警来源
     */
    public void triggerAlarm(String equipmentCode, AlarmCodeEnum alarmEnum, String param, String source) {
        triggerAlarm(equipmentCode, null, null, alarmEnum, param, source);
    }

    /**
     * 触发/上报告警
     *
     * @param equipmentCode 设备编号
     * @param alarmEnum     告警枚举
     * @param source        告警来源
     */
    public void triggerAlarm(String equipmentCode, AlarmCodeEnum alarmEnum, String source) {
        triggerAlarm(equipmentCode, null, null, alarmEnum, null, source);
    }

    /**
     * 手动清除告警
     * <p>
     * 场景：人工在界面上点击“确认清除”，或收到明确的“故障恢复”协议指令。
     */
    public void clearAlarm(String equipmentCode, AlarmCodeEnum alarmEnum) {
        if (StrUtil.isBlank(equipmentCode) || alarmCache == null || alarmEnum == null) {
            return;
        }
        // 从缓存移除 -> 触发 Listener.onRemove -> 异步更新 DB state=1
        alarmCache.remove(new AlertKey(equipmentCode, alarmEnum.code));
    }

    // ==================== 数据库原子操作 ====================
    // 均由虚拟线程执行，允许阻塞

    private void saveAlarmToDb(String equipmentCode, String taskGroup, String taskCode, AlarmCodeEnum alarm, String param, String source) {
        try {
            Entity entity = Entity.create("rcs_alarm");
            entity.set("task_group", taskGroup);
            entity.set("task_code", taskCode);
            entity.set("name", equipmentCode);
            entity.set("level", alarm.level);
            entity.set("code", alarm.code);
            entity.set("msg", alarm.msg);
            entity.set("param", param);
            entity.set("type", alarm.type);
            entity.set("report_state", alarm.report);
            entity.set("source", source);
            entity.set("create_time", System.currentTimeMillis());
            entity.set("state", 0);

            // 建议：此处可以捕获 DuplicateKeyException (如果设置了唯一索引)，忽略重复插入
            alarmDB.createAlarm(entity);
        } catch (Exception e) {
            // 记录日志，但不抛出异常中断业务流
            RcsLog.sysLog.error("告警入库失败 [{} - {}]", equipmentCode, alarm.code, e);
        }
    }

    private void updateAlarmStatus(String equipmentCode, Integer alarmCode) {
        try {
            // 1: 已结束
            Entity body = Entity.create().set("state", 1);

            // 乐观锁更新：只更新当前还处于 state=0 的记录
            Entity where = Entity.create()
                    .set("name", equipmentCode)
                    .set("state", 0);

            if (alarmCode != null) {
                where.set("code", alarmCode);
            }

            // 注意：Hutool DB 这里的 update 可能返回影响行数，可用于统计
            alarmDB.updateAlarm(body, where);
        } catch (Exception e) {
            RcsLog.sysLog.error("告警状态自动更新失败 [{} - {}]", equipmentCode, alarmCode, e);
        }
    }

    private void updateAllActiveAlarmsToSystemClosed() {
        try {
            // 停机逻辑：所有未结束的告警 (0)，全部置为系统关闭 (2)
            Entity body = Entity.create().set("state", 2);
            Entity where = Entity.create().set("state", 0);

            alarmDB.updateAlarm(body, where);
        } catch (Exception e) {
            RcsLog.sysLog.error("系统关闭告警批量更新失败", e);
        }
    }

    /**
     * 告警唯一键 Record
     * JDK 14+ 特性，天然不可变，适合作为 Map Key
     */
    private record AlertKey(String equipmentCode, Integer code) {
    }
}