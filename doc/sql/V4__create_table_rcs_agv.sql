/*
 Navicat Premium Data Transfer

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 50719
 Source Host           : localhost:3306
 Source Schema         : ruinap_rcs

 Target Server Type    : MySQL
 Target Server Version : 50719
 File Encoding         : 65001

 Date: 04/03/2025 14:21:46
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_agv
-- ----------------------------
DROP TABLE IF EXISTS `rcs_agv`;
CREATE TABLE `rcs_agv`
(
    `id`                int(11)                                                NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agv_id`            varchar(10)                                            NULL DEFAULT NULL COMMENT 'AGV编号',
    `agv_name`          varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT 'AGV名称',
    `agv_type`          int(2)                                                 NULL DEFAULT 0 COMMENT 'AGV类型 1潜伏式 2叉车式',
    `agv_label`         varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT 'AGV标签',
    `brand`             varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT 'ruinap' COMMENT '品牌',
    `car_range`         int(5)                                                 NULL DEFAULT 500 COMMENT '车距 单位半径毫米',
    `map_id`            int(3)                                                 NULL DEFAULT 0 COMMENT '地图号',
    `slam_x`            int(8)                                                 NULL DEFAULT 0 COMMENT 'x坐标',
    `slam_y`            int(8)                                                 NULL DEFAULT 0 COMMENT 'y坐标',
    `slam_angle`        int(8)                                                 NULL DEFAULT 0 COMMENT '角度',
    `slam_cov`          int(8)                                                 NULL DEFAULT 0 COMMENT '协方差',
    `battery`           int(3)                                                 NULL DEFAULT 0 COMMENT '电量',
    `v_x`               int(8)                                                 NULL DEFAULT 0 COMMENT 'x速度',
    `v_y`               int(8)                                                 NULL DEFAULT 0 COMMENT 'y速度',
    `v_angle`           int(8)                                                 NULL DEFAULT 0 COMMENT '角速度',
    `agv_control`       int(1)                                                 NULL DEFAULT 0 COMMENT 'AGV控制权 0调度 1其他',
    `agv_control_mode`  int(1)                                                 NULL DEFAULT 0 COMMENT 'AGV控制模式 0单车调试 1点对点 2调度',
    `agv_mode`          int(1)                                                 NULL DEFAULT 0 COMMENT 'AGV模式 0手动 1自动',
    `agv_state`         int(3)                                                 NULL DEFAULT -1 COMMENT 'AGV状态 -1离线 0待命 1自动行走 2自动动作 3充电中 10暂停 11等待中 12地图切换中',
    `point_id`          int(8)                                                 NULL DEFAULT NULL COMMENT 'AGV当前点位',
    `task_id`           varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '当前任务号',
    `task_origin`       varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务起点',
    `task_destin`       varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务终点',
    `task_state`        int(2)                                                 NULL DEFAULT 0 COMMENT '任务状态 0无任务 1有任务 2已完成 3已取消',
    `task_act`          int(2)                                                 NULL DEFAULT 0 COMMENT '任务动作号 1取货 2放货 3充电 4对接设备',
    `task_param`        int(8)                                                 NULL DEFAULT 0 COMMENT '任务参数',
    `task_description`  varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务描述',
    `charge_signal`     int(2)                                                 NULL DEFAULT 0 COMMENT '充电信号 0正常 1高优先级信号 2低优先级信号',
    `isolation_state`   int(2)                                                 NULL DEFAULT 0 COMMENT '隔离状态 0未隔离 1在线隔离 2离线隔离',
    `goods_state`       int(2)                                                 NULL DEFAULT 0 COMMENT '载货状态 0无货 1单左货 2单右货 3左右货 -1无信号',
    `estop_state`       int(2)                                                 NULL DEFAULT 0 COMMENT '急停状态 0未急停 1急停中',
    `move_dir`          int(2)                                                 NULL DEFAULT 0 COMMENT '运动方向 0停车 1前进 2后退 3左横移 4右横移 5逆原 6顺原',
    `front_area`        int(2)                                                 NULL DEFAULT 0 COMMENT '前避障 0不触发 1触发减速 2触发停车 -1无信号',
    `front_left`        int(2)                                                 NULL DEFAULT 0 COMMENT '左前避障 0不触发 1触发减速 2触发停车 -1无信号',
    `front_right`       int(2)                                                 NULL DEFAULT 0 COMMENT '右前避障 0不触发 1触发减速 2触发停车 -1无信号',
    `back_area`         int(2)                                                 NULL DEFAULT 0 COMMENT '后避障 0不触发 1触发减速 2触发停车 -1无信号',
    `back_left`         int(2)                                                 NULL DEFAULT 0 COMMENT '左后避障 0不触发 1触发减速 2触发停车 -1无信号',
    `back_right`        int(2)                                                 NULL DEFAULT 0 COMMENT '右后避障 0不触发 1触发减速 2触发停车 -1无信号',
    `bump_front`        int(2)                                                 NULL DEFAULT 0 COMMENT '前防撞条 0不触发 1触发 -1无信号',
    `bump_back`         int(2)                                                 NULL DEFAULT 0 COMMENT '后防撞条 0不触发 1触发 -1无信号',
    `bump_left`         int(2)                                                 NULL DEFAULT 0 COMMENT '左防撞条 0不触发 1触发 -1无信号',
    `bump_right`        int(2)                                                 NULL DEFAULT 0 COMMENT '右防撞条 0不触发 1触发 -1无信号',
    `run_time`          int(11)                                                NULL DEFAULT 0 COMMENT '开机运行时间 单位s',
    `run_length`        int(11)                                                NULL DEFAULT 0 COMMENT '开机运行里程 单位mm',
    `light`             int(3)                                                 NULL DEFAULT 0 COMMENT '状态灯颜色 0无色 1红 2绿 3蓝 4黄 5紫 6淡蓝 7白',
    `agv_err_msg`       text CHARACTER SET utf8 COLLATE utf8_general_ci        NULL COMMENT 'AGV错误信息',
    `handle_suggestion` text CHARACTER SET utf8 COLLATE utf8_general_ci        NULL COMMENT '处理建议',
    `pallet_state`      int(2)                                                 NULL DEFAULT 0 COMMENT '托盘状态 0无托 1单左托 2单右托 3左右托',
    `lift_height`       int(5)                                                 NULL DEFAULT 0 COMMENT '举升高度 单位mm',
    `update_time`       datetime                                               NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = 'AGV信息'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
