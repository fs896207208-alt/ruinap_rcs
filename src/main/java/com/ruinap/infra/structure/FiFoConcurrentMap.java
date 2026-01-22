package com.ruinap.infra.structure;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

/**
 * 线程安全的FIFO集合实现
 *
 * @author qianye
 * @create 2025-09-19 16:20
 */
public class FiFoConcurrentMap<K, V> {
    private final Map<K, V> map = new ConcurrentHashMap<>();
    private final Queue<K> queue = new ConcurrentLinkedQueue<>();

    /**
     * 构造函数
     *
     */
    public FiFoConcurrentMap() {
    }

    /**
     * 插入键值对，按 FIFO 维护顺序
     *
     * @param key   键
     * @param value 值
     * @return 旧值
     */
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key or value cannot be null");
        }
        // 先放入 Map
        V oldValue = map.put(key, value);
        // 如果是新键，加入队列
        if (oldValue == null) {
            queue.offer(key);
        }
        return oldValue;
    }

    /**
     * 仅当键不存在时插入，按 FIFO 维护顺序
     *
     * @param key   键
     * @param value 值
     * @return 旧值
     */
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Key or value cannot be null");
        }
        // 原子操作：仅当键不存在时插入
        V oldValue = map.putIfAbsent(key, value);
        // 如果插入成功（oldValue == null），加入队列
        if (oldValue == null) {
            queue.offer(key);
        }
        return oldValue;
    }

    /**
     * 获取值
     *
     * @param key 键
     * @return 值
     */
    public V get(K key) {
        return map.get(key);
    }

    /**
     * 移除指定键
     *
     * @param key 键
     * @return 值
     */
    public V remove(K key) {
        // 从队列移除，O(n) 操作
        queue.remove(key);
        return map.remove(key);
    }

    /**
     * 检查是否包含键
     *
     * @param key 键
     * @return 是否包含键
     */
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /**
     * 获取当前大小
     *
     * @return 当前大小
     */
    public int size() {
        return map.size();
    }

    /**
     * 获取所有键值对（弱一致性）
     *
     * @return 所有键值对
     */
    public ConcurrentHashMap<K, V> getAll() {
        return new ConcurrentHashMap<>(map);
    }

    /**
     * 获取按 FIFO 顺序的键列表（弱一致性）
     *
     * @return 按 FIFO 顺序的键列表
     */
    public ConcurrentLinkedQueue<K> getKeysInFifoOrder() {
        return new ConcurrentLinkedQueue<>(queue);
    }

    /**
     * 获取按 FIFO 顺序的值列表（弱一致性）
     *
     * @return 按 FIFO 顺序的值列表
     */
    public List<V> values() {
        List<V> result = new ArrayList<>();
        for (K key : queue) {
            V value = map.get(key);
            // 确保值存在，处理并发移除情况
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * 按 FIFO 顺序遍历键值对（弱一致性）
     *
     * @param action 处理函数
     */
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException("action cannot be null");
        }
        for (K key : queue) {
            V value = map.get(key);
            if (value != null) {
                // 确保值存在，处理并发移除情况
                action.accept(key, value);
            }
        }
    }


    /**
     * 获取按 FIFO 顺序的键值对集合（弱一致性）
     *
     * @return 按 FIFO 顺序的键值对集合
     */
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {
            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                return new Iterator<>() {
                    private final Iterator<K> keyIterator = queue.iterator();

                    @Override
                    public boolean hasNext() {
                        return keyIterator.hasNext();
                    }

                    @Override
                    public Map.Entry<K, V> next() {
                        K key = keyIterator.next();
                        V value = map.get(key);
                        if (value == null) { // 跳过已移除的键
                            return keyIterator.hasNext() ? next() : null;
                        }
                        return new Map.Entry<K, V>() {
                            @Override
                            public K getKey() {
                                return key;
                            }

                            @Override
                            public V getValue() {
                                return value;
                            }

                            @Override
                            public V setValue(V newValue) {
                                throw new UnsupportedOperationException("setValue not supported");
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }
        };
    }
}
