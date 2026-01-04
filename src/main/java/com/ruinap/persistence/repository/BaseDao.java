package com.ruinap.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.func.VoidFunc1;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Page;
import cn.hutool.db.PageResult;
import cn.hutool.db.sql.Condition;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;


/**
 * 基础 DB 类
 * <p>
 * 需要继承该类才能使用通用方法
 * <p>
 * 封装通用的数据库增删改查功能，减少出错的概率
 *
 * @author qianye
 * @create 2024-11-18 13:47
 */
public class BaseDao {
    // ==================== 基础元数据查询 ====================

    /**
     * 检测存储过程是否存在
     * 仅 MySQL使用，不适用H2
     *
     * @param db           数据库连接
     * @param databaseName 数据库名
     * @param routineName  存储过程名
     * @return true:存在 false:不存在
     * @throws SQLException 抛出数据库异常
     */
    protected boolean checkProcedureExists(Db db, String databaseName, String routineName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_TYPE='PROCEDURE' AND ROUTINE_NAME= ? AND ROUTINE_SCHEMA= ?";
        Number count = db.queryNumber(sql, routineName, databaseName);
        return count != null && count.intValue() > 0;
    }

    /**
     * 检测表是否存在
     *
     * @param db           数据库连接
     * @param databaseName 数据库名
     * @param tableName    表名
     * @return true:存在 false:不存在
     * @throws SQLException 抛出数据库异常
     */
    protected boolean checkTableExists(Db db, String databaseName, String tableName) throws SQLException {
        String sql = "SELECT count(table_name) FROM information_schema.tables WHERE table_schema = ? and table_name = ?";
        Number count = db.queryNumber(sql, databaseName, tableName);
        return count != null && count.intValue() > 0;
    }

    // ==================== 增强查询 (支持 Bean/Record) ====================

    /**
     * 通用查询方法（该方法可能会查询出多条数据，但只返回第一条，请谨慎传入查询条件）
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param where     条件
     * @return 数据对象
     */
    protected Entity select(Db db, String tableName, Entity where) throws SQLException {
        List<Entity> list = db.find(where.setTableName(tableName));
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 通用查询方法
     * <p>
     * 示例：select("SELECT * FROM user WHERE id = ?", 1001);
     *
     * @param db     数据库连接
     * @param sql    SQL字符串
     * @param params 参数条件
     * @return 数据对象
     * @throws SQLException 抛出数据库异常
     */
    protected Entity select(Db db, String sql, Object... params) throws SQLException {
        Entity entity = db.queryOne(sql, params);
        return (entity == null || entity.isEmpty()) ? null : entity;
    }

    /**
     * 通用查询方法 (增强版：支持直接返回 Bean/Record)
     * <p>
     * 示例：selectBean("SELECT * FROM user WHERE id = ?", User.class, 1001);
     *
     * @param db     数据库连接
     * @param sql    SQL字符串
     * @param clazz  目标类型 (支持 DTO, POJO, Record)
     * @param params 参数条件
     * @param <T>    泛型
     * @return 目标对象
     * @throws SQLException 抛出数据库异常
     */
    protected <T> T selectBean(Db db, String sql, Class<T> clazz, Object... params) throws SQLException {
        Entity entity = db.queryOne(sql, params);
        if (entity == null || entity.isEmpty()) {
            return null;
        }
        return entity.toBean(clazz);
    }

    /**
     * 通用查询方法
     * <p>
     * 模糊查询
     * <p>
     * set("name", "like 王%")
     * <p>
     * 大于等于查询
     * <p>
     * set("id", ">= 1")
     * <p>
     * IN查询
     * <p>
     * set("id", "in 1,2,3")
     * <p>
     * set("id", new int[]{1, 2, 3})
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param where     条件
     * @return 数据对象集合
     */
    protected List<Entity> selectList(Db db, String tableName, Entity where) throws SQLException {
        List<Entity> entityList = db.find(where.setTableName(tableName));
        if (entityList == null) {
            return Collections.emptyList();
        }
        return entityList;
    }

    /**
     * 通用复杂条件查询方法
     * <p>
     * 每一个条件都是一个 Condition 对象
     * 示例：
     * new Condition("id", ">", 0)
     * new Condition("id", ">=", 0)
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param wheres    特殊条件
     * @return 数据对象集合
     */
    protected List<Entity> selectList(Db db, String tableName, Condition... wheres) throws SQLException {
        List<Entity> entityList = db.findBy(tableName, wheres);
        if (entityList == null) {
            return Collections.emptyList();
        }
        return entityList;
    }

    /**
     * 自定义 SQL 查询
     * <p>
     * 请严格使用 ? 作为占位符
     * <p>
     * SQL示例： select * from user where id = ? and name = ?
     *
     * @param db     数据库连接
     * @param sql    SQL字符串
     * @param params 参数值 (按顺序对应 ?)
     */
    protected List<Entity> queryList(Db db, String sql, Object... params) throws SQLException {
        List<Entity> list = db.query(sql, params);
        return CollUtil.defaultIfEmpty(list, Collections.emptyList());
    }

    /**
     * 通用自定SQL查询方法 (增强版：支持直接返回 Bean List)
     * <p>
     * 建议优先使用标准占位符 `?`，性能更佳
     *
     * @param db     数据库连接
     * @param sql    SQL字符串
     * @param clazz  目标类型
     * @param params 参数
     * @param <T>    泛型
     * @return Bean 集合
     * @throws SQLException 抛出数据库异常
     */
    protected <T> List<T> queryListBean(Db db, String sql, Class<T> clazz, Object... params) throws SQLException {
        List<Entity> entities = db.query(sql, params);
        if (CollUtil.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(e -> e.toBean(clazz)).toList();
    }

    /**
     * 分页查询
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param pageNum   页码 (1开始)
     * @param pageSize  每页条数
     * @param where     查询条件
     * @return 分页结果
     * @throws SQLException 抛出数据库异常
     */
    protected PageResult<Entity> selectListPage(Db db, String tableName, int pageNum, int pageSize, Entity where) throws SQLException {
        return db.page(
                where.setTableName(tableName),
                new Page(pageNum, pageSize)
        );
    }

    /**
     * 分页查询
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param pageNum   页码 (1开始)
     * @param pageSize  每页条数
     * @param where     查询条件
     * @param clazz     目标 Bean 类型
     * @param <T>       泛型
     * @return PageResult<T>
     * @throws SQLException 抛出数据库异常
     */
    protected <T> PageResult<T> selectPageBean(Db db, String tableName, int pageNum, int pageSize, Entity where, Class<T> clazz) throws SQLException {
        // 1. 获取 Entity 分页数据
        PageResult<Entity> entityPage = db.page(
                where.setTableName(tableName),
                new Page(pageNum, pageSize)
        );

        // 2. 转换数据
        List<T> beanList = entityPage.stream()
                .map(entity -> entity.toBean(clazz))
                .toList();

        // 3. 构造新的 PageResult
        // 注意：PageResult 是 ArrayList 的子类，所以可以直接 addAll
        PageResult<T> result = new PageResult<>(entityPage.getPage(), entityPage.getPageSize(), entityPage.getTotal());
        result.addAll(beanList);

        return result;
    }

    /**
     * 查询统计数量
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param where     条件
     * @return 数量
     * @throws SQLException 抛出数据库异常
     */
    protected long count(Db db, String tableName, Entity where) throws SQLException {
        return db.count(where.setTableName(tableName));
    }

    /**
     * 通用更新方法
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param body      修改的数据
     * @param where     条件
     * @return 受影响行数，0表示无更新
     * @throws SQLException 抛出数据库异常
     */
    protected int update(Db db, String tableName, Entity body, Entity where) throws SQLException {
        return db.update(
                body,
                where.setTableName(tableName)
        );
    }

    /**
     * 通用删除方法
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param where     条件
     * @return 受影响行数，0表示无删除
     * @throws SQLException 抛出数据库异常
     */
    protected int delete(Db db, String tableName, Entity where) throws SQLException {
        return db.del(
                where.setTableName(tableName)
        );
    }

    /**
     * 通用插入方法
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param body      新增内容
     * @return 受影响行数，0表示无添加
     * @throws SQLException 抛出数据库异常
     */
    protected int insert(Db db, String tableName, Entity body) throws SQLException {
        return db.insert(body.setTableName(tableName));
    }

    /**
     * 批量插入方法
     * 适用于大量数据初始化或日志转储
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param list      实体列表
     * @return 每个插入语句的影响行数数组
     * @throws SQLException 抛出数据库异常
     */
    protected int[] insertBatch(Db db, String tableName, List<Entity> list) throws SQLException {
        if (CollUtil.isEmpty(list)) {
            return new int[0];
        }
        for (Entity entity : list) {
            entity.setTableName(tableName);
        }
        return db.insert(list);
    }

    /**
     * 通用插入并返回自增主键方法
     *
     * @param db        数据库连接
     * @param tableName 表名
     * @param body      新增内容
     * @return 返回自增主键
     * @throws SQLException 抛出数据库异常
     */
    protected Integer insertForPrimaryKey(Db db, String tableName, Entity body) throws SQLException {
        return db.insertForGeneratedKey(body.setTableName(tableName)).intValue();
    }

    /**
     * 通用SQL执行方法
     * <p>
     * 用于不需要返回结果集的 SQL 语句（例如创建表、删除表等）
     *
     * @param db  数据库连接
     * @param sql SQL字符串
     * @return 受影响行数，0表示无操作
     * @throws SQLException 抛出数据库异常
     */
    protected int execute(Db db, String sql) throws SQLException {
        return db.execute(sql);
    }

    /**
     * 事务执行模板
     * <p>
     * ⚠️ 警告：
     * 1. 此方法基于 ThreadLocal 管理连接。
     * <p>
     * 2. **严禁**在 func 内部开启新线程（如 VthreadPool.execute），否则事务失效！
     * <p>
     * 3. 必须在 func 内部调用 CommonDB 的方法或使用同一个 DataSource。
     *
     * @param db   数据库连接
     * @param func 事务逻辑
     */
    protected void tx(Db db, VoidFunc1<Db> func) throws SQLException {
        db.tx(func);
    }
}
