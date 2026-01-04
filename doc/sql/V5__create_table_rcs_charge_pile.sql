/*
 Navicat Premium Data Transfer

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 50719
 Source Host           : localhost:3306
 Source Schema         : slamopto_rcs3

 Target Server Type    : MySQL
 Target Server Version : 50719
 File Encoding         : 65001

 Date: 27/01/2025 18:35:53
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_charge_pile
-- ----------------------------
DROP TABLE IF EXISTS `rcs_charge_pile`;
CREATE TABLE `rcs_charge_pile`
(
    `id`              int(11)                                                NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`            varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '充电桩编号',
    `name`            varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '充电桩名称',
    `area`            varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '充电桩区域',
    `floor`           int(3)                                                 NULL DEFAULT 1 COMMENT '充电桩楼层',
    `point_id`        varchar(10)                                            NULL DEFAULT NULL COMMENT '所属点位',
    `voltage`         int(10)                                                NULL DEFAULT 0 COMMENT '电压',
    `current`         int(10)                                                NULL DEFAULT 0 COMMENT '电流',
    `state`           int(1)                                                 NULL DEFAULT 0 COMMENT '状态 0离线 1在线',
    `isolation_state` int(1)                                                 NULL DEFAULT 0 COMMENT '隔离状态 0未隔离 1在线隔离 2离线隔离',
    `match_type`      int(1)                                                 NULL DEFAULT 0 COMMENT '匹配AGV类型 0通用 1潜伏式 2叉车式',
    `mode`            int(1)                                                 NULL DEFAULT -1 COMMENT '充电桩模式 -1离线 0自动 1手动',
    `arm_state`       int(1)                                                 NULL DEFAULT 0 COMMENT '伸缩臂状态 -1未知 0退回到位 1伸出到位',
    `idle_state`      int(1)                                                 NULL DEFAULT -1 COMMENT '空闲状态 -1未知 0空闲 1占用',
    `update_time`     datetime                                               NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = '充电桩信息'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
