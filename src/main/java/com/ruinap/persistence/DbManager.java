package com.ruinap.persistence;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import com.ruinap.core.business.AgvSuggestionManager;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.manager.ChargePileManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.equipment.pojo.RcsChargePile;
import com.ruinap.core.task.TaskManager;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.config.LinkYaml;
import com.ruinap.infra.enums.task.TaskStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.repository.*;
import lombok.Getter;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据库管理
 * <p>
 * 负责在系统启动时检查所有必要的表是否存在，不存在则自动创建。
 * 必须在同步数据库数据 Flyway.migrate 之后执行。
 *
 * @author qianye
 * @create 2025-12-17 17:57
 */
@Component
@Order(2)
public class DbManager implements CommandLineRunner {

    @Autowired
    private LinkYaml linkYaml;
    @Autowired
    private ActivationDB activationDB;
    @Autowired
    private ConfigDB configDB;
    @Autowired
    private AlarmDB alarmDB;
    @Autowired
    private AgvDB agvDB;
    @Autowired
    private ChargePeriodDB chargePeriodDB;
    @Autowired
    private ChargePileDB chargePileDB;
    @Autowired
    private TaskDB taskDB;
    @Autowired
    private TaskAgvDB taskAgvDB;
    @Autowired
    private StatisticsDB statisticsDB;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private ChargePileManager chargePileManager;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private AgvSuggestionManager agvSuggestionManager;


    /**
     * 告警列表数据缓存
     */
    @Getter
    private List<Entity> alarmListCache = new CopyOnWriteArrayList<>();
    /**
     * 第三方数据缓存
     */
    @Getter
    private List<JSONObject> thirdPartyCache = new CopyOnWriteArrayList<>();

    /**
     * 工作状态 0结束工作 1正在工作 2上班中 3下班中
     */
    @Getter
    private Integer workState = 1;


    @Override
    public void run(String... args) {
        try {
            // 初始化数据库表
            checkTableExists();
        } catch (Exception e) {
            RcsLog.consoleLog.error("数据库表结构初始化失败，系统可能无法正常运行！", e);
            // 根据严重程度，这里可以选择是否 System.exit(1);
        }

        // 检查数据
        checkData();
        // 更新告警数据
        replaceAlarmList();
    }

    /**
     * 检查数据库表是否存在
     */
    private void checkTableExists() throws SQLException {
        // 存在标记
        boolean flag;
        //检查激活表是否存在
        flag = activationDB.checkTableExists();
        if (!flag) {
            RcsLog.consoleLog.info("检测到H2数据库表 [{}] 不存在，正在创建表", activationDB.TABLE_NAME);
            activationDB.createTable();
            RcsLog.consoleLog.info("H2数据库表 [{}] 创建成功", activationDB.TABLE_NAME);
        }

        // 检查参数表是否存在
        flag = configDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + configDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.algorithmLog.error(logStr);
            throw new RuntimeException(logStr);
        }
        // 检查存储过程是否存在
        flag = configDB.checkProcedureExists();
        if (!flag) {
            String logStr = "数据库存储过程[" + configDB.PROCEDURE_GETCONFIGVALUE + "]不存在，请检查文件夹 doc/db 里是否存在创建该存储过程的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.algorithmLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查告警表是否存在
        flag = alarmDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + alarmDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.algorithmLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查AGV表是否存在
        flag = agvDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + agvDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查充电时间段表是否存在
        flag = chargePeriodDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + chargePeriodDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查充电桩表是否存在
        flag = chargePileDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + chargePileDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查任务表是否存在
        flag = taskDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + taskDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查AGV任务表是否存在
        flag = taskAgvDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + taskAgvDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }

        // 检查统计表是否存在
        flag = statisticsDB.checkTableExists();
        if (!flag) {
            String logStr = "数据库表[" + statisticsDB.TABLE_NAME + "]不存在，请检查文件夹 doc/db 里是否存在创建该表的SQL文件";
            RcsLog.consoleLog.error(logStr);
            RcsLog.sysLog.error(logStr);
            throw new RuntimeException(logStr);
        }
    }


    /**
     * 更新告警数据
     */
    private void replaceAlarmList() {
        // 获取今天 0:00:00 的时间
        DateTime startTime = DateUtil.beginOfDay(DateUtil.date());
        // 获取今天 23:59:59 的时间
        DateTime endTime = DateUtil.endOfDay(DateUtil.date());
        try {
            alarmListCache = alarmDB.queryAlarmList(startTime, endTime);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 检查AGV和充电桩数据，如果AGV或充电桩数据不存在，则创建数据
     * <p>
     * 防止数据库数据被人为误删导致异常
     */
    public void checkData() {
        // 获取AGV链接配置
        Map<String, Map<String, String>> agvLink = linkYaml.getAgvLink();
        for (Map.Entry<String, Map<String, String>> entry : agvLink.entrySet()) {
            String key = entry.getKey();
            Map<String, String> value = entry.getValue();
            try {
                Entity agv = agvDB.selectAgv(new Entity().set("agv_id", key));
                if (agv == null) {
                    // 创建AGV
                    Integer count = agvDB.createAgv(new Entity().set("agv_id", key).set("agv_name", key).set("agv_type", value.get("type")).set("car_range", value.get("car_range")));
                    if (count > 0) {
                        RcsLog.consoleLog.info("{} {} AGV数据创建成功", RcsLog.randomInt(), key);
                        RcsLog.algorithmLog.info("{} {} AGV数据创建成功", RcsLog.randomInt(), key);
                        // 将AGV数据添加到缓存中
                        Entity agvEntity = agvDB.selectAgv(new Entity().set("agv_id", key));
                        agvManager.getRcsAgvMap().putIfAbsent(key, agvEntity.toBean(RcsAgv.class));
                    } else {
                        RcsLog.consoleLog.error("{} {} AGV数据创建失败", RcsLog.randomInt(), key);
                        RcsLog.algorithmLog.error("{} {} AGV数据创建失败", RcsLog.randomInt(), key);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        // 获取充电桩链接配置
        Map<String, Map<String, String>> chargeLink = linkYaml.getChargeLink();
        for (String key : chargeLink.keySet()) {
            try {
                Entity charge = chargePileDB.selectChargePile(new Entity().set("code", key));
                if (charge == null) {
                    // 创建充电桩
                    Integer count = chargePileDB.createChargePile(new Entity().set("code", key).set("name", key));
                    if (count > 0) {
                        RcsLog.consoleLog.info("{} {} 充电桩数据创建成功", RcsLog.randomInt(), key);
                        RcsLog.algorithmLog.info("{} {} 充电桩数据创建成功", RcsLog.randomInt(), key);
                        // 将充电桩数据添加到缓存中
                        Entity chargeEntity = chargePileDB.selectChargePile(new Entity().set("code", key));
                        chargePileManager.getRcsChargePileMap().putIfAbsent(key, chargeEntity.toBean(RcsChargePile.class));
                    } else {
                        RcsLog.consoleLog.error("{} {} 充电桩数据创建失败", RcsLog.randomInt(), key);
                        RcsLog.algorithmLog.error("{} {} 充电桩数据创建失败", RcsLog.randomInt(), key);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 同步数据到调度系统
     * <p>
     * 同步项目：AGV、充电桩、任务
     */
    public void syncDataToRcs() {
        //同步AGV数据
        agvManager.getRcsAgvMap().forEach((key, value) -> {
            try {
                Entity entity = agvDB.selectAgv("select agv_control,task_origin,task_destin,charge_signal,isolation_state,handle_suggestion from rcs_agv where agv_id = ?", key);
                Integer chargeSignal = entity.getInt("charge_signal");
                Integer isolationState = entity.getInt("isolation_state");
                String handleSuggestion = entity.getStr("handle_suggestion");

                value.setChargeSignal(chargeSignal);
                value.setIsolationState(isolationState);
                value.setHandleSuggestion(handleSuggestion);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        //同步充电桩数据
        chargePileManager.getRcsChargePileMap().forEach((key, value) -> {
            try {
                Entity entity = chargePileDB.selectChargePile(new Entity().set("code", key));
                value.setState(entity.getInt("state"));
                value.setIdleState(entity.getInt("idle_state"));
                value.setIsolationState(entity.getInt("isolation_state"));
                value.setMatchType(entity.getInt("match_type"));
                value.setIdleState(entity.getInt("idle_state"));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        //同步任务数据
        taskManager.taskCache.forEach((taskCode, value) -> {
            try {
                Entity entity = agvDB.selectAgv("select interrupt_state from rcs_task where task_code = ?", taskCode);
                if (entity != null) {
                    Integer interruptState = entity.getInt("interrupt_state");
                    value.setInterruptState(interruptState);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        //同步工作状态
        this.workState = configDB.getWorkState();
    }

    /**
     * 同步数据到数据库
     * <p>
     * 同步项目：AGV、充电桩、任务
     */
    public void syncDataToDb() {
        //同步AGV数据
        agvManager.getRcsAgvMap().forEach((key, value) -> {
            try {
                Entity parse = Entity.parse(value, true, true);
                // 移除不可同步的字段
                parse.remove("charge_signal");
                parse.remove("isolation_state");
                //获取处理建议
                List<String> suggestions = agvSuggestionManager.getSuggestions(key);
                parse.set("handle_suggestion", suggestions.toString());

                agvDB.updateAgv(parse, new Entity().set("agv_id", key));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        //同步充电桩数据
        chargePileManager.getRcsChargePileMap().forEach((key, value) -> {
            Map<String, String> chargeLink = linkYaml.getChargeLink(key);
            if (chargeLink != null) {
                String enable = chargeLink.get("enable");
                if ("true".equalsIgnoreCase(enable)) {
                    try {
                        Entity parse = Entity.parse(value, true, true);
                        parse.remove("update_time");
                        chargePileDB.updateChargePile(parse, new Entity().set("code", key));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        //同步任务数据
        for (RcsTask rcsTask : taskManager.taskCache.values()) {
            try {
                RcsTask rcsTaskCopy = ObjectUtil.clone(rcsTask);
                Entity parse = Entity.parse(rcsTask, true, true);
                // 移除不可同步的字段
                parse.remove("id");
                parse.remove("interrupt_state");
                parse.remove("equipment_label");
                parse.remove("task_priority");
                parse.remove("priority_time");
                Integer count = taskDB.updateTask(parse, new Entity().set("task_code", rcsTask.getTaskCode()));
                if (count > 0) {
                    //判断是否任务完成状态
                    if (rcsTaskCopy != null && rcsTaskCopy.getTaskState().compareTo(TaskStateEnum.FINISH.code) == 0) {
                        // 任务完成，从缓存中移除
                        taskManager.taskCache.remove(rcsTask.getTaskCode());
                        RcsLog.consoleLog.info("{} 任务结束，从缓存中移除", rcsTask.getTaskCode());
                        RcsLog.algorithmLog.info("{} 任务结束，从缓存中移除", rcsTask.getTaskCode());
                    } else if (rcsTaskCopy != null && rcsTaskCopy.getTaskState().compareTo(TaskStateEnum.FINISH.code) < 0) {
                        // 任务取消，从缓存中移除
                        taskManager.taskCache.remove(rcsTask.getTaskCode());
                        RcsLog.consoleLog.info("{} 任务取消，从缓存中移除", rcsTask.getTaskCode());
                        RcsLog.algorithmLog.info("{} 任务取消，从缓存中移除", rcsTask.getTaskCode());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
