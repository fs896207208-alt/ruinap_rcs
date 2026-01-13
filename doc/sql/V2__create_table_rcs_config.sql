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

 Date: 11/01/2025 17:55:03
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_config
-- ----------------------------
DROP TABLE IF EXISTS `rcs_config`;
CREATE TABLE `rcs_config`
(
    `config_id`     int(5)                                                  NOT NULL AUTO_INCREMENT COMMENT '参数主键',
    `config_name`   varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '参数名称',
    `config_key`    varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '参数键名',
    `config_value`  varchar(500) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '参数键值',
    `config_remark` varchar(500) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '参数备注',
    PRIMARY KEY (`config_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 0
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = '参数配置表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of rcs_config
-- ----------------------------
INSERT INTO `rcs_config`
VALUES (1, '任务组号', 'sys.taskGroup.key', '1', '该字段需配合存储过程getConfigValue使用，请勿直接查询');
INSERT INTO `rcs_config`
VALUES (2, '任务编号', 'sys.task.key', '1', '该字段需配合存储过程getConfigValue使用，请勿直接查询');
INSERT INTO `rcs_config`
VALUES (3, '工作状态', 'rcs.work.state', '1', '工作状态 0结束工作 1正在工作');

SET FOREIGN_KEY_CHECKS = 1;
