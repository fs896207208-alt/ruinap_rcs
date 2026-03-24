package com.ruinap.core.equipment.manager;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.json.JSONObject;
import com.ruinap.adapter.communicate.NettyManager;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.enums.PointOccupyTypeEnum;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.infra.command.transfer.TransferCommand;
import com.ruinap.infra.config.InteractionYaml;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.config.pojo.interactions.HandoverDeviceEntity;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.equipment.HandoverStateEnum;
import com.ruinap.infra.enums.netty.LinkEquipmentTypeEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 交接设备管理器 (HandoverDeviceManager)
 * <p>
 * 统一管理所有与 AGV 进行货物交接(Handover)交互的外部设备。
 * <p>
 * 核心机制：
 * 1. 懒加载：首次交互时自动建立监控。
 * 2. 自动续期：只要有业务调用(get/set)，租约自动延长。
 * 3. 自动释放：指定时间内无交互，自动停止后台轮询，释放资源。
 * 4. 线程安全：使用 Hutool TimedCache + AtomicReference 实现无锁高并发。
 *
 * @author qianye
 * @create 2026-01-29 16:37
 */
@Component
public class HandoverDeviceManager {
    @Autowired
    private InteractionYaml interactionYaml;
    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private NettyManager nettyManager;
    @Autowired
    private AlarmManager alarmManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private VthreadPool vThreadPool;

    /**
     * 取货完成设备
     */
    private final Map<String, RcsPoint> loadFinishMap = new ConcurrentHashMap<>();
    /**
     * 放货完成设备
     */
    private final Map<String, RcsPoint> unloadFinishMap = new ConcurrentHashMap<>();

    /**
     * 交接状态缓存
     * Key: 设备关联点位
     * Value: 状态枚举 (AtomicReference 包装，确保更新值时不重置缓存过期时间)
     */
    private TimedCache<RcsPoint, AtomicReference<HandoverStateEnum>> handoverStateCache;

    /**
     * 超时时间 (毫秒)，60秒无访问则停止监控
     */
    private static final long LEASE_TIMEOUT = 60 * 1000L;

    @PostConstruct
    public void init() {
        // 1. 初始化状态缓存
        handoverStateCache = CacheUtil.newTimedCache(LEASE_TIMEOUT);
        handoverStateCache.schedulePrune(5000);
        handoverStateCache.setListener((key, value) -> {
            RcsLog.consoleLog.info("交接设备[{}]缓存过期，停止后台轮询", key);
        });

        // 2. 注册状态轮询线程 (FixedDelay 模式)
        // 初始延迟 20000ms，执行间隔 1000ms
        vThreadPool.scheduleWithFixedDelay(
                this::refreshActiveDevices,
                20000,
                1000,
                TimeUnit.MILLISECONDS
        );

        RcsLog.consoleLog.info("HandoverDeviceManager 初始化完成，后台轮询已启动");
    }

    /**
     * 内部任务：刷新活跃设备状态
     */
    private void refreshActiveDevices() {
        if (handoverStateCache.isEmpty()) {
            return;
        }

        Set<RcsPoint> keys = handoverStateCache.keySet();
        for (RcsPoint rcsPoint : keys) {
            try {
                // 静默读取，不续期
                AtomicReference<HandoverStateEnum> ref = handoverStateCache.get(rcsPoint, false);
                if (ref != null) {
                    int state = 0;
                    //获取指定的交接设备配置
                    HandoverDeviceEntity handoverDevice = interactionYaml.getHandoverDeviceByRelevancyPoint(rcsPoint);
                    if (handoverDevice != null) {
                        //获取交接设备编号
                        String code = handoverDevice.getCode();
                        //获取中转系统的类型
                        String equipmentType = linkYaml.getTransferLink().get("equipment_type");
                        //获取中转系统的编号
                        String equipmentCode = linkYaml.getTransferLink().get("code");
                        //获取标记
                        Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
                        ByteBuf byteBuf = Unpooled.buffer();
                        //获取交接设备状态
                        TransferCommand.writeGetHandoverDeviceState(byteBuf, code, rcsPoint.toString(), clientCounter);
                        //发送指令
                        CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
                        try {
                            // 获取返回结果 最多等待3秒
                            JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
                            //获取状态
                            state = resultJson.getInt("data", 0);
                            HandoverStateEnum handoverStateEnum = HandoverStateEnum.fromEnum(state);
                            ref.set(handoverStateEnum);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        RcsLog.consoleLog.error("[{}] 查询不到交接设备，请检查配置文件", rcsPoint);
                        RcsLog.algorithmLog.error("[{}] 查询不到交接设备，请检查配置文件", rcsPoint);
                    }
                }
            } catch (Exception e) {
                RcsLog.algorithmLog.warn("设备点位[{}]状态刷新异常: {}", rcsPoint, e.getMessage());
            }
        }
    }

    /**
     * 获取交接设备状态
     *
     * @param taskPoint 任务起点 / 终点
     * @return 状态
     */
    public HandoverStateEnum getHandoverDeviceState(String taskPoint) {
        RcsPoint pointByAlias = mapManager.getPointByAlias(taskPoint);
        if (pointByAlias == null) {
            RcsLog.consoleLog.error("点位别名[{}]获取不到点位数据，请检查配置文件", taskPoint);
            RcsLog.algorithmLog.error("点位别名[{}]获取不到点位数据，请检查配置文件", taskPoint);
            return null;
        }
        //获取指定数据，并续期
        AtomicReference<HandoverStateEnum> ref = handoverStateCache.get(pointByAlias);
        if (ref == null) {
            RcsLog.consoleLog.error("交接设备[{}]不存在，请检查配置文件", taskPoint);
            RcsLog.algorithmLog.error("交接设备[{}]不存在，请检查配置文件", taskPoint);
            return null;
        }
        return ref.get();
    }

    /**
     * 设置交接设备取货中
     *
     * @param taskPoint 任务起点 / 终点
     * @return 结果，true成功 false失败
     */
    public Boolean setHandoverDeviceLoad(String taskPoint) {
        boolean flag = false;
        //获取指定的交接设备配置
        HandoverDeviceEntity HandoverDevice = interactionYaml.getHandoverDeviceByRelevancyCode(taskPoint);
        if (HandoverDevice != null) {
            //获取交接设备编号
            String code = HandoverDevice.getCode();
            //获取状态
            HandoverStateEnum HandoverDeviceState = getHandoverDeviceState(taskPoint);
            if (HandoverStateEnum.isEnumByCode(HandoverDeviceState, HandoverStateEnum.ALLOW_PICKUP.code)) {
                //获取中转系统的类型
                String equipmentType = linkYaml.getTransferLink().get("equipment_type");
                //获取中转系统的编号
                String equipmentCode = linkYaml.getTransferLink().get("code");
                //获取标记
                Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
                ByteBuf byteBuf = Unpooled.buffer();
                //获取指令
                TransferCommand.writeSetLoad(byteBuf, code, taskPoint, clientCounter);
                //发送指令
                CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
                try {
                    // 获取返回结果
                    JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
                    flag = resultJson.getBool("data", false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                RcsLog.consoleLog.error("交接设备[{}]状态不是允许取货，请检查交接设备状态", taskPoint);
                alarmManager.triggerAlarm(code, AlarmCodeEnum.E12001, taskPoint, "rcs");
            }
        } else {
            RcsLog.consoleLog.error("交接设备[{}]不存在，请检查配置文件", taskPoint);
        }

        return flag;
    }

    /**
     * 设置交接设备放货中
     *
     * @param taskPoint 任务起点 / 终点
     * @return 结果，true成功 false失败
     */
    public Boolean setHandoverDeviceUnLoad(String taskPoint) {
        boolean flag = false;
        //获取指定的交接设备配置
        HandoverDeviceEntity HandoverDevice = interactionYaml.getHandoverDeviceByRelevancyCode(taskPoint);
        if (HandoverDevice != null) {
            //获取交接设备编号
            String code = HandoverDevice.getCode();
            //获取状态
            HandoverStateEnum HandoverDeviceState = getHandoverDeviceState(taskPoint);
            if (HandoverStateEnum.isEnumByCode(HandoverDeviceState, HandoverStateEnum.ALLOW_UNLOAD.code)) {
                //获取中转系统的类型
                String equipmentType = linkYaml.getTransferLink().get("equipment_type");
                //获取中转系统的编号
                String equipmentCode = linkYaml.getTransferLink().get("code");
                //获取标记
                Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
                ByteBuf byteBuf = Unpooled.buffer();
                //获取指令
                TransferCommand.writeSetUnLoad(byteBuf, code, taskPoint, clientCounter);
                //发送指令
                CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
                try {
                    // 获取返回结果
                    JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
                    flag = resultJson.getBool("data", false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                RcsLog.consoleLog.error("交接设备[{}]状态不是允许卸货，请检查交接设备状态", taskPoint);
                alarmManager.triggerAlarm(code, AlarmCodeEnum.E12002, taskPoint, "rcs");
            }
        } else {
            RcsLog.consoleLog.error("交接设备[{}]不存在，请检查配置文件", taskPoint);
        }

        return flag;
    }

    /**
     * 添加取货完成设备
     *
     * @param taskPoint 任务起点 / 终点
     */
    public boolean addLoadFinish(RcsPoint taskPoint) {
        boolean flag = false;
        //获取指定的交接设备配置
        HandoverDeviceEntity HandoverDevice = interactionYaml.getHandoverDeviceByRelevancyCode(taskPoint.toString());
        if (HandoverDevice != null) {
            loadFinishMap.put(HandoverDevice.getCode(), taskPoint);
            flag = true;
        }
        return flag;
    }

    /**
     * 添加放货完成设备
     *
     * @param taskPoint 任务起点 / 终点
     */
    public boolean addUnLoadFinish(RcsPoint taskPoint) {
        boolean flag = false;
        //获取指定的交接设备配置
        HandoverDeviceEntity HandoverDevice = interactionYaml.getHandoverDeviceByRelevancyCode(taskPoint.toString());
        if (HandoverDevice != null) {
            unloadFinishMap.put(HandoverDevice.getCode(), taskPoint);
            flag = true;
        }
        return flag;
    }

    /**
     * 交接设备取放货完成管理
     */
    private void HandoverDeviceFinishManage() {
        //遍历取货完成设备
        for (Map.Entry<String, RcsPoint> entry : loadFinishMap.entrySet()) {
            //获取中转系统的类型
            String equipmentType = linkYaml.getTransferLink().get("equipment_type");
            //获取中转系统的编号
            String equipmentCode = linkYaml.getTransferLink().get("code");
            //获取标记
            Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
            ByteBuf byteBuf = Unpooled.buffer();
            //获取指令
            TransferCommand.writeSetLoadFinish(byteBuf, entry.getKey(), entry.getValue().toString(), clientCounter);
            //发送指令
            CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
            try {
                // 获取返回结果
                JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
                boolean flag = resultJson.getBool("data", false);
                if (flag) {
                    RcsLog.consoleLog.info("向设备[{}]发送取货完成", entry.getKey());
                    loadFinishMap.remove(entry.getKey());
                    //获取占用点位
                    RcsPointOccupy rcsOccupy = mapManager.getRcsOccupy(entry.getValue());
                    if (rcsOccupy == null) {
                        continue;
                    }
                    //判断是否被设备占用
                    if (!rcsOccupy.containsType(PointOccupyTypeEnum.EQUIPMENT)) {
                        //将点位占用
                        mapManager.addOccupyType(entry.getKey(), entry.getValue(), PointOccupyTypeEnum.EQUIPMENT);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        //遍历放货完成设备
        for (Map.Entry<String, RcsPoint> entry : unloadFinishMap.entrySet()) {
            //获取中转系统的类型
            String equipmentType = linkYaml.getTransferLink().get("equipment_type");
            //获取中转系统的编号
            String equipmentCode = linkYaml.getTransferLink().get("code");
            //获取标记
            Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
            ByteBuf byteBuf = Unpooled.buffer();
            //获取指令
            TransferCommand.writeSetUnLoadFinish(byteBuf, entry.getKey(), entry.getValue().toString(), clientCounter);
            //发送指令
            CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
            try {
                // 获取返回结果
                JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
                boolean flag = resultJson.getBool("data", false);
                if (flag) {
                    RcsLog.consoleLog.info("向设备[{}]发送放货完成", entry.getKey());
                    unloadFinishMap.remove(entry.getKey());
                    //获取占用点位
                    RcsPointOccupy rcsOccupy = mapManager.getRcsOccupy(entry.getValue());
                    if (rcsOccupy == null) {
                        continue;
                    }
                    //判断是否被设备占用
                    if (!rcsOccupy.containsType(PointOccupyTypeEnum.EQUIPMENT)) {
                        //将点位占用
                        mapManager.addOccupyType(entry.getKey(), entry.getValue(), PointOccupyTypeEnum.EQUIPMENT);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 取货取消
     *
     * @param origin 任务起点
     * @return 结果，true成功 false失败
     */
    public Boolean cancelLoad(String origin) {
        boolean flag = false;
        //获取指定的交接设备配置
        HandoverDeviceEntity HandoverDevice = interactionYaml.getHandoverDeviceByRelevancyCode(origin);
        if (HandoverDevice == null) {
            throw new RuntimeException("交接设备关联点位[" + origin + "]获取不到配置，请检查配置文件");
        }
        //获取中转系统的类型
        String equipmentType = linkYaml.getTransferLink().get("equipment_type");
        //获取中转系统的编号
        String equipmentCode = linkYaml.getTransferLink().get("code");
        //获取标记
        Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
        ByteBuf byteBuf = Unpooled.buffer();
        //获取指令
        TransferCommand.writeCancelLoad(byteBuf, HandoverDevice.getCode(), origin, clientCounter);
        //发送指令
        CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
        try {
            // 获取返回结果
            JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
            flag = resultJson.getBool("data", false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return flag;
    }

    /**
     * 放货取消
     *
     * @param destin 任务终点
     */
    public Boolean cancelUnLoad(String destin) {
        boolean flag = false;
        //获取指定的交接设备配置
        HandoverDeviceEntity HandoverDevice = interactionYaml.getHandoverDeviceByRelevancyCode(destin);
        if (HandoverDevice == null) {
            throw new RuntimeException("交接设备关联点位[" + destin + "]获取不到配置，请检查配置文件");
        }
        //获取中转系统的类型
        String equipmentType = linkYaml.getTransferLink().get("equipment_type");
        //获取中转系统的编号
        String equipmentCode = linkYaml.getTransferLink().get("code");
        //获取标记
        Long clientCounter = nettyManager.getClientCounter(equipmentType, equipmentCode);
        ByteBuf byteBuf = Unpooled.buffer();
        //获取指令
        TransferCommand.writeCancelUnLoad(byteBuf, HandoverDevice.getCode(), destin, clientCounter);
        //发送指令
        CompletableFuture<JSONObject> futureState = nettyManager.sendMessage(LinkEquipmentTypeEnum.fromEquipmentType(equipmentType), equipmentCode, clientCounter, byteBuf, JSONObject.class);
        try {
            // 获取返回结果
            JSONObject resultJson = futureState.get(3, TimeUnit.SECONDS);
            flag = resultJson.getBool("data", false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return flag;
    }
}
