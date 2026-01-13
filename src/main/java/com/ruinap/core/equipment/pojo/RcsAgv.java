package com.ruinap.core.equipment.pojo;

import cn.hutool.core.annotation.Alias;
import cn.hutool.core.annotation.PropIgnore;
import com.ruinap.core.map.pojo.RcsPoint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.StringJoiner;

/**
 * 调度AGV实体类
 *
 * @author qianye
 * @create 2025-03-01 17:11
 */
@EqualsAndHashCode(of = {"agvId"})
@Getter
@Setter
public class RcsAgv {
    /**
     * 主键
     */
    private Integer id;

    /**
     * AGV编号
     */
    @Alias("agv_id")
    private String agvId;

    /**
     * AGV名称
     */
    @Alias("agv_name")
    private String agvName;

    /**
     * AGV类型 1潜伏式 2叉车式
     */
    @Alias("agv_type")
    private Integer agvType;

    /**
     * AGV标签
     */
    @Alias("agv_label")
    private String agvLabel;

    /**
     * 品牌
     * <p>
     * 调度业务字段（数据来源：配置文件）
     */
    @Alias("brand")
    private volatile String brand;

    /**
     * 车距 单位mm
     * <p>
     * 调度业务字段（数据来源：配置文件）
     */
    @Alias("car_range")
    private volatile Integer carRange;

    /**
     * 地图号
     */
    @Alias("map_id")
    private volatile Integer mapId = -1;

    /**
     * x坐标
     */
    @Alias("slam_x")
    private Integer slamX;

    /**
     * y坐标
     */
    @Alias("slam_y")
    private Integer slamY;

    /**
     * 角度
     */
    @Alias("slam_angle")
    private Integer slamAngle;

    /**
     * 协方差
     */
    @Alias("slam_cov")
    private Integer slamCov;

    /**
     * 电量
     */
    @Alias("battery")
    private Integer battery;

    /**
     * x速度
     */
    @Alias("v_x")
    private Integer vX;

    /**
     * y速度
     */
    @Alias("v_y")
    private Integer vY;

    /**
     * 角速度
     */
    @Alias("v_angle")
    private Integer vAngle;

    /**
     * AGV控制权 0调度 1其他
     */
    @Alias("agv_control")
    private volatile Integer agvControl;

    /**
     * AGV控制模式 0单车调试，1点对点，2调度
     */
    @Alias("agv_control")
    private volatile Integer agvControlMode;

    /**
     * AGV模式 0手动 1自动
     */
    @Alias("agv_mode")
    private volatile Integer agvMode;

    /**
     * AGV状态 -1离线 0待命 1自动行走 2自动动作 3充电中 10暂停 11等待中 12地图切换中
     */
    @Alias("agv_state")
    private volatile Integer agvState;

    /**
     * AGV当前点位
     */
    @Alias("point_id")
    private volatile Integer pointId = -1;

    /**
     * AGV下一个目标点
     */
    @PropIgnore
    private volatile Integer goalPoint = -1;

    /**
     * 当前任务号
     */
    @Alias("task_id")
    private volatile String taskId;

    /**
     * 任务起点
     * <p>
     * 调度业务字段（数据来源：数据库）
     */
    @Alias("task_origin")
    private volatile String taskOrigin;

    /**
     * 任务终点
     * <p>
     * 调度业务字段（数据来源：数据库）
     */
    @Alias("task_destin")
    private volatile String taskDestin;

    /**
     * 任务状态 0无任务 1有任务 2已完成 3已取消
     */
    @Alias("task_state")
    private volatile Integer taskState;

    /**
     * 任务动作号 1取货 2放货 3充电 4对接设备
     */
    @Alias("task_act")
    private volatile Integer taskAct;

    /**
     * 任务参数
     */
    @Alias("task_param")
    private Integer taskParam;

    /**
     * 任务描述
     */
    @Alias("task_description")
    private String taskDescription;

    /**
     * 充电信号 0正常 1高优先级信号 2低优先级信号
     * <p>
     * 调度业务字段（数据来源：数据库）
     */
    @Alias("charge_signal")
    private Integer chargeSignal;

    /**
     * 隔离状态 0未隔离 1在线隔离 2离线隔离
     * <p>
     * 调度业务字段（数据来源：数据库）
     */
    @Alias("isolation_state")
    private volatile Integer isolationState;

    /**
     * 载货状态 0无货 1单左货 2单右货 3左右货 Null为无货物信号
     */
    @Alias("goods_state")
    private volatile Integer goodsState;

    /**
     * 急停状态 0未急停 1急停中
     */
    @Alias("estop_state")
    private volatile Integer estopState;

    /**
     * 运动方向 0停车 1前进 2后退 3左横移 4右横移 5逆原 6顺原
     */
    @Alias("move_dir")
    private volatile Integer moveDir;

    /**
     * 前避障 0不触发 1触发减速 2触发停车 Null无信号
     */
    @Alias("front_area")
    private volatile Integer frontArea;

    /**
     * 左前避障 0不触发 1触发减速 2触发停车 Null无信号
     */
    @Alias("front_left")
    private volatile Integer frontLeft;

    /**
     * 右前避障 0不触发 1触发减速 2触发停车 Null无信号
     */
    @Alias("front_right")
    private volatile Integer frontRight;

    /**
     * 后避障 0不触发 1触发减速 2触发停车 Null无信号
     */
    @Alias("back_area")
    private volatile Integer backArea;

    /**
     * 左后避障 0不触发 1触发减速 2触发停车 Null无信号
     */
    @Alias("back_left")
    private volatile Integer backLeft;

    /**
     * 右后避障 0不触发 1触发减速 2触发停车 Null无信号
     */
    @Alias("back_right")
    private volatile Integer backRight;

    /**
     * 前防撞条 0不触发 1触发 Null无信号
     */
    @Alias("bump_front")
    private volatile Integer bumpFront;

    /**
     * 后防撞条 0不触发 1触发 Null无信号
     */
    @Alias("bump_back")
    private volatile Integer bumpBack;

    /**
     * 左防撞条 0不触发 1触发 Null无信号
     */
    @Alias("bump_left")
    private volatile Integer bumpLeft;

    /**
     * 右防撞条 0不触发 1触发 Null无信号
     */
    @Alias("bump_right")
    private volatile Integer bumpRight;

    /**
     * 开机运行时间 单位s
     */
    @Alias("run_time")
    private Integer runTime;

    /**
     * 开机运行里程 单位mm
     */
    @Alias("run_length")
    private Integer runLength;

    /**
     * 状态灯颜色 0无色 1红 2绿 3蓝 4黄 5紫 6淡蓝 7白
     */
    @Alias("light")
    private volatile Integer light;

    /**
     * AGV错误信息
     */
    @Alias("agv_err_msg")
    private String agvErrMsg;

    /**
     * 处理建议
     * <p>
     * 调度业务字段（数据来源：数据库）
     */
    @Alias("handle_suggestion")
    private String handleSuggestion;

    /**
     * 托盘状态 0无托 1单左托 2单右托 3左右托
     */
    @Alias("pallet_state")
    private Integer palletState;

    /**
     * 举升高度 单位mm
     */
    @Alias("lift_height")
    private volatile Integer liftHeight;

    /**
     * AGV属性
     */
    @Alias("agv_attribute")
    private RcsAgvAttribute rcsAgvAttribute;

    /**
     * 地图是否已检查
     */
    @PropIgnore
    private volatile boolean mapChecked;

    /**
     * 非避障报警，0无报警，1有报警
     */
    @Alias("alarm_signal")
    private volatile Integer alarmSignal;

    /**
     * 是未行驶路径距离最远的设备
     */
    @PropIgnore
    private volatile boolean farthestDistance;

    /**
     * 避让成本 成本越高就越不愿意避让
     */
    @PropIgnore
    private volatile int avoidanceCost;

    /**
     * 避让点
     */
    @PropIgnore
    private RcsPoint avoidancePoint;

    @Override
    public String toString() {
        return new StringJoiner(", ", RcsAgv.class.getSimpleName() + "[", "]")
                .add("agvId='" + agvId + "'")
                .toString();
    }
}
