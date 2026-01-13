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

 Date: 28/01/2025 14:53:24
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_route_preset
-- ----------------------------
DROP TABLE IF EXISTS `rcs_route_preset`;
CREATE TABLE `rcs_route_preset`
(
    `id`          int(10)                                                 NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT NULL COMMENT '线路编码',
    `name`        varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '线路名称',
    `type`        int(2)                                                  NULL DEFAULT 0 COMMENT '任务类型 0搬运任务 5设备任务 6分拣任务',
    `origin`      varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT NULL COMMENT '起点',
    `destin`      varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT NULL COMMENT '终点',
    `rank`        int(10)                                                 NULL DEFAULT NULL COMMENT '顺序',
    `state`       int(2)                                                  NULL DEFAULT 0 COMMENT '状态 0开启 1关闭',
    `create_by`   varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci  NULL DEFAULT NULL COMMENT '创建人',
    `update_time` datetime                                                NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `remark`      varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = '线路预设表'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
