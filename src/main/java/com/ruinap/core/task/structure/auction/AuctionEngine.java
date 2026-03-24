package com.ruinap.core.task.structure.auction;

import com.ruinap.core.algorithm.domain.RouteResult;
import com.ruinap.core.algorithm.search.RcsAstarSearch;
import com.ruinap.core.equipment.manager.AgvManager;
import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.MapManager;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.core.task.domain.RcsTask;
import com.ruinap.core.task.structure.auction.filter.AgvEligibilityFilter;
import com.ruinap.infra.config.TaskYaml;
import com.ruinap.infra.enums.agv.AgvStateEnum;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * FMS 极速拍卖引擎 (支持动态策略与人工干预)
 *
 * @author qianye
 * @create 2026-02-26 17:43
 */
@Component
public class AuctionEngine {

    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private TaskYaml taskYaml;
    @Autowired
    private AgvManager agvManager;
    @Autowired
    private MapManager mapManager;
    @Autowired
    private List<CostCalculator> costCalculators;
    @Autowired
    private List<AgvEligibilityFilter> eligibilityFilters;
    @Autowired
    private RcsAstarSearch rcsAstarSearch;

    /**
     * 开启一场拍卖
     *
     * @param task 待分配的新任务
     * @return 中标的标书 (包含最终指定的 AGV 和路线)，如果没有AGV接单则返回 null
     */
    public BidResult startAuction(RcsTask task) {
        // ==========================================
        // 0. 前置校验：任务起点合法性 (保护全局)
        // ==========================================
        RcsPoint taskOrigin = mapManager.getPointByAlias(task.getOrigin());
        if (taskOrigin == null) {
            RcsLog.taskLog.error("任务 [{}] 的起点位置 [{}] 不存在或拼写错误！", task.getTaskCode(), task.getOrigin());
            // 直接流标，保护后续所有 A* 算法
            return null;
        }

        // ==========================================
        // 1. 人工强行干预拦截 (黑箱操作)
        // ==========================================
        String manualAssignedAgvCode = checkManualAssignment(task);
        if (manualAssignedAgvCode != null) {
            RcsAgv assignedAgv = agvManager.getRcsAgvByCode(manualAssignedAgvCode);
            // 增加空指针防御，并支持降级
            if (assignedAgv != null) {
                RcsLog.taskLog.info("任务 [{}] 触发人工强制指派逻辑，直接分配给 AGV [{}]", task.getTaskCode(), manualAssignedAgvCode);
                RcsPoint rcsPoint = mapManager.getRcsPoint(assignedAgv.getMapId(), assignedAgv.getPointId());
                RouteResult route = rcsAstarSearch.aStarSearch(assignedAgv.getAgvId(), rcsPoint, taskOrigin);
                return new BidResult(assignedAgv, task, 0.0, route);
            } else {
                RcsLog.taskLog.error("任务 [{}] 指定的 AGV [{}] 不存在，降级为全局自动拍卖！", task.getTaskCode(), manualAssignedAgvCode);
            }
        }

        // ==========================================
        // 2. 资格审查 (过滤出可用的空闲车辆)
        // ==========================================
        List<RcsAgv> eligibleAgvs = agvManager.getRcsAgvMap().values().stream()
                .filter(agv -> AgvStateEnum.isEnumByCode(AgvStateEnum.IDLE, agv.getAgvState()))
                .filter(agv -> eligibilityFilters == null || eligibilityFilters.stream().allMatch(filter -> filter.isEligible(agv, task)))
                .toList();

        if (eligibleAgvs.isEmpty()) {
            RcsLog.taskLog.warn("任务 [{}] 拍卖流标：当前无空闲合规的 AGV", task.getTaskCode());
            return null;
        }

        // ==========================================
        // 2.5 【新增核心优化】漏斗式距离粗筛 (Top-K 机制)
        // ==========================================
        List<RcsAgv> topKAgvs = eligibleAgvs.stream()
                .sorted((agv1, agv2) -> {
                    RcsPoint p1 = mapManager.getRcsPoint(agv1.getMapId(), agv1.getPointId());
                    RcsPoint p2 = mapManager.getRcsPoint(agv2.getMapId(), agv2.getPointId());

                    // 如果获取不到点位，视为距离无限大(Integer.MAX_VALUE)，直接排到最后面淘汰
                    int dist1 = p1 != null ? GeometryUtils.calculateDistance(p1, taskOrigin) : Integer.MAX_VALUE;
                    int dist2 = p2 != null ? GeometryUtils.calculateDistance(p2, taskOrigin) : Integer.MAX_VALUE;
                    return Integer.compare(dist1, dist2);
                })
                .limit(taskYaml.getTaskCommon().getAuctionTopK())
                .toList();

        // ==========================================
        // 3. 虚拟线程并发竞标 (核爆级性能)
        // ==========================================
        List<CompletableFuture<BidResult>> futures = topKAgvs.stream()
                .map(agv -> CompletableFuture.supplyAsync(() -> generateBid(agv, task, taskOrigin), vthreadPool.getExecutor()))
                .toList();

        // 阻塞主线程等待所有虚拟线程计算完毕 (极速)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // ==========================================
        // 4. 决标：选出综合 Cost 最小的胜者
        // ==========================================
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                // 过滤掉彻底算不出路的死锁车
                .filter(bid -> bid.getTotalCost() < Double.MAX_VALUE)
                .min(Comparator.comparingDouble(BidResult::getTotalCost))
                .orElse(null);
    }

    /**
     * AGV 生成竞标书的底层逻辑
     */
    private BidResult generateBid(RcsAgv agv, RcsTask task, RcsPoint taskOrigin) {
        try {
            // 1. 先进行一次 A* 寻路，拿到物理拓扑和拥堵情况
            RcsPoint rcsPoint = mapManager.getRcsPoint(agv.getMapId(), agv.getPointId());
            // 如果车子当前处于脱轨/丢失定位状态，直接放弃竞标
            if (rcsPoint == null) {
                return null;
            }
            RouteResult route = rcsAstarSearch.aStarSearch(agv.getAgvId(), rcsPoint, taskOrigin);
            if (route == null || route.getPaths().isEmpty()) {
                return null;
            }

            // 2. 遍历执行所有动态策略，累加加权代价值
            double finalTotalCost = 0.0;
            if (costCalculators != null) {
                for (CostCalculator calculator : costCalculators) {
                    double rawCost = calculator.calculate(agv, task, route);
                    // 获取动态权重
                    double weight = calculator.getDynamicWeight();
                    finalTotalCost += (rawCost * weight);
                }
            }

            return new BidResult(agv, task, finalTotalCost, route);
        } catch (Exception e) {
            RcsLog.algorithmLog.error("AGV [{}] 竞价计算异常", agv.getAgvId(), e);
            return null;
        }
    }

    /**
     * 检查是否有人工强行指派配置 (你可以在数据库或缓存里维护一个强制指派表)
     */
    private String checkManualAssignment(RcsTask task) {
        // 伪代码：检查缓存中是否存在人工为这个任务指定的 AGV
        // return RedisUtil.get("MANUAL_ASSIGN:TASK:" + task.getTaskCode());
        return null;
    }
}