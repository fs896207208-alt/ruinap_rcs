package com.ruinap.core.task.domain;

import com.ruinap.core.map.pojo.RcsPoint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务路径类
 *
 * @author qianye
 * @create 2024-04-15 13:28
 */
@Getter
@Setter
@EqualsAndHashCode(of = {"agvId", "taskId", "subTaskNo"})
public class TaskPath {
    /**
     * AGV编号
     */
    private String agvId;
    /**
     * 任务ID
     */
    private Integer taskId;
    /**
     * 子任务类型 0前往起点任务 1前往终点任务
     */
    private int subTaskType;
    /**
     * 最后任务 0非最终 1起点最终 2终点最终
     */
    private int finallyTask;
    /**
     * 任务组
     */
    private String taskGroup;
    /**
     * 任务编号
     */
    private String taskCode;
    /**
     * 子任务号
     */
    private volatile int subTaskNo;
    /**
     * 任务起点
     */
    private RcsPoint taskOrigin;
    /**
     * 任务终点
     */
    private RcsPoint taskDestin;
    /**
     * 动作开始 0未开始 1已开始
     */
    private volatile int actionStart;
    /**
     * 任务动作号
     */
    private volatile int taskAction;
    /**
     * 任务参数
     */
    private volatile int taskParameter;
    /**
     * 当前规划起点
     */
    private volatile RcsPoint currentPlanOrigin;
    /**
     * 当前规划终点
     */
    private volatile RcsPoint currentPlanDestin;
    /**
     * 路径编号
     */
    private volatile int pathCode;
    /**
     * 最后一次路径编号
     */
    private volatile int lastPathCode;
    /**
     * 前规划状态
     */
    private volatile int beforeState;
    /**
     * 规划状态 0待检查 5对接设备中 10新任务 20二次规划 30待开始 40运行中 50暂停任务 60动作中 70中断任务 80取消任务 100任务完成
     */
    private volatile int state;
    /**
     * 取消类型 0正常取消 1强制取消
     */
    private volatile int cancelType;
    /**
     * 预期路径
     */
    private volatile List<RcsPoint> expectRoutes = new ArrayList<>();
    /**
     * 预期路径总代价
     */
    private volatile int expectCost;
    /**
     * 已实现代价
     */
    private volatile int realizedCost;
    /**
     * 当前规划标记 0未规划 1已规划 2规划中
     */
    private volatile int currentPlan;
    /**
     * 期望下一个点
     */
    private volatile RcsPoint expectNextPoint;
    /**
     * 运行中点位
     */
    private volatile List<RcsPoint> runningRoutes = new ArrayList<>();
    /**
     * 新规划点位
     */
    private volatile List<RcsPoint> newPlanRoutes = new ArrayList<>();
    /**
     * 已行驶点位
     */
    private volatile List<RcsPoint> traveledRoutes = new ArrayList<>();
    /**
     * 对接设备
     */
    private volatile DockDevice dockDevice;
    /**
     * 交管状态 0未交管 1交管中
     */
    private volatile int trafficState;

    /**
     * 设置规划状态并重置当前规划标记为未规划
     *
     * @param state 规划状态
     */
    public void setState(int state) {
        this.currentPlan = 0;
        this.state = state;
    }

    /**
     * 添加运行中的点位
     *
     * @param rcsPoints 点位集合
     */
    public void addRunningRoutes(List<RcsPoint> rcsPoints) {
        // 判断运行中点位是否为空
        if (this.runningRoutes == null || this.runningRoutes.isEmpty()) {
            this.runningRoutes.addAll(rcsPoints);
        } else {
            //判断运行中路径的最后一点位是否与点位集合第一个点位相同
            if (this.runningRoutes.getLast().equals(rcsPoints.getFirst())) {
                //删除点位集合的第一个点位
                rcsPoints.removeFirst();
                //添加点位集合到运行中点位集合
                this.runningRoutes.addAll(rcsPoints);
            } else {
                //添加点位集合到运行中点位集合
                this.runningRoutes.addAll(rcsPoints);
            }
        }
        //清空新规划点位
        this.newPlanRoutes.clear();
    }

    /**
     * 添加已行驶点位
     *
     * @param rcsPoints 点位集合
     */
    public void addTraveledRoutes(List<RcsPoint> rcsPoints) {
        this.traveledRoutes.addAll(rcsPoints);
    }

    /**
     * 获取运行中的有效点位
     * 计算规则：runningRoutes - traveledRoutes
     *
     * @return 运行中的有效点位列表
     */
    public List<RcsPoint> getEffectiveRunningPoints() {
        int traveledSize = traveledRoutes.size();
        if (traveledSize == 0) {
            return new ArrayList<>(runningRoutes);
        }
        return new ArrayList<>(runningRoutes.subList(traveledSize, runningRoutes.size()));
    }

    /**
     * 计算当前点位与传入点位数组中在 expectRoutes 中出现的第一个点位之间的点位差
     *
     * @param points  传入的点位数组（可能乱序）
     * @param current 当前点位
     * @return 点位差（索引差的绝对值），如果输入无效或点位未找到则返回 -1
     */
    public int getPointDistance(List<RcsPoint> points, RcsPoint current) {
        // 验证输入是否有效
        if (points == null || points.isEmpty() || current == null || expectRoutes == null || expectRoutes.isEmpty()) {
            return -1;
        }

        // 查找当前点位在 expectRoutes 中的索引
        int currentIndex = expectRoutes.indexOf(current);
        if (currentIndex == -1) {
            // 当前点位在 expectRoutes 中未找到
            return -1;
        }

        // 查找 points 中在 expectRoutes 中出现的第一个点位的索引
        int minTargetIndex = Integer.MAX_VALUE;
        for (RcsPoint point : points) {
            int index = expectRoutes.indexOf(point);
            if (index != -1 && index < minTargetIndex) {
                // 更新最小的有效索引
                minTargetIndex = index;
            }
        }

        // 如果没有找到任何点位在 expectRoutes 中
        if (minTargetIndex == Integer.MAX_VALUE) {
            // 目标点位在 expectRoutes 中未找到
            return -1;
        }

        // 计算并返回索引差的绝对值
        return Math.abs(minTargetIndex - currentIndex);
    }
}
