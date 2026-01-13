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

 Date: 09/01/2026 14:59:05
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_statistics
-- ----------------------------
DROP TABLE IF EXISTS `rcs_statistics`;
CREATE TABLE `rcs_statistics`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `begin` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `finish` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `run_time` int(10) NULL DEFAULT 0 COMMENT '运行时间',
  `task_count` int(10) NULL DEFAULT 0 COMMENT '任务次数',
  `idle_time` int(10) NULL DEFAULT 0 COMMENT '待机时间',
  `abnormal_time` int(10) NULL DEFAULT 0 COMMENT '异常时间',
  `charge_time` int(10) NULL DEFAULT 0 COMMENT '充电时间',
  `total_itinerary` int(10) NULL DEFAULT 0 COMMENT '总行程',
  `total_itinerary_offset` int(10) NULL DEFAULT 0 COMMENT '总行程偏移量',
  `utilization_rate` double(5, 2) NULL DEFAULT 0.00 COMMENT '稼动率',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '调度统计表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
