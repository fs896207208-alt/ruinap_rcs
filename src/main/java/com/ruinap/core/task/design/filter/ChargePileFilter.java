package com.ruinap.core.task.design.filter;

import cn.hutool.core.date.DateUtil;
import com.ruinap.core.business.AlarmManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.manager.ChargePileManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.equipment.pojo.RcsChargePile;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.enums.alarm.AlarmCodeEnum;
import com.ruinap.infra.enums.charge.ChargeIdleEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;

import java.util.*;

/**
 * 充电桩过滤器
 *
 * @author qianye
 * @create 2024-06-17 14:53
 */
@Component
public class ChargePileFilter {

    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private ChargePileManager chargePileManager;
    @Autowired
    private AlarmManager alarmManager;

    /**
     * 距离优先匹配
     *
     * @param rcsAgv         AGV
     * @param chargePileList 充电桩列表
     * @return 最近的充电桩
     */
    public RcsChargePile distanceFirst(RcsAgv rcsAgv, List<RcsChargePile> chargePileList) {
        RcsChargePile returnCharge = null;
        Integer tempCost = Integer.MAX_VALUE;
        //获取AGV的点位
        RcsPoint rcsPoint = mapManager.getRcsPoint(rcsAgv.getMapId(), rcsAgv.getPointId());
        if (rcsPoint == null) {
            RcsLog.algorithmLog.error("{} AGV坐标不存在任何点位上", rcsAgv.getAgvId());
            return returnCharge;
        }
        for (RcsChargePile value : chargePileList) {
            //状态 0离线 1在线
            Integer state = value.getState();
            //隔离状态 0未隔离 1在线隔离 2离线隔离
            Integer isolationState = value.getIsolationState();
            //空闲状态 -1未知 0空闲 1占用
            Integer idleState = value.getIdleState();
            //匹配AGV类型 0通用 1潜伏式 2叉车式
            Integer matchType = value.getMatchType();
            if (state.equals(1) && isolationState.equals(0) && ChargeIdleEnum.isEnumByCode(ChargeIdleEnum.IDLE, idleState)) {
                //获取充电桩的点位
                RcsPoint chargePoint = mapManager.getPointByAlias(value.getPointId());
                if (chargePoint == null) {
                    RcsLog.consoleLog.info("{} 充电桩[{}]绑定的点位[{}]查询不到", rcsAgv.getAgvId(), value.getCode(), value.getPointId());
                    RcsLog.algorithmLog.info("{} 充电桩[{}]绑定的点位[{}]查询不到", rcsAgv.getAgvId(), value.getCode(), value.getPointId());
                    alarmManager.triggerAlarm(rcsAgv.getAgvId(), AlarmCodeEnum.E12003, value.getCode(), "rcs");
                    continue;
                }

                //获取点位占用
                if (mapManager.getPointOccupyState(chargePoint)) {
                    Set<RcsPointOccupy> deviceOccupiedPoints = mapManager.getDeviceOccupiedPoints(rcsAgv.getAgvId());
                    if (deviceOccupiedPoints.isEmpty()) {
                        RcsLog.algorithmLog.info("{} 充电桩[{}]已经被占用，跳过该充电桩", rcsAgv.getAgvId(), value.getCode());
                        continue;
                    }
                }

                //判断当前AGV类型是否可接取任务
                if (matchType.equals(0) || rcsAgv.getAgvType().equals(matchType)) {
                    // 计算距离
                    int distance = GeometryUtils.calculateDistance(rcsPoint, chargePoint);
                    if (tempCost.compareTo(distance) > 0) {
                        tempCost = distance;
                        returnCharge = value;
                    }
                } else {
                    RcsLog.algorithmLog.error("{} 充电桩[{}]分配失败，充电桩匹配类型与AGV类型不匹配", rcsAgv.getAgvId(), value.getCode());
                }
            }
        }

        return returnCharge;
    }

    /**
     * 根据距离过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByDistance(RcsAgv rcsAgv) {
        //调用距离优先匹配方法

        return distanceFirst(rcsAgv, chargePileManager.getIdleRcsChargePileMap().values().stream().toList());
    }

    /**
     * 根据绑定数据过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByBinding(RcsAgv rcsAgv) {
        Set<RcsChargePile> chargePileSet = new HashSet<>();
        // 获取配置文件数据
        Map<String, String> chargeBindingAgvMap = taskYaml.getChargeCommon().getChargeBindingAgv();
        for (Map.Entry<String, String> entry : chargeBindingAgvMap.entrySet()) {
            // 获取绑定的AGV字符串
            List<String> agvs = Arrays.asList(entry.getValue().split(","));
            // 判断AGV是否存在
            if (agvs.contains(rcsAgv.getAgvId())) {
                //充电桩编号
                String chargePileId = entry.getKey();
                //从数据库获取充电桩信息
                RcsChargePile rcsChargePile = chargePileManager.getRcsChargePileIdle(chargePileId);
                //判断充电桩不是空并且充电桩是否空闲
                if (rcsChargePile != null) {
                    chargePileSet.add(rcsChargePile);
                }
            }
        }

        if (chargePileSet.isEmpty()) {
            return null;
        } else {
            //获取最近的充电桩
            return distanceFirst(rcsAgv, chargePileSet.stream().toList());
        }
    }

    /**
     * 根据类型过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByType(RcsAgv rcsAgv) {
        List<RcsChargePile> chargePileList = new ArrayList<>();
        //AGV类型
        Integer agvType = rcsAgv.getAgvType();
        //遍历充电桩列表
        for (RcsChargePile chargePile : chargePileManager.getIdleRcsChargePileMap().values()) {
            //匹配类型
            Integer matchType = chargePile.getMatchType();
            if (agvType.equals(matchType)) {
                chargePileList.add(chargePile);
            }
        }

        if (chargePileList.isEmpty()) {
            return null;
        } else {
            //获取最近的充电桩
            return distanceFirst(rcsAgv, chargePileList);
        }
    }

    /**
     * 根据区域过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByArea(RcsAgv rcsAgv) {
        Set<RcsChargePile> chargePileSet = new HashSet<>();
        // 获取配置文件数据
        Map<String, String> chargePileArea = taskYaml.getChargeCommon().getChargePileArea();
        for (Map.Entry<String, String> entry : chargePileArea.entrySet()) {
            // 获取绑定的AGV字符串
            List<String> agvs = Arrays.asList(entry.getValue().split(","));
            // 判断AGV是否存在
            if (agvs.contains(rcsAgv.getAgvId())) {
                //充电桩编号
                String chargePileId = entry.getKey();
                //从数据库获取充电桩信息
                RcsChargePile rcsChargePile = chargePileManager.getRcsChargePileIdle(chargePileId);
                //判断充电桩不是空并且充电桩是否空闲
                if (rcsChargePile != null) {
                    chargePileSet.add(rcsChargePile);
                }
            }
        }

        if (chargePileSet.isEmpty()) {
            return null;
        } else {
            //获取最近的充电桩
            return distanceFirst(rcsAgv, chargePileSet.stream().toList());
        }
    }

    /**
     * 根据楼层过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByFloor(RcsAgv rcsAgv) {
        Set<RcsChargePile> chargePileSet = new HashSet<>();
        // 获取配置文件数据
        Map<String, String> chargePileFloor = taskYaml.getChargeCommon().getChargePileFloor();
        for (Map.Entry<String, String> entry : chargePileFloor.entrySet()) {
            // 获取绑定的AGV字符串
            List<String> agvs = Arrays.asList(entry.getValue().split(","));
            // 判断AGV是否存在
            if (agvs.contains(rcsAgv.getAgvId())) {
                //充电桩编号
                String chargePileId = entry.getKey();
                //从数据库获取充电桩信息
                RcsChargePile rcsChargePile = chargePileManager.getRcsChargePileIdle(chargePileId);
                //判断充电桩不是空并且充电桩是否空闲
                if (rcsChargePile != null) {
                    chargePileSet.add(rcsChargePile);
                }
            }
        }

        if (chargePileSet.isEmpty()) {
            return null;
        } else {
            //获取最近的充电桩
            return distanceFirst(rcsAgv, chargePileSet.stream().toList());
        }
    }

    /**
     * 根据时间段过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByTimes(RcsAgv rcsAgv) {
        Set<RcsChargePile> chargePileSet = new HashSet<>();

        //判断是否在指定时间段内
        boolean inChargeTime = false;
        // 使用Hutool的DateUtil获取小时
        int currentHour = DateUtil.hour(new Date(), true);

        // 遍历map中的时间段
        for (Map.Entry<Integer, Integer> entry : taskYaml.getChargeCommon().getChargeTimesAgv().entrySet()) {
            int startHour = entry.getKey();
            int endHour = entry.getValue();
            if (currentHour >= startHour && currentHour < endHour) {
                inChargeTime = true;
                break;
            }
        }

        if (inChargeTime) {
            //获取AGV的空闲状态
            RcsAgv rcsAgvIdle = agvManager.getRcsAgvIdle(rcsAgv);
            if (rcsAgvIdle != null) {
                //遍历充电桩列表
                chargePileSet.addAll(chargePileManager.getIdleRcsChargePileMap().values());
            }
        }

        if (chargePileSet.isEmpty()) {
            return null;
        } else {
            //获取最近的充电桩
            return distanceFirst(rcsAgv, chargePileSet.stream().toList());
        }
    }

    /**
     * 根据自定义过滤充电桩
     *
     * @param rcsAgv AGV
     * @return 距离最近的充电桩
     */
    public RcsChargePile filterChargePileByCustomization(RcsAgv rcsAgv) {
        RcsChargePile rcsChargePile = filterChargePileByTimes(rcsAgv);
        return rcsChargePile != null ? rcsChargePile : filterChargePileByDistance(rcsAgv);
    }
}
