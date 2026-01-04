package com.ruinap.core.map;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruinap.core.map.pojo.MapSnapshot;
import com.ruinap.core.map.pojo.RcsPoint;
import com.ruinap.core.map.pojo.RcsPointOccupy;
import com.ruinap.core.map.pojo.RcsPointTarget;
import com.ruinap.core.map.strategy.MapSourceStrategy;
import com.ruinap.core.map.util.GeometryUtils;
import com.ruinap.core.map.util.MapKeyUtil;
import com.ruinap.infra.config.MapYaml;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.log.RcsLog;
import org.graph4j.Digraph;
import org.graph4j.GraphBuilder;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <h1>地图构建器 (Core Builder)</h1>
 * <p>
 * 负责将原始 JSON 地图数据与 YAML 配置文件数据进行解析、清洗、合并，
 * 最终构建为不可变的 {@link MapSnapshot} 全局快照。
 * </p>
 * <strong>核心职责：</strong>
 * <ol>
 * <li><b>数据加载：</b> 通过策略模式加载原始 JSON 字符串。</li>
 * <li><b>数据解析：</b> 解析点位 (Point) 及 线路 (Edge)。</li>
 * <li><b>业务融合：</b> 将 JSON 中的业务数据（如充电点）与 YAML 配置进行合并/兜底。</li>
 * <li><b>图构建：</b> 基于 Graph4J 构建用于 A* 导航的有向加权图。</li>
 * <li><b>内存优化：</b> 执行严格的非空校验，避免在大对象中存储无意义的空集合。</li>
 * </ol>
 *
 * @author qianye
 * @create 2025-12-23 16:04
 */
@Component
public class MapLoader {

    /**
     * 地图源策略接口（支持 File/DB/Http 等多种方式读取原始 JSON）
     */
    @Autowired
    private MapSourceStrategy sourceStrategy;

    /**
     * YAML 配置管理（提供补充配置，如桥接点、强制覆盖的管制区等）
     */
    @Autowired
    private MapYaml mapYaml;

    /**
     * 核心加载方法：构建全新的地图快照
     *
     * @return 包含全量数据的不可变快照对象
     */
    public MapSnapshot load() {
        // 1. 加载原始 JSON 数据
        Map<Integer, String> rawData = sourceStrategy.loadRawData();
        if (rawData.isEmpty()) {
            return MapSnapshot.empty();
        }

        // --- 全局容器初始化 ---
        // 临时存储所有地图的全量点位 (用于构建全局图)
        List<RcsPoint> allPoints = new ArrayList<>();
        // 存储每个地图文件的 MD5 指纹
        Map<Integer, String> md5Map = new HashMap<>();

        // --- 业务数据容器 (最终放入 Snapshot) ---
        Map<Integer, List<RcsPoint>> chargePoints = new HashMap<>();
        Map<Integer, List<RcsPoint>> standbyPoints = new HashMap<>();
        Map<Integer, List<RcsPoint>> standbyShieldPoints = new HashMap<>();
        Map<Integer, Map<String, List<RcsPoint>>> controlAreas = new HashMap<>();
        Map<Integer, Map<RcsPoint, List<RcsPoint>>> controlPoints = new HashMap<>();
        Map<Integer, Map<String, List<RcsPoint>>> avoidancePoints = new HashMap<>();
        Map<Integer, Map<String, RcsPoint>> actionParamMap = new HashMap<>();

        // 2. 遍历解析每个地图 (MapId 维度)
        for (Map.Entry<Integer, String> entry : rawData.entrySet()) {
            Integer mapId = entry.getKey();
            String jsonContent = entry.getValue();
            md5Map.put(mapId, SecureUtil.md5(jsonContent));

            try {
                JSONObject json = JSONUtil.parseObj(jsonContent);

                // Step A: 解析基础点位
                List<RcsPoint> mapPoints = new ArrayList<>();
                if (json.containsKey("point")) {
                    mapPoints = json.getJSONArray("point").toList(RcsPoint.class);
                    // 强制校正 mapId，确保数据归属正确
                    mapPoints.forEach(p -> p.setMapId(mapId));
                    allPoints.addAll(mapPoints);
                }

                // 建立"当前地图"的本地查找表 (仅用于辅助解析当前的业务配置)
                Map<Integer, RcsPoint> localMap = mapPoints.stream()
                        .collect(Collectors.toMap(RcsPoint::getId, Function.identity(), (a, b) -> a));

                // Step B: 解析各类业务点列表 (充电、待机等)
                List<RcsPoint> charges = loadSimpleList(mapId, json, "charge", mapYaml.getChargePoint(), localMap);
                if (!charges.isEmpty()) {
                    chargePoints.put(mapId, charges);
                }

                List<RcsPoint> standbys = loadSimpleList(mapId, json, "standby", mapYaml.getStandbyPoint(), localMap);
                if (!standbys.isEmpty()) {
                    standbyPoints.put(mapId, standbys);
                }

                List<RcsPoint> shields = loadSimpleList(mapId, json, "standby_shield", mapYaml.getStandbyShieldPoint(), localMap);
                if (!shields.isEmpty()) {
                    standbyShieldPoints.put(mapId, shields);
                }

                Map<String, List<RcsPoint>> cAreas = loadControlAreas(mapId, json, mapYaml.getControlArea(), localMap);
                if (!cAreas.isEmpty()) {
                    controlAreas.put(mapId, cAreas);
                }

                Map<String, List<RcsPoint>> avoids = loadAvoidancePoints(mapId, json, mapYaml.getAvoidancePoint(), localMap);
                if (!avoids.isEmpty()) {
                    avoidancePoints.put(mapId, avoids);
                }

                Map<String, RcsPoint> aParams = parseActionParams(json, "action_param_index", localMap);
                if (!aParams.isEmpty()) {
                    actionParamMap.put(mapId, aParams);
                }

                Map<RcsPoint, List<RcsPoint>> cPoints = loadControlPoints(mapId, json, mapYaml.getControlPoint(), localMap);
                if (!cPoints.isEmpty()) {
                    controlPoints.put(mapId, cPoints);
                }

            } catch (Exception e) {
                RcsLog.sysLog.error("地图 [{}] 解析失败，请检查 JSON 格式或数据完整性", mapId, e);
            }
        }

        // 3. 构建全局核心索引 (The Grand Unification)
        // -----------------------------------------------------------------------
        // 必须为每一个点分配一个全局唯一的 "算法ID" (graphIndex: 0, 1, 2...)
        // -----------------------------------------------------------------------

        // 业务索引: "1_23" -> Point Object (用于 O(1) 业务查询)
        Map<String, RcsPoint> pointMap = new HashMap<>(allPoints.size());

        // 入口索引: "1_23" -> 0 (用于将业务指令转为算法指令)
        Map<String, Integer> pointKeyToGraphId = new HashMap<>(allPoints.size());

        // 点位占用 Map
        Map<String, RcsPointOccupy> occupys = new HashMap<>(allPoints.size());

        int globalGraphIndex = 0;

        // --- 第一遍循环：分配 ID 并 注入 Vertex ---
        // 初始化图构建器 (泛型指定为 <RcsPoint, RcsPointTarget>)
        Digraph<RcsPoint, RcsPointTarget> graph = GraphBuilder.numVertices(allPoints.size()).buildDigraph();

        for (RcsPoint p : allPoints) {
            String key = MapKeyUtil.compositeKey(p.getMapId(), p.getId());

            // 3.1 填充 Map 索引
            pointMap.put(key, p);
            pointKeyToGraphId.put(key, globalGraphIndex);

            // 3.2 初始化点位占用对象
            occupys.put(key, new RcsPointOccupy(p.getId()));

            // 3.3 【关键】反向注入：将算法ID埋入对象内部
            // 这样以后拿到对象，p.getGraphIndex() 瞬间就能知道它在图里的位置
            p.setGraphIndex(globalGraphIndex);
            // 3.4 【关键】正向注入：将对象塞入图的顶点 Label
            // 这样 graph.getVertexLabel(i) 瞬间就能拿到对象 (满足你的旧习惯)
            graph.setVertexLabel(globalGraphIndex, p);

            globalGraphIndex++;
        }

        // --- 第二遍循环：构建边 (Edge) 并 注入 Edge Label ---
        for (RcsPoint startPoint : allPoints) {
            // 直接获取起点 ID (O(1))
            int u = startPoint.getGraphIndex();

            if (startPoint.getTargets() != null) {
                for (RcsPointTarget target : startPoint.getTargets()) {
                    // 解析目标点的图 ID
                    // 默认同层导航，目标 mapId = 当前 mapId
                    int targetMapId = startPoint.getMapId();
                    int targetPointId = target.getId();
                    String targetKey = MapKeyUtil.compositeKey(targetMapId, targetPointId);

                    // 查表获取目标点的算法 ID
                    Integer v = pointKeyToGraphId.get(MapKeyUtil.compositeKey(targetMapId, targetPointId));
                    RcsPoint endPoint = pointMap.get(targetKey);

                    if (v != null) {
                        //将计算好的 Geometry 注入到 target 对象
                        GeometryUtils.initGeometry(startPoint, endPoint, target);

                        double weight = target.getDistance() > 0 ? target.getDistance() : 1.0;

                        // 1. 建立拓扑连接
                        graph.addEdge(u, v, weight);

                        // 2. 【关键】将导航数据 (Target) 塞入边 Label
                        graph.setEdgeLabel(u, v, target);
                    }
                }
            }
        }

        // 3.5. 添加桥接边 (跨地图连接)
        // 读取 YAML 中的 bridge_point 配置，建立跨层的高权重连接
        addBridgeEdges(graph, pointKeyToGraphId, pointMap, mapYaml.getBridgePoint());

        // ============================================================
        // 4. 构建 JTS 空间索引 (Spatial Indexing)
        // ============================================================
        Map<Integer, STRtree> spatialIndexes = new HashMap<>();

        // 4.1 按 MapId 对所有点位进行分组 (Stream 流处理)
        Map<Integer, List<RcsPoint>> pointsByMap = allPoints.stream()
                .collect(Collectors.groupingBy(RcsPoint::getMapId));

        // 4.2 为每个地图构建独立的 STRtree
        // 这里调用的是我们优化后的 GeometryUtils，构建速度极快
        pointsByMap.forEach((mapId, points) -> {
            STRtree tree = GeometryUtils.buildSpatialIndex(points);
            spatialIndexes.put(mapId, tree);
        });

        // 5. 构建并返回不可变快照
        return MapSnapshot.builder()
                .versionMd5(Collections.unmodifiableMap(md5Map))
                .graph(graph)
                .pointMap(Collections.unmodifiableMap(pointMap))
                .pointKeyToGraphId(Collections.unmodifiableMap(pointKeyToGraphId))
                .occupys(Collections.unmodifiableMap(occupys))
                .spatialIndexes(Collections.unmodifiableMap(spatialIndexes))
                // 注入业务数据
                .chargePoints(Collections.unmodifiableMap(chargePoints))
                .standbyPoints(Collections.unmodifiableMap(standbyPoints))
                .standbyShieldPoints(Collections.unmodifiableMap(standbyShieldPoints))
                .controlAreas(Collections.unmodifiableMap(controlAreas))
                .controlPoints(Collections.unmodifiableMap(controlPoints))
                .avoidancePoints(Collections.unmodifiableMap(avoidancePoints))
                .actionParamMap(Collections.unmodifiableMap(actionParamMap))
                .build();
    }

    // ================== 辅助解析方法 (Fallback 逻辑) ==================

    /**
     * 加载简单列表类型的点位数据 (如：待机点、充电点)
     * <p>
     * <strong>优先级策略：</strong> JSON > YAML (如果 JSON 有数据则直接使用，否则尝试从 YAML 补全)
     * </p>
     *
     * @param mapId    地图 ID
     * @param json     原始 JSON 对象
     * @param jsonKey  JSON 中的字段名 (e.g. "standby")
     * @param yamlCfg  YAML 配置 Map
     * @param localMap 本地 ID->Point 映射表
     * @return 转换后的点位对象列表
     */
    private List<RcsPoint> loadSimpleList(Integer mapId, JSONObject json, String jsonKey,
                                          Map<Integer, ArrayList<Integer>> yamlCfg, Map<Integer, RcsPoint> localMap) {
        List<Integer> ids = new ArrayList<>();
        // 1. 尝试从 JSON 读取
        if (json.containsKey(jsonKey)) {
            ids = json.getJSONArray(jsonKey).toList(Integer.class);
        }

        // 2. 如果 JSON 为空，则从 YAML 读取 (兜底逻辑)
        if (ids.isEmpty() && yamlCfg != null && yamlCfg.containsKey(mapId)) {
            ArrayList<Integer> cfgIds = yamlCfg.get(mapId);
            if (cfgIds != null) {
                ids.addAll(cfgIds);
            }
        }

        // 3. 将 ID 转换为对象
        return mapIdsToPoints(ids, localMap);
    }

    /**
     * 加载管制区数据 (复杂嵌套结构)
     * <p>JSON 结构: [{"C1": [1,2]}, ...] </p>
     * <p>YAML 结构: MapID -> Code -> Floor -> List</p>
     */
    private Map<String, List<RcsPoint>> loadControlAreas(Integer mapId, JSONObject json,
                                                         LinkedHashMap<Integer, LinkedHashMap<String, LinkedHashMap<Integer, ArrayList<Integer>>>> yamlCfg,
                                                         Map<Integer, RcsPoint> localMap) {
        Map<String, List<RcsPoint>> result = new HashMap<>();

        // 1. 解析 JSON
        if (json.containsKey("control")) {
            JSONArray arr = json.getJSONArray("control");
            for (Object obj : arr) {
                JSONObject group = (JSONObject) obj;
                for (String code : group.keySet()) {
                    List<Integer> ids = group.getJSONArray(code).toList(Integer.class);
                    result.put(code, mapIdsToPoints(ids, localMap));
                }
            }
        }

        // 2. YAML 兜底
        if (result.isEmpty() && yamlCfg != null) {
            var mapCfg = yamlCfg.get(mapId);
            if (mapCfg != null) {
                mapCfg.forEach((code, floorMap) -> {
                    List<Integer> allIds = new ArrayList<>();
                    // YAML 配置中可能包含多个楼层，这里只取当前 mapId 的数据
                    if (floorMap.containsKey(mapId)) {
                        allIds.addAll(floorMap.get(mapId));
                    }
                    if (!allIds.isEmpty()) {
                        result.put(code, mapIdsToPoints(allIds, localMap));
                    }
                });
            }
        }
        return result;
    }

    /**
     * 加载避让点数据 (三层结构)
     * <p>YAML 结构: MapID -> Code -> List</p>
     */
    private Map<String, List<RcsPoint>> loadAvoidancePoints(Integer mapId, JSONObject json,
                                                            LinkedHashMap<Integer, LinkedHashMap<String, ArrayList<Integer>>> yamlCfg,
                                                            Map<Integer, RcsPoint> localMap) {
        Map<String, List<RcsPoint>> result = new HashMap<>();

        // 1. 解析 JSON
        if (json.containsKey("avoidance")) {
            JSONArray arr = json.getJSONArray("avoidance");
            for (Object obj : arr) {
                JSONObject group = (JSONObject) obj;
                for (String code : group.keySet()) {
                    List<Integer> ids = group.getJSONArray(code).toList(Integer.class);
                    result.put(code, mapIdsToPoints(ids, localMap));
                }
            }
        }

        // 2. YAML 兜底
        if (result.isEmpty() && yamlCfg != null) {
            var mapCfg = yamlCfg.get(mapId);
            if (mapCfg != null) {
                mapCfg.forEach((code, ids) -> {
                    if (ids != null && !ids.isEmpty()) {
                        result.put(code, mapIdsToPoints(ids, localMap));
                    }
                });
            }
        }
        return result;
    }

    /**
     * 加载管制点数据 (特殊逻辑)
     * <p>管制点通常配置在 Point 对象的属性中 (json: point.control_point)</p>
     */
    private Map<RcsPoint, List<RcsPoint>> loadControlPoints(Integer mapId, JSONObject json,
                                                            LinkedHashMap<Integer, LinkedHashMap<Integer, LinkedHashMap<Integer, ArrayList<Integer>>>> yamlCfg,
                                                            Map<Integer, RcsPoint> localMap) {
        Map<RcsPoint, List<RcsPoint>> result = new HashMap<>();

        // 1. 遍历 JSON 中的所有点位，查找 control_point 字段
        if (json.containsKey("point")) {
            JSONArray pts = json.getJSONArray("point");
            for (Object o : pts) {
                JSONObject pObj = (JSONObject) o;
                if (pObj.containsKey("control_point")) {
                    List<Integer> blockedIds = pObj.getJSONArray("control_point").toList(Integer.class);
                    if (!blockedIds.isEmpty()) {
                        // 触发点
                        RcsPoint trigger = localMap.get(pObj.getInt("id"));
                        if (trigger != null) {
                            result.put(trigger, mapIdsToPoints(blockedIds, localMap));
                        }
                    }
                }
            }
        }

        // 2. YAML 兜底
        if (result.isEmpty() && yamlCfg != null) {
            var mapCfg = yamlCfg.get(mapId);
            if (mapCfg != null) {
                mapCfg.forEach((triggerId, floorMap) -> {
                    RcsPoint trigger = localMap.get(triggerId);
                    if (trigger != null) {
                        List<Integer> allBlocked = new ArrayList<>();
                        if (floorMap.containsKey(mapId)) {
                            allBlocked.addAll(floorMap.get(mapId));
                        }
                        if (!allBlocked.isEmpty()) {
                            result.put(trigger, mapIdsToPoints(allBlocked, localMap));
                        }
                    }
                });
            }
        }
        return result;
    }

    /**
     * 解析动作参数索引 (仅支持 JSON 配置)
     * <p>结构: [{"KeyName": PointID}, ...]</p>
     */
    private Map<String, RcsPoint> parseActionParams(JSONObject json, String key, Map<Integer, RcsPoint> localMap) {
        Map<String, RcsPoint> res = new HashMap<>();
        if (json.containsKey(key)) {
            JSONArray arr = json.getJSONArray(key);
            for (Object o : arr) {
                JSONObject obj = (JSONObject) o;
                for (String paramKey : obj.keySet()) {
                    RcsPoint p = localMap.get(obj.getInt(paramKey));
                    if (p != null) {
                        res.put(paramKey, p);
                    }
                }
            }
        }
        return res;
    }

    /**
     * 工具方法：将 ID 列表转换为 Point 对象列表
     * 会自动过滤掉找不到的 ID (null)
     */
    private List<RcsPoint> mapIdsToPoints(List<Integer> ids, Map<Integer, RcsPoint> localMap) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream().map(localMap::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 处理桥接点 (Bridge Edges)
     * <p>根据 YAML 配置，在不同地图/楼层的点位之间建立高权重的虚拟边，实现跨图导航。</p>
     */
    private void addBridgeEdges(Digraph<RcsPoint, RcsPointTarget> g, Map<String, Integer> keyToId, Map<String, RcsPoint> pointMap, Map<String, ? extends Map<String, String>> bridges) {
        if (bridges == null) {
            return;
        }
        for (Map.Entry<String, ? extends Map<String, String>> entry : bridges.entrySet()) {
            Map<String, String> cfg = entry.getValue();
            // 格式: "1-29" -> mapId-pointId
            String originStr = cfg.get("origin");
            String destinStr = cfg.get("destin");
            String bidirectionalStr = cfg.get("bidirectional");

            if (originStr == null || destinStr == null) {
                continue;
            }

            // 将配置文件中的 "-" 转换为内部使用的 "_" 组合键
            String k1 = originStr.replace("-", "_");
            String k2 = destinStr.replace("-", "_");

            // 获取图算法 ID
            Integer u = keyToId.get(k1);
            Integer v = keyToId.get(k2);
            // 获取起点对象
            RcsPoint startPoint = pointMap.get(k1);
            // 获取终点对象
            RcsPoint endPoint = pointMap.get(k2);

            // 3. 必须确保点对象存在，才能构建几何
            if (u != null && v != null && startPoint != null && endPoint != null) {
                // 设置极高的权重，除非必要（跨楼层），否则 AGV 不会优先走这条路
                double cost = 500000.0;

                // --- 正向边 (u -> v) ---
                g.addEdge(u, v, cost);

                // [关键] 构建虚拟 Target 并注入几何
                RcsPointTarget forwardTarget = createBridgeTarget(cost);
                // ⚡️ 注入几何：生成 start -> end 的直线
                GeometryUtils.initGeometry(startPoint, endPoint, forwardTarget);
                g.setEdgeLabel(u, v, forwardTarget);

                // --- 双向处理 (v -> u) ---
                boolean isBi = bidirectionalStr == null || "true".equalsIgnoreCase(bidirectionalStr);
                if (isBi) {
                    g.addEdge(v, u, cost);

                    // [关键] 反向边必须是一个新的 Target 对象，因为几何方向不同 (end -> start)
                    RcsPointTarget backwardTarget = createBridgeTarget(cost);
                    // ⚡️ 注入几何：生成 end -> start 的直线
                    GeometryUtils.initGeometry(endPoint, startPoint, backwardTarget);
                    g.setEdgeLabel(v, u, backwardTarget);
                }
            }
        }
    }

    /**
     * 辅助方法：创建基础的桥接 Target
     */
    private RcsPointTarget createBridgeTarget(double cost) {
        RcsPointTarget target = new RcsPointTarget();
        // 虚拟 ID，表示这是桥接边
        target.setId(-1);
        // 默认为直线 (RcsCurveType.STRAIGHT)
        target.setType(1);
        // 距离设为权重值，或者设为 0 也可以，视业务而定
        target.setDistance((int) cost);
        return target;
    }
}