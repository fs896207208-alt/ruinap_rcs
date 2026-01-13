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

 Date: 28/01/2025 16:46:59
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_task_monitor_log
-- ----------------------------
DROP TABLE IF EXISTS `rcs_task_monitor_log`;
CREATE TABLE `rcs_task_monitor_log`
(
    `id`             int(11)                                                NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_group`     varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务组',
    `task_code`      varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务编码',
    `equipment_code` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '设备编码',
    `origin`         varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '起始点',
    `destin`         varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '目的点',
    `old_state`      int(10)                                                NULL DEFAULT 0 COMMENT '修改前状态',
    `new_state`      int(10)                                                NULL DEFAULT 0 COMMENT '任务状态',
    `report_state`   int(10)                                                NULL DEFAULT 0 COMMENT '是否上报 0未上报 1已上报',
    `create_time`    datetime                                               NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `report_time`    datetime                                               NULL DEFAULT NULL COMMENT '上报时间',
    `remark`         text CHARACTER SET utf8 COLLATE utf8_general_ci        NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = '任务触发器记录'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
