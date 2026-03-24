package com.ruinap.core.task.structure.auction.filter;

import cn.hutool.core.util.StrUtil;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.infra.framework.annotation.Component;

import java.util.List;

/**
 * 设备类型与标签匹配审查器
 * <p>
 * 过滤规则：
 * 1. 类型匹配：若任务指定了 equipmentType，AGV 的 agvType 必须完全一致。
 * 2. 标签匹配：若任务指定了 equipmentLabel，AGV 的 agvLabel 必须与其存在交集（逗号分隔）。
 * </p>
 *
 * @author qianye
 * @create 2026-02-27 09:54
 */
@Component
public class EquipmentMatchFilter implements AgvEligibilityFilter {

    @Override
    public boolean isEligible(RcsAgv agv, RcsTask task) {

        // ==========================================
        // 1. 强校验：设备类型 (equipmentType -> agvType)
        // ==========================================
        // 只有当任务明确限制了设备类型时，才进行严格校验；若为空，视作不限制类型，直接放行
        if (task.getEquipmentType() != null) {
            if (!task.getEquipmentType().equals(agv.getAgvType())) {
                // 类型不符，直接淘汰
                return false;
            }
        }

        // ==========================================
        // 2. 柔性校验：设备标签 (equipmentLabel -> agvLabel)
        // ==========================================
        // 只有当任务明确指定了标签时，才进行校验
        if (StrUtil.isNotBlank(task.getEquipmentLabel())) {

            // 如果任务要求了特殊标签，但 AGV 是一台“白板车”（没有任何标签），肯定干不了
            if (StrUtil.isBlank(agv.getAgvLabel())) {
                return false;
            }

            // 解析任务所需的标签列表（支持英文逗号分隔，如 "重载型,冷库专用"）
            List<String> requiredLabels = StrUtil.split(task.getEquipmentLabel(), ',');

            // 解析 AGV 自身拥有的标签列表
            List<String> agvLabels = StrUtil.split(agv.getAgvLabel(), ',');

            // 【核心并发流处理：交集匹配】
            // 只要 AGV 拥有任务要求中的【任意一个】标签，即视为满足条件。
            // (执行模型：anyMatch 具备短路特性，一旦找到匹配的标签，立即停止遍历，性能极高)
            boolean hasIntersection = requiredLabels.stream().anyMatch(agvLabels::contains);

            if (!hasIntersection) {
                // 标签无交集，淘汰
                return false;
            }
        }

        // 经受住了所有考验，允许该 AGV 进入最终的 A* 物理竞标环节！
        return true;
    }
}