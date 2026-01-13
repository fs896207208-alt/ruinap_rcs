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

 Date: 28/01/2025 14:51:26
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_route_preset_detail
-- ----------------------------
DROP TABLE IF EXISTS `rcs_route_preset_detail`;
CREATE TABLE `rcs_route_preset_detail`
(
    `id`               int(10)                                                  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_code`       varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci   NULL DEFAULT NULL COMMENT '线路编码',
    `code`             varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT NULL COMMENT '明细编码',
    `name`             varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci   NULL DEFAULT NULL COMMENT '明细名称',
    `state`            int(10)                                                  NULL DEFAULT 0 COMMENT '明细状态 0开启 1关闭',
    `rank`             int(10)                                                  NULL DEFAULT NULL COMMENT '顺序',
    `origin_floor`     int(2)                                                   NULL DEFAULT 1 COMMENT '起点楼层',
    `origin_area`      varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci   NULL DEFAULT NULL COMMENT '起点区域',
    `origin`           varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci   NULL DEFAULT NULL COMMENT '起点',
    `destin_floor`     int(2)                                                   NULL DEFAULT 1 COMMENT '终点楼层',
    `destin_area`      varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci   NULL DEFAULT NULL COMMENT '终点区域',
    `destin`           varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci   NULL DEFAULT NULL COMMENT '终点',
    `equipment_type`   int(2)                                                   NULL DEFAULT 0 COMMENT '设备类型 0全类型 1差速潜伏式，2单舵叉车式',
    `executive_system` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT '0' COMMENT '执行系统 0AGV 1PDA 2输送线 3电梯',
    `finally_task`     int(1)                                                   NULL DEFAULT 1 COMMENT '最终任务 0是 1否',
    `is_control`       int(1)                                                   NULL DEFAULT 0 COMMENT '是否管制 0不管制 1管制',
    `task_control`     varchar(1000) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务管制点',
    `remark`           varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = '线路预设明细表'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
