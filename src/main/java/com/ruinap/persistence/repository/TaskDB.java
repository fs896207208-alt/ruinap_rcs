package com.ruinap.persistence.repository;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import cn.hutool.db.sql.Condition;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Service;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.persistence.factory.RcsDSFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务信息数据库
 *
 * @author qianye
 * @create 2024-05-21 16:11
 */
@Service
public class TaskDB extends BaseDao {

    @Autowired
    private RcsDSFactory factory;
    @Autowired
    private ConfigDB configDB;

    /**
     * 表名称
     */
    public String TABLE_NAME = "rcs_task";

    /**
     * 提取SQL为静态常量，避免每次拼接产生String垃圾
     */
    private static final String SELECT_GROUP_SQL = """
            SELECT id, task_group, task_code, task_type, is_control, task_control, equipment_type, equipment_label, equipment_code, pallet_type, origin_floor, origin_area, origin, destin_floor, destin_area, destin, task_priority, priority_time, task_rank, task_state, send_state, interrupt_state, executive_system, create_time, start_time, finish_time, update_time, finally_task, task_source, remark, task_duration 
            FROM {}
            WHERE executive_system = ?
              AND task_state > ?
              AND interrupt_state = ?
            GROUP BY task_group, task_code
            ORDER BY
                task_state DESC, task_priority DESC, priority_time DESC,
                create_time ASC, task_rank ASC
            """;

    /**
     * 缓存格式化后的SQL，确保全生命周期只创建一次
     */
    private final String cachedGroupSql = StrUtil.format(SELECT_GROUP_SQL, TABLE_NAME);

    /**
     * 检测表是否存在
     *
     * @return true:存在 false:不存在
     * @throws SQLException
     */
    public boolean checkTableExists() throws SQLException {
        return checkTableExists(factory.db, factory.dbSetting.getDatabaseName(), TABLE_NAME);
    }

    /**
     * 新建任务
     *
     * @param body 数据体
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer createTask(Entity body) throws SQLException {
        String taskGroup = body.getStr("task_group");
        if (StrUtil.hasEmpty(taskGroup)) {
            body.set("task_group", configDB.taskGroupKey());
        }
        String taskCode = body.getStr("task_code");
        if (StrUtil.hasEmpty(taskCode)) {
            body.set("task_code", configDB.taskCodeKey());
        }
        return insert(factory.db, TABLE_NAME, body);
    }

    /**
     * 修改任务
     *
     * @param body  修改体
     * @param where 条件
     * @return 受影响行数，大于0表示成功
     * @throws SQLException
     */
    public Integer updateTask(Entity body, Entity where) throws SQLException {
        return update(factory.db, TABLE_NAME, body, where);
    }

    /**
     * 查询任务
     *
     * @param where 条件
     * @return 任务数据
     * @throws SQLException
     */
    public Entity queryTask(Entity where) throws SQLException {
        return select(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询任务列表
     *
     * @param where 条件
     * @return 任务列表
     * @throws SQLException
     */
    public List<Entity> queryTaskList(Entity where) throws SQLException {
        return selectList(factory.db, TABLE_NAME, where);
    }

    /**
     * 查询任务列表
     *
     * @param wheres 特殊条件
     * @return 任务列表
     * @throws SQLException
     */
    public List<Entity> queryTaskList(Condition... wheres) throws SQLException {
        return selectList(factory.db, TABLE_NAME, wheres);
    }

    /**
     * 查询分组任务列表
     *
     * @return 任务列表
     */
    public List<Entity> selectTaskGroupList() {
        try {
            return queryList(factory.db, cachedGroupSql, 0, 0, 0);
        } catch (SQLException e) {
            // 捕获异常并记录日志
            RcsLog.sysLog.error(e);
            return new ArrayList<>(0);
        }
    }
}
