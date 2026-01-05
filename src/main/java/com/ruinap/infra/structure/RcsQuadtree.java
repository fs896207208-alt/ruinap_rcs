package com.ruinap.infra.structure;

import com.ruinap.infra.lock.RcsLock;
import com.ruinap.infra.log.RcsLog;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 线程安全的空间索引封装类 (装饰器模式)
 * <p>
 * 内部封装了 JTS Quadtree 并配合读写锁，提供并发安全的空间查询能力。
 * <p>1. <b>分片(Sharding)</b>: 将地图划分为 N*N 个网格，每个网格独立维护锁和 Quadtree。
 * 大幅降低多 AGV 并发更新时的锁竞争（理论吞吐量提升 N*N 倍）。</p>
 * <p>2. <b>原子移动</b>: 支持跨分片的原子移动，防止物体在移动瞬间"消失"。</p>
 *
 * @author qianye
 * @create 2026-01-05 16:32
 */
public class RcsQuadtree {
    /**
     * 内部静态分片类
     * 每个分片拥有独立的锁和数据结构
     */
    private static class Shard {
        final Quadtree tree = new Quadtree();
        final RcsLock lock = RcsLock.ofReadWrite();
    }

    // ================= 核心属性 =================
    private final int rows;
    private final int cols;
    private final double shardWidth;
    private final double shardHeight;
    // 支持配置地图原点，适配负坐标系
    private final double mapMinX;
    private final double mapMinY;

    /**
     * 分片数组 (扁平化存储: index = row * cols + col)
     */
    private final Shard[] shards;

    // ================= 构造函数 =================

    /**
     * 默认构造函数
     * <p>创建单分片模式 (1x1)，行为等同于未分片的 Quadtree</p>
     */
    public RcsQuadtree() {
        // 默认 1000km*1000km, 不分片
        this(1_000_000, 1_000_000, 1, 0, 0);
    }

    /**
     * 高并发分片模式构造函数 (推荐)
     *
     * @param mapWidth  地图总宽度 (mm)
     * @param mapHeight 地图总高度 (mm)
     * @param gridSize  网格划分密度 (例如 4 表示 4x4=16 个分片)
     */
    public RcsQuadtree(double mapWidth, double mapHeight, int gridSize, double minX, double minY) {
        if (gridSize < 1) {
            gridSize = 1;
        }
        this.rows = gridSize;
        this.cols = gridSize;
        this.mapMinX = minX;
        this.mapMinY = minY;
        // 防止除以0
        this.shardWidth = (mapWidth <= 0 ? 1_000_000 : mapWidth) / cols;
        this.shardHeight = (mapHeight <= 0 ? 1_000_000 : mapHeight) / rows;

        int totalShards = rows * cols;
        this.shards = new Shard[totalShards];
        for (int i = 0; i < totalShards; i++) {
            shards[i] = new Shard();
        }

        if (RcsLog.sysLog.isInfoEnabled()) {
            RcsLog.sysLog.info("RcsQuadtree 初始化完成. Mode: {}x{} Shards", rows, cols);
        }
    }

    // ================= 业务接口 =================

    /**
     * 插入物体
     */
    public void insert(Envelope env, Object item) {
        int idx = getShardIndex(env);
        Shard shard = shards[idx];
        shard.lock.runInWrite(() -> shard.tree.insert(env, item));
    }

    /**
     * 移除物体
     *
     * @return true 如果移除成功
     */
    public boolean remove(Envelope env, Object item) {
        int idx = getShardIndex(env);
        Shard shard = shards[idx];
        return shard.lock.supplyInWrite(() -> shard.tree.remove(env, item));
    }

    /**
     * 区域查询 (可能跨越多个分片)
     *
     * @param searchEnv 查询范围
     * @return 结果列表
     */
    @SuppressWarnings("unchecked")
    public List<Object> query(Envelope searchEnv) {
        int minCol = getCol(searchEnv.getMinX());
        int maxCol = getCol(searchEnv.getMaxX());
        int minRow = getRow(searchEnv.getMinY());
        int maxRow = getRow(searchEnv.getMaxY());

        // 1. 快速路径：单分片直接查
        if (minCol == maxCol && minRow == maxRow) {
            int idx = minRow * cols + minCol;
            return shards[idx].lock.supplyInRead(() -> {
                List<?> res = shards[idx].tree.query(searchEnv);
                return res == null ? Collections.emptyList() : (List<Object>) res;
            });
        }

        // 2. 慢速路径：跨分片查询 (一致性保障)
        // 收集所有涉及的分片
        // targetShards 的最大数量是 (maxRow - minRow + 1) * (maxCol - minCol + 1)
        int estimatedShards = (maxRow - minRow + 1) * (maxCol - minCol + 1);
        List<Shard> targetShards = new ArrayList<>(estimatedShards);
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                targetShards.add(shards[r * cols + c]);
            }
        }

        List<Object> combinedResults = new ArrayList<>(16);

        // 核心技巧：递归批量加锁 (Multi-Locking)
        // 只有拿到了所有 targetShards 的读锁，才开始执行查询
        executeWithMultiReadLock(targetShards, 0, () -> {
            for (Shard shard : targetShards) {
                List<?> res = shard.tree.query(searchEnv);
                if (res != null && !res.isEmpty()) {
                    combinedResults.addAll((List<Object>) res);
                }
            }
        });

        return combinedResults;
    }

    /**
     * 辅助方法：递归获取读锁
     */
    private void executeWithMultiReadLock(List<Shard> shards, int index, Runnable action) {
        if (index >= shards.size()) {
            // 所有锁都拿到了，执行核心逻辑
            action.run();
            return;
        }
        // 拿住当前分片的锁，然后递归去拿下一个
        shards.get(index).lock.runInRead(() -> {
            executeWithMultiReadLock(shards, index + 1, action);
        });
    }

    /**
     * 原子移动：从旧位置移除并添加到新位置 (支持跨分片)
     */
    public void move(Envelope oldEnv, Object oldItem, Envelope newEnv, Object newItem) {
        int oldIdx = getShardIndex(oldEnv);
        int newIdx = getShardIndex(newEnv);

        if (oldIdx == newIdx) {
            // 场景A: 同分片移动 (性能最优，只锁一个分片)
            Shard shard = shards[oldIdx];
            shard.lock.runInWrite(() -> doInternalMove(shard.tree, oldEnv, oldItem, newEnv, newItem));
        } else {
            // 场景B: 跨分片移动 (涉及两个锁)
            // 必须按索引顺序加锁，防止死锁 (Deadlock Avoidance)
            int firstIdx = Math.min(oldIdx, newIdx);
            int secondIdx = Math.max(oldIdx, newIdx);

            shards[firstIdx].lock.runInWrite(() -> {
                shards[secondIdx].lock.runInWrite(() -> {
                    // 安全区域：同时持有两把写锁
                    Shard oldShard = shards[oldIdx];
                    if (oldItem != null) {
                        if (!oldShard.tree.remove(oldEnv, oldItem)) {
                            RcsLog.algorithmLog.error("Move 删除失败. Idx: {}", oldIdx);
                        }
                    }
                    Shard newShard = shards[newIdx];
                    if (newItem != null) {
                        newShard.tree.insert(newEnv, newItem);
                    }
                });
            });
        }
    }

    /**
     * 内部移动逻辑 (复用代码)
     */
    private void doInternalMove(Quadtree tree, Envelope oldEnv, Object oldItem, Envelope newEnv, Object newItem) {
        if (oldItem != null) {
            boolean removed = tree.remove(oldEnv, oldItem);
            if (!removed) {
                RcsLog.algorithmLog.error("严重警告: 同片移动时移除失败! Item: {}", oldItem);
            }
        }
        if (newItem != null) {
            tree.insert(newEnv, newItem);
        }
    }

    /**
     * 获取全量数据 (调试用，需锁所有分片，慎用)
     */
    @SuppressWarnings("unchecked")
    public List<Object> queryAll() {
        List<Object> all = new ArrayList<>();
        for (Shard shard : shards) {
            shard.lock.runInRead(() -> {
                all.addAll(shard.tree.queryAll());
            });
        }
        return all;
    }

    /**
     * 获取总数量
     */
    public int size() {
        int count = 0;
        for (Shard shard : shards) {
            // size 操作很快，读锁即可
            count += shard.lock.supplyInRead(shard.tree::size);
        }
        return count;
    }

    public void clear() {
        throw new UnsupportedOperationException("RcsQuadtree 不支持 clear，请重建实例");
    }

    // ================= 辅助计算方法 =================

    private int getShardIndex(Envelope env) {
        // 使用中心点策略 (Centroid Strategy) 确定归属
        double centerX = (env.getMinX() + env.getMaxX()) / 2.0;
        double centerY = (env.getMinY() + env.getMaxY()) / 2.0;
        return getRow(centerY) * cols + getCol(centerX);
    }

    private int getCol(double x) {
        // 假设地图原点为 (0,0)，如果包含负坐标需调整 mapMinX/Y
        int col = (int) ((x - mapMinX) / shardWidth);
        if (col < 0) {
            return 0;
        }
        if (col >= cols) {
            return cols - 1;
        }
        return col;
    }

    private int getRow(double y) {
        int row = (int) ((y - mapMinY) / shardHeight);
        if (row < 0) {
            return 0;
        }
        if (row >= rows) {
            return rows - 1;
        }
        return row;
    }
}
