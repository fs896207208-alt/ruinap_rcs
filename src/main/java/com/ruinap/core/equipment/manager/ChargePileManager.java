package com.ruinap.core.equipment.manager;

import com.ruinap.core.equipment.pojo.RcsChargePile;
import com.ruinap.infra.enums.charge.ChargeIdleEnum;
import com.ruinap.infra.enums.charge.ChargeStateEnum;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充电桩 设备管理
 *
 * @author qianye
 * @create 2026-01-15 14:14
 */
@Service
public class ChargePileManager {

    /**
     * 全局充电桩集合
     */
    public final Map<String, RcsChargePile> chargeCache = new ConcurrentHashMap<>();

    /**
     * 获取全部充电桩集合
     *
     * @return 全部充电桩集合
     */
    public Map<String, RcsChargePile> getRcsChargePileMap() {
        return chargeCache;
    }

    /**
     * 根据充电桩编号获取对应的 RcsChargePile 对象
     *
     * @param code 充电桩编号
     * @return 充电桩对象，如果找不到则返回null
     */
    public RcsChargePile getRcsChargePileByCode(String code) {
        if (code == null) {
            return null;
        }
        return getRcsChargePileMap().get(code);
    }

    /**
     * 获取指定充电桩是否在线
     *
     * @param chargePile 充电桩对象
     * @return 在线状态，true：在线，false：离线
     */
    public static boolean getRcsChargePileByOnline(RcsChargePile chargePile) {
        return !ChargeStateEnum.isEnumByCode(ChargeStateEnum.OFFLINE, chargePile.getState());
    }

    /**
     * 获取指定充电桩对象的隔离状态
     *
     * @param chargePile 充电桩对象
     * @return 隔离状态 0未隔离 1在线隔离 2离线隔离
     */
    public static Integer getRcsChargePileByIsolation(RcsChargePile chargePile) {
        return chargePile.getIsolationState();
    }

    /**
     * 获取指定空闲充电桩
     *
     * @param code 充电桩编号
     * @return 空闲充电桩，如果找不到则返回null
     */
    public RcsChargePile getRcsChargePileIdle(String code) {
        RcsChargePile chargePile = getRcsChargePileByCode(code);
        if (chargePile == null) {
            return null;
        }
        return getRcsChargePileByIdle(chargePile);
    }

    /**
     * 获取指定空闲充电桩
     *
     * @param chargePile 充电桩对象
     * @return 空闲充电桩，如果找不到则返回null
     */
    public RcsChargePile getRcsChargePileByIdle(RcsChargePile chargePile) {
        String code = chargePile.getCode();
        //判断充电桩是否在线
        boolean online = getRcsChargePileByOnline(chargePile);
        if (!online) {
            RcsLog.algorithmLog.warn("{} 充电桩状态[{}]，当前无法获取空闲充电桩信息", code, chargePile.getState());
            return null;
        }

        //判断充电桩是否是隔离
        //隔离状态 0未隔离 1在线隔离 2离线隔离
        Integer isolation = getRcsChargePileByIsolation(chargePile);
        if (isolation == null || !isolation.equals(0)) {
            RcsLog.algorithmLog.warn("{} 充电桩隔离状态[{}]，当前无法获取空闲充电桩信息", code, chargePile.getIsolationState());
            return null;
        }

        //判断充电桩是否空闲
        if (ChargeIdleEnum.isEnumByCode(ChargeIdleEnum.IDLE, chargePile.getIdleState())) {
            return chargePile;
        } else {
            RcsLog.algorithmLog.warn("{} 请检查充电桩的状态，当前无法获取空闲充电桩信息", code);
            return null;
        }
    }

    /**
     * 获取空闲充电桩集合
     *
     * @return 空闲充电桩集合
     */
    public Map<String, RcsChargePile> getIdleRcsChargePileMap() {
        Map<String, RcsChargePile> tempMap = new ConcurrentHashMap<>(getRcsChargePileMap().size());
        for (Map.Entry<String, RcsChargePile> entry : getRcsChargePileMap().entrySet()) {
            //获取充电桩
            RcsChargePile chargePile = entry.getValue();
            //判断充电桩是否是空闲
            if (getRcsChargePileByIdle(chargePile) != null) {
                tempMap.put(chargePile.getCode(), chargePile);
            }
        }
        return tempMap;
    }
}
