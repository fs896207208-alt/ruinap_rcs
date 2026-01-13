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

 Date: 28/02/2025 10:22:18
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for rcs_alarm
-- ----------------------------
DROP TABLE IF EXISTS `rcs_alarm`;
CREATE TABLE `rcs_alarm`
(
    `id`           int(10)                                                NOT NULL AUTO_INCREMENT COMMENT '序号',
    `task_group`   varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务组',
    `task_code`    varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '任务编号',
    `name`         varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '设备名称',
    `level`        int(1)                                                 NULL DEFAULT 0 COMMENT '告警级别  0通知 1警告 2异常 3错误',
    `code`         int(50)                                                NULL DEFAULT 0 COMMENT '告警代码',
    `msg`          text CHARACTER SET utf8 COLLATE utf8_general_ci        NULL COMMENT '告警信息',
    `param`        text CHARACTER SET utf8 COLLATE utf8_general_ci        NULL COMMENT '告警参数',
    `type`         int(1)                                                 NULL DEFAULT 0 COMMENT '告警类型 0AGV 1系统 2设备 3其他 ',
    `report_state` int(1)                                                 NULL DEFAULT 0 COMMENT '是否上报 -1不上报 0未上报 1已上报',
    `source`       varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '告警来源',
    `state`        int(1)                                                 NULL DEFAULT 0 COMMENT '告警状态 0新告警 1告警结束 2系统关闭',
    `create_time`  datetime                                               NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime                                               NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `duration`     int(8)                                                 NULL DEFAULT 0 COMMENT '告警时长',
    `remark`       text CHARACTER SET utf8 COLLATE utf8_general_ci        NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8
  COLLATE = utf8_general_ci COMMENT = '告警信息'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Triggers structure for table rcs_alarm
-- ----------------------------
DROP TRIGGER IF EXISTS `update_alarm_duration`;
delimiter ;;
CREATE TRIGGER `update_alarm_duration`
    BEFORE UPDATE
    ON `rcs_alarm`
    FOR EACH ROW
BEGIN
    -- 只有当 state 被修改为大于 0 且旧值是 0 时触发
    IF NEW.state > 0 AND OLD.state = 0 THEN
        SET NEW.duration = TIMESTAMPDIFF(SECOND, OLD.create_time, COALESCE(NEW.update_time, NOW()));
    END IF;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
