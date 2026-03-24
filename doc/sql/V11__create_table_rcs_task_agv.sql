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

 Date: 29/01/2026 10:41:27
*/

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_task_agv
-- ----------------------------
DROP TABLE IF EXISTS `rcs_task_agv`;
CREATE TABLE `rcs_task_agv`
(
    `id`               int(10) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_group`       varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务组',
    `task_code`        varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务编号',
    `task_type`        int(1) NULL DEFAULT 0 COMMENT '任务类型 0搬运任务 1充电任务 2停靠任务 3临时任务 4避让任务',
    `is_control`       int(1) NULL DEFAULT 0 COMMENT '是否管制 0无管制 1有管制',
    `task_control`     varchar(500) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务管制点',
    `equipment_type`   int(10) NULL DEFAULT 0 COMMENT '设备类型 0全类型 1差速潜伏式，2单舵叉车式',
    `equipment_label`  varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '设备标签',
    `equipment_code`   varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '设备编号',
    `pallet_type`      int(1) NULL DEFAULT 0 COMMENT '托盘类型 0通用',
    `origin_floor`     int(3) NULL DEFAULT 1 COMMENT '起点楼层',
    `origin_area`      varchar(10) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '起点区域',
    `origin`           varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '起点',
    `destin_floor`     int(3) NULL DEFAULT 1 COMMENT '终点楼层',
    `destin_area`      varchar(10) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '终点区域',
    `destin`           varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '终点',
    `task_priority`    int(3) NULL DEFAULT 0 COMMENT '优先级',
    `priority_time`    datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '置顶时间',
    `task_rank`        int(2) NULL DEFAULT 1 COMMENT '任务顺序',
    `task_state`       int(2) NULL DEFAULT 2 COMMENT '任务状态 -2上位取消 -1任务取消 0任务完成 1暂停任务 2新任务 3动作中 4取货动作中 5卸货动作中 6取货完成 7放货完成 90已下发 97取货运行中 98卸货运行中 99运行中',
    `send_state`       int(1) NULL DEFAULT 0 COMMENT '下发状态 0未下发 1已下发',
    `interrupt_state`  int(1) NULL DEFAULT 0 COMMENT '中断状态 0默认 1中断任务 2取消任务 3上位取消',
    `executive_system` int(2) NULL DEFAULT 0 COMMENT '执行系统 0AGV 1PDA 2输送线 3电梯',
    `create_time`      datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `start_time`       datetime NULL DEFAULT NULL COMMENT '开始时间',
    `finish_time`      datetime NULL DEFAULT NULL COMMENT '完成时间',
    `update_time`      datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `finally_task`     int(1) NULL DEFAULT 0 COMMENT '最终任务 0是 1否',
    `task_source`      varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务来源',
    `remark`           text CHARACTER SET utf8 COLLATE utf8_general_ci NULL COMMENT '备注',
    `task_duration`    int(8) NULL DEFAULT 0 COMMENT '任务时长',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1872 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = 'AGV任务信息表' ROW_FORMAT = DYNAMIC;

SET
FOREIGN_KEY_CHECKS = 1;
