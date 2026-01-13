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

DELIMITER $$

-- 判断存储过程是否存在，如果存在则删除
DROP PROCEDURE IF EXISTS getConfigValue$$

-- 创建存储过程
CREATE
    DEFINER = `root`@`localhost` PROCEDURE `getConfigValue`(
    IN targetConfigKey VARCHAR(50), -- 输入参数：配置键，用于查询 rcs_config 表
    IN totalLength INT, -- 输入参数：序列值的目标总长度
    IN prefix VARCHAR(50), -- 输入参数：序列值的前缀
    OUT generatedValue VARCHAR(50) -- 输出参数：生成的完整序列值
)
BEGIN
    -- 定义变量存储当前值（使用字符串处理，兼容性更强）
    DECLARE currentValStr VARCHAR(50);

    -- 1. 获取当前值
    -- 使用 MAX() ：如果查不到数据，MAX会返回NULL而不会报错停止存储过程
    SELECT MAX(config_value)
    INTO currentValStr
    FROM rcs_config
    WHERE config_key = targetConfigKey;

    -- 2. 判空处理
    IF currentValStr IS NULL THEN
        -- 如果未找到配置键，返回 NULL
        SET generatedValue = NULL;
    ELSE
        -- 3. 逻辑运算
        -- 生成新的序列值并赋值给输出参数
        SET generatedValue = CONCAT(
                prefix,
                LPAD(currentValStr,
                     GREATEST(
                             LENGTH(currentValStr), totalLength), '0')
                             );

        -- 4. 更新数据库（自增）
        -- 更新配置表中的值（自增操作）
        UPDATE rcs_config
        SET config_value = config_value + 1
        WHERE config_key = targetConfigKey;
    END IF;

END$$

DELIMITER ;