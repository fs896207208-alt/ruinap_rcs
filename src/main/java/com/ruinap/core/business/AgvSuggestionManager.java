package com.ruinap.core.business;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ruinap.infra.framework.annotation.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AGV 处理建议管理器 (Final Corrected Edition)
 * <p>
 * 作用：暂存 WCS 或 上层系统下发给 AGV 的临时建议（如：避让、减速提示）。
 * <p>
 * 修正说明：
 * 1. 确认 AbstractCache 中存在 keySet() 方法，恢复使用。
 * 2. 修复了类型错误：使用 keySet().iterator() 遍历 Key，而不是直接使用 iterator() 遍历 Value。
 * 3. 保持 Value 为 Boolean 以节省内存。
 *
 * @author qianye
 * @create 2026-01-12 10:23
 */
@Service
public class AgvSuggestionManager {

    /**
     * 建议超时时间 (毫秒)
     */
    private static final long TIMEOUT_MS = 5000L;

    /**
     * 建议缓存池
     * Key: AGV编号
     * Value: 该 AGV 的建议缓存 (Key: 建议内容, Value: 占位符)
     */
    private final Map<String, TimedCache<String, Boolean>> agvCacheMap = new ConcurrentHashMap<>();

    /**
     * 添加/更新 AGV 建议信息
     */
    public void addSuggestion(String agvId, String suggestion) {
        if (StrUtil.isBlank(agvId) || StrUtil.isBlank(suggestion)) {
            return;
        }

        // 1. 获取或创建该 AGV 的专属缓存
        TimedCache<String, Boolean> cache = agvCacheMap.computeIfAbsent(agvId, k -> {
            // 创建一个新的带有超时机制的缓存
            return CacheUtil.newTimedCache(TIMEOUT_MS);
        });

        // 2. 写入建议
        // Value 存 Boolean.TRUE 即可，我们只关心 Key (建议内容) 的存在性
        cache.put(suggestion, Boolean.TRUE);
    }

    /**
     * 批量添加建议
     */
    public void addSuggestions(String agvId, List<String> suggestions) {
        if (CollUtil.isEmpty(suggestions)) {
            return;
        }
        suggestions.forEach(s -> addSuggestion(agvId, s));
    }

    /**
     * 获取 AGV 当前有效的建议列表
     *
     * @param agvId AGV编号
     * @return 有效建议列表 (Never null)
     */
    public List<String> getSuggestions(String agvId) {
        if (StrUtil.isBlank(agvId)) {
            return new ArrayList<>();
        }

        TimedCache<String, Boolean> cache = agvCacheMap.get(agvId);
        if (cache == null) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        // [Fix]: 使用 keySet().iterator() 获取 Key 的迭代器
        // 这里的 iterator 返回的是 Set<String> 的迭代器，即建议内容
        Iterator<String> iterator = cache.keySet().iterator();

        while (iterator.hasNext()) {
            String suggestion = iterator.next();
            // 关键逻辑：
            // keySet() 返回的是所有键的快照，可能包含已过期但未被清理的键。
            // 必须再次调用 cache.get(suggestion)，Hutool 内部会检查 expire，
            // 如果过期则返回 null (并触发移除)，确保返回给业务的数据是绝对有效的。
            if (cache.get(suggestion) != null) {
                result.add(suggestion);
            }
        }

        return result;
    }

    /**
     * 移除指定 AGV 的所有缓存（如 AGV 下线时调用）
     */
    public void removeAgvCache(String agvId) {
        agvCacheMap.remove(agvId);
    }
}