package com.ruinap.core.algorithm;

import com.ruinap.core.equipment.pojo.RcsAgv;
import com.ruinap.core.map.pojo.RcsPoint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.graph4j.Digraph;
import org.graph4j.GraphBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * <h1>基于 Tarjan 算法的死锁检测服务 </h1>
 * <p>
 * <b>核心功能：</b><br>
 * 用于在多 AGV 调度系统中实时检测路径规划产生的死锁风险。
 * 通过构建资源分配图 (Resource Allocation Graph, RAG)，将 AGV 和地图点位映射为图节点，
 * 并利用 Tarjan 算法识别图中的强连通分量 (SCC)，从而精准捕获死锁闭环。
 * </p>
 *
 * <p>
 * <b>架构设计关键点：</b><br>
 * 1. <b>无状态设计 (Stateless)</b>: 将图对象和算法状态下沉到方法栈中。
 * 这确保了该类是绝对<b>线程安全</b>的，可以作为单例 Bean 被高并发调用，完美契合 JDK 21 虚拟线程模型。<br>
 * 2. <b>高性能优化</b>: 引入了 {@code estimatedSize} 预估图容量，避免底层数组频繁扩容 (Re-hashing)。<br>
 * 3. <b>原生适配</b>: 利用 Graph4J 1.0.8 的 {@code Labeled} 接口，直接操作业务对象，代码更具可读性。<br>
 * </p>
 *
 * <p>
 * <b>死锁判定标准 (RAG模型)：</b><br>
 * 1. <b>资源占用 (Assignment)</b>: {@code Point -> AGV}。表示点位当前被某 AGV 物理占据。<br>
 * 2. <b>资源请求 (Request)</b>: {@code AGV -> Point}。表示 AGV 计划进入某点位。<br>
 * 3. <b>死锁环</b>: 当形成 {@code AGV1 -> P1 -> AGV2 -> P2 -> AGV1} 的闭环时，判定为死锁。<br>
 * 4. <b>过滤策略</b>: 仅报告包含 <b>≥2 台 AGV</b> 的闭环。
 * (排除单车因路径规划错误或物理阻塞导致的自环，那些属于“阻塞/超时”范畴，非多车协同死锁)。
 * </p>
 *
 * @author qianye
 * @create 2026-01-09 13:00
 */
public class TarjanDeadlockDetector {

    /**
     * <b>执行死锁检测 (服务入口)</b>
     * <p>
     * 输入当前的全局 AGV 路径快照，输出所有检测到的死锁环路。
     * 该方法是无副作用的纯函数。
     * </p>
     *
     * @param agvToPath Key: AGV实体, Value: 该 AGV 当前规划的路径点列表
     * @return 死锁环列表。每个内部 List 代表一个死锁闭环，包含涉及的 AGV 和点位。
     */
    public List<List<Node>> detect(Map<RcsAgv, List<RcsPoint>> agvToPath) {
        // 1. 基础防御：无数据直接返回，避免空指针与无效计算
        if (agvToPath == null || agvToPath.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 构建资源分配图 (RAG)
        // 这一步是将物理世界的车辆位置关系，转化为数学上的图论模型
        Digraph<Node, Integer> rag = buildRag(agvToPath);

        // 3. 算法计算：寻找图中的强连通分量 (SCC) 并应用业务过滤规则
        return findSccs(rag);
    }

    /**
     * <b>构建资源分配图 (Resource Allocation Graph)</b>
     * <p>
     * 核心逻辑：将业务对象映射为图节点，将资源竞争关系映射为有向边。
     * </p>
     *
     * @param agvToPath AGV 路径数据
     * @return 构建好的有向图
     */
    @SuppressWarnings("unchecked")
    private Digraph<Node, Integer> buildRag(Map<RcsAgv, List<RcsPoint>> agvToPath) {
        // --- 性能优化：容量预估 ---
        // 预估节点数量 = (AGV数 + 路径点数)。
        // 乘以 10 是一个经验系数，确保底层数组有足够空间，避免频繁 resize 扩容导致的性能抖动。
        int estimatedSize = agvToPath.size() * 10;

        // 创建图对象：使用 numVertices 预分配内存，并强转适配泛型接口以支持 Label
        Digraph<Node, Integer> rag = (Digraph<Node, Integer>) GraphBuilder.numVertices(estimatedSize).buildDigraph();

        // --- 辅助数据准备 ---
        // 收集所有被路径覆盖的点位 ID。
        // 作用：只有被路径覆盖的点才可能发生资源竞争。通过此 Set 过滤，避免将无关的点加入图中，精简图规模。
        Set<Integer> pointIdsInPath = new HashSet<>();
        for (List<RcsPoint> path : agvToPath.values()) {
            path.forEach(p -> pointIdsInPath.add(p.getId()));
        }

        // --- Step 1: 添加所有图节点 (Vertex) ---
        // 关键：必须先添加 Vertex，Graph4J 才能建立 Object -> int 的内部索引
        // 否则后续添加 Edge 时会因为找不到 Label 而报错
        for (Map.Entry<RcsAgv, List<RcsPoint>> entry : agvToPath.entrySet()) {
            // 添加 AGV 节点
            rag.addLabeledVertex(new Node(entry.getKey()));
            // 添加 路径点 节点
            for (RcsPoint p : entry.getValue()) {
                rag.addLabeledVertex(new Node(p));
            }
        }

        // --- Step 2: 添加边 (Edge) ---
        for (Map.Entry<RcsAgv, List<RcsPoint>> entry : agvToPath.entrySet()) {
            RcsAgv agv = entry.getKey();
            List<RcsPoint> path = entry.getValue();
            if (path.isEmpty()) {
                continue;
            }

            Node agvNode = new Node(agv);
            // 路径的第一个点，代表 AGV 当前所在的物理位置 (已占用资源)
            Node occupiedNode = new Node(path.getFirst());

            // 边类型 A: 占用边 (Point -> AGV)
            // 含义: 资源 Point 被 AGV 持有。
            // 逻辑: 这是死锁形成的必要条件之一 —— 互斥持有。
            rag.addLabeledEdge(occupiedNode, agvNode, 1);

            // [重要架构决策]
            // 此处严禁添加 "AGV -> OccupiedPoint" 的关联边。
            // 原因：如果添加此边，会形成 P->A 和 A->P 的互指环，导致 AGV 和自身脚下的点
            // 构成一个大小为 2 的 SCC。这将导致所有正常行驶的车辆都被误报为死锁。

            // 构建后续路径的请求链
            for (int i = 0; i < path.size(); i++) {
                RcsPoint p = path.get(i);

                // 边类型 B: 请求边 (AGV -> Point)
                // 含义: AGV 想要获取资源 Point。
                // 优化: 仅当该点在 pointIdsInPath 中(即潜在竞争点)时才连线，减少无效边。
                if (pointIdsInPath.contains(p.getId())) {
                    rag.addLabeledEdge(agvNode, new Node(p), 1);
                }

                // 边类型 C: 连续路径约束 (Point_i -> Point_i+1)
                // 含义: 物理运动的连续性。占据了 Point_i，下一步必然请求 Point_i+1。
                // 这有助于检测“逻辑死锁”（即物理上未接触，但逻辑上已锁死）。
                if (i == 0 && path.size() > 1) {
                    RcsPoint next = path.get(1);
                    if (pointIdsInPath.contains(next.getId())) {
                        rag.addLabeledEdge(occupiedNode, new Node(next), 1);
                    }
                }
            }
        }

        // [架构决策]
        // 移除了 handleAdvancedConflicts 方法。
        // 标准 RAG 模型 (A请求P，P被B占 => A->P->B) 已经能完美表达阻塞关系。
        // 额外添加冲突边会破坏图模型，导致“单向跟车阻塞”被误判为“双向死锁”。

        return rag;
    }

    /**
     * <b>Tarjan 算法主流程</b>
     * <p>
     * 遍历图节点，识别强连通分量 (SCC)。
     * 所有的状态变量 (dfn, low, stack) 都是局部变量，保证线程封闭。
     * </p>
     */
    private List<List<Node>> findSccs(Digraph<Node, Integer> rag) {
        // --- 算法上下文 (局部变量保证线程安全) ---
        // 深度优先数 (时间戳)
        Map<Node, Integer> dfn = new HashMap<>();
        // 最低链路值 (追溯值)
        Map<Node, Integer> low = new HashMap<>();
        // 递归栈
        Stack<Node> stack = new Stack<>();
        // 栈内标记 (用于O(1)判断)
        Set<Node> inStack = new HashSet<>();
        // 结果集
        List<List<Node>> sccs = new ArrayList<>();
        // 全局时间计数器 (数组封装以支持引用传递)
        int[] time = {0};

        // 遍历所有节点 (rag.numVertices 返回底层 int 节点总数)
        for (int i = 0; i < rag.numVertices(); i++) {
            // 通过 int 索引反查 Label (业务对象)
            Node label = rag.getVertexLabel(i);
            // 仅处理未访问过的有效节点
            if (label != null && !dfn.containsKey(label)) {
                tarjanDfs(rag, i, label, dfn, low, stack, inStack, sccs, time);
            }
        }

        // --- 最终业务过滤 (核心策略) ---
        List<List<Node>> realDeadlocks = new ArrayList<>();
        for (List<Node> scc : sccs) {
            // 统计该 SCC 中包含多少个 AGV
            long agvCount = scc.stream().filter(Node::isAgv).count();

            // 判定规则：只有涉及 ≥2 台 AGV 的环，才是多车死锁 (Multi-AGV Deadlock)。
            // 1. 过滤掉单纯的阻塞链 (非环)。
            // 2. 过滤掉因路径规划错误导致的单车自锁 (AGV->P1->AGV)，agvCount=1。
            //    这属于路径合法性检查的范畴，不应触发死锁熔断。
            if (agvCount >= 2) {
                realDeadlocks.add(scc);
            }
        }

        return realDeadlocks;
    }

    /**
     * <b>Tarjan DFS 递归逻辑</b>
     */
    private void tarjanDfs(Digraph<Node, Integer> rag, int uIdx, Node u,
                           Map<Node, Integer> dfn, Map<Node, Integer> low,
                           Stack<Node> stack, Set<Node> inStack,
                           List<List<Node>> sccs, int[] time) {
        // 1. 初始化当前节点
        dfn.put(u, time[0]);
        low.put(u, time[0]);
        time[0]++;
        stack.push(u);
        inStack.add(u);

        // 2. 遍历邻居 (使用 successors 获取 int 索引)
        for (int vIdx : rag.successors(uIdx)) {
            Node v = rag.getVertexLabel(vIdx);
            if (v == null) {
                continue;
            }

            if (!dfn.containsKey(v)) {
                // 情况 A: 树边 (Tree Edge)，递归访问
                tarjanDfs(rag, vIdx, v, dfn, low, stack, inStack, sccs, time);
                // 回溯时，更新当前节点 u 的 low 值 (取 u 和 v 的较小者)
                low.put(u, Math.min(low.get(u), low.get(v)));
            } else if (inStack.contains(v)) {
                // 情况 B: 后向边 (Back Edge)，更新追溯值
                // 说明 v 是 u 的祖先，或者它们在同一个强连通分量中
                low.put(u, Math.min(low.get(u), dfn.get(v)));
            }
        }

        // 3. 判定 SCC 根节点
        // 如果 low[u] == dfn[u]，说明 u 是该 SCC 的入口/根节点
        if (Objects.equals(dfn.get(u), low.get(u))) {
            List<Node> currentScc = new ArrayList<>();
            Node w;
            // 4. 弹栈构建 SCC
            do {
                w = stack.pop();
                inStack.remove(w);
                currentScc.add(w);
            } while (!w.equals(u));

            // 将原始 SCC 加入列表，后续统一在 findSccs 中进行业务过滤
            sccs.add(currentScc);
        }
    }

    /**
     * <b>图节点包装类 (DTO)</b>
     * <p>
     * 实现了 {@link Serializable} 以支持死锁现场数据的序列化持久化。
     * 重写了 Equals/HashCode 以作为 Map 的 Key。
     * </p>
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class Node implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 关联的 AGV 对象 (如果节点代表 AGV)
         */
        private final RcsAgv rcsAgv;
        /**
         * 关联的点位对象 (如果节点代表地图点)
         */
        private final RcsPoint rcsPoint;

        public Node(RcsAgv rcsAgv) {
            this.rcsAgv = rcsAgv;
            this.rcsPoint = null;
        }

        public Node(RcsPoint rcsPoint) {
            this.rcsAgv = null;
            this.rcsPoint = rcsPoint;
        }

        /**
         * 判断当前节点是否代表一个 AGV
         */
        public boolean isAgv() {
            return rcsAgv != null;
        }
    }
}