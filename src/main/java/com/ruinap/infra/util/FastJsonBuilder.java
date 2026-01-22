package com.ruinap.infra.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * 高性能 JSON 流式构建器 (Zero-GC)
 * <p>
 * <strong>核心特性：</strong>
 * <ul>
 * <li><strong>Zero-GC：</strong> 基于 Jackson Generator 直接向 Netty ByteBuf 写入字节，避免生成中间 String 对象。</li>
 * <li><strong>全能兼容：</strong> 支持基础类型高性能写入，同时支持 POJO/Map/List 的自动序列化。</li>
 * <li><strong>资源管理：</strong> 实现 {@link AutoCloseable}，支持 try-with-resources 自动释放资源。</li>
 * </ul>
 *
 * <p><strong>⚠️ 并发安全警告：</strong><br>
 * 此类<b>非线程安全 (Not Thread-Safe)</b>。它持有底层的 OutputStream 和 Generator 状态。<br>
 * 严禁将其定义为类的成员变量或在多线程间共享。<br>
 * <b>正确用法：</b>必须在方法内部局部创建，用完即销毁（栈封闭）。
 * </p>
 *
 * <p><strong>⚠️ 使用警告：</strong><br>
 * 此类为<b>输出流构建</b>。必需释放资源，可手动释放或自动释放<br>
 * 手动调用 finish()方法<br>
 * 或自动释放资源，在 try (FastJsonBuilder json = new FastJsonBuilder(buffer)) { 您的代码逻辑 }
 * </p>
 *
 * @author qianye
 * @create 2026-01-14 15:13
 */
public class FastJsonBuilder implements AutoCloseable {

    /**
     * 全局共享的 Jackson 工厂（线程安全）
     */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    /**
     * 全局共享的对象映射器（线程安全），用于处理复杂对象
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 禁止自动关闭底层流 (关键：我们要复用 ByteBuf，不能被 Jackson 关闭)
        JSON_FACTORY.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        // 序列化配置：总是包含 null 值 (保留字段结构)
        MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        // 允许序列化空值的 Map
        MAPPER.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true);
        // 允许序列化空对象 (避免报错)
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final JsonGenerator gen;
    private final OutputStream os;
    /**
     * 保留引用，用于转 String 调试
     */
    private final ByteBuf buffer;

    /**
     * 创建一个新的 JSON 构建器实例
     * <p>
     * 必须配合 try-with-resources 使用，或在链式调用末尾执行 {@link #finish()}
     *
     * @param out Netty 的 ByteBuf，用于接收 JSON 字节流
     */
    public FastJsonBuilder(ByteBuf out) {
        try {
            this.buffer = out;
            // ByteBufOutputStream 是 Netty 提供的轻量级封装，无缓冲，开销极低
            this.os = new ByteBufOutputStream(out);
            this.gen = JSON_FACTORY.createGenerator(os, JsonEncoding.UTF8);
            // 挂载 ObjectMapper 作为复杂对象处理的大脑
            this.gen.setCodec(MAPPER);
            this.gen.writeStartObject(); // 自动写入起始符 '{'
        } catch (IOException e) {
            throw new RuntimeException("FastJsonBuilder 初始化失败", e);
        }
    }

    // =================================================================
    // 调试与监控
    // =================================================================

    /**
     * 获取当前构建的 JSON 字符串
     * <p>
     * <strong>注意：</strong>
     * <ul>
     * <li>此操作会触发 {@code flush}，确保缓冲区数据写入 ByteBuf。</li>
     * <li>此操作涉及字节转 String 的内存拷贝，<b>仅建议在 DEBUG 模式或日志记录时使用</b>。</li>
     * <li>不会关闭构建器，获取后可继续写入。</li>
     * </ul>
     *
     * @return 完整的 JSON 字符串
     */
    public String toJsonString() {
        try {
            gen.flush(); // 强制刷出 Jackson 内部缓冲区
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("获取 JSON 字符串失败", e);
        }
    }

    @Override
    public String toString() {
        return toJsonString();
    }

    // =================================================================
    // 核心 API：万能 Set
    // =================================================================

    /**
     * 智能设置键值对 (自动类型推断)
     * <p>
     * 优先匹配基础类型以获得极致性能；若为复杂对象(Map/List/POJO)，则委托给 Jackson 序列化。
     *
     * @param key   JSON Key
     * @param value 值 (支持 null, String, Number, Boolean, Map, List, POJO)
     * @return this (链式调用)
     */
    public FastJsonBuilder set(String key, Object value) {
        try {
            if (value == null) {
                gen.writeNullField(key);
                return this;
            }
            // --- 性能快车道 (基础类型直接写，绕过反射) ---
            if (value instanceof String v) {
                gen.writeStringField(key, v);
                return this;
            }
            if (value instanceof Integer v) {
                gen.writeNumberField(key, v);
                return this;
            }
            if (value instanceof Long v) {
                gen.writeNumberField(key, v);
                return this;
            }
            if (value instanceof Boolean v) {
                gen.writeBooleanField(key, v);
                return this;
            }
            if (value instanceof Double v) {
                gen.writeNumberField(key, v);
                return this;
            }
            if (value instanceof Float v) {
                gen.writeNumberField(key, v);
                return this;
            }
            if (value instanceof BigDecimal v) {
                gen.writeNumberField(key, v);
                return this;
            }
            if (value instanceof byte[] v) {
                gen.writeBinaryField(key, v);
                return this;
            } // 自动 Base64 编码

            // --- 复杂对象序列化 (List/Map/POJO) ---
            gen.writeObjectField(key, value);
            return this;
        } catch (IOException e) {
            throw new RuntimeException("JSON 写入异常: key=" + key, e);
        }
    }

    // --- 强类型重载 (手动优化路径，减少 instanceof 判断开销) ---

    public FastJsonBuilder set(String key, String value) {
        try {
            if (value == null) {
                gen.writeNullField(key);
            } else {
                gen.writeStringField(key, value);
            }
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FastJsonBuilder set(String key, int value) {
        try {
            gen.writeNumberField(key, value);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FastJsonBuilder set(String key, long value) {
        try {
            gen.writeNumberField(key, value);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FastJsonBuilder set(String key, boolean value) {
        try {
            gen.writeBooleanField(key, value);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // =================================================================
    // 资源释放
    // =================================================================

    /**
     * 手动结束构建 (finish)
     * <p>
     * 等同于 {@link #close()}，提供语义化的方法名，
     * 用于非 try-with-resources 场景下的链式调用结束。
     */
    public void finish() {
        this.close();
    }

    /**
     * 关闭构建器并释放资源
     * <p>
     * 执行动作：
     * 1. 自动补全根对象的结束符 '}'
     * 2. 刷新缓冲区数据到 ByteBuf
     * 3. 关闭底层流
     */
    @Override
    public void close() {
        try {
            // 自动闭合根对象 }
            // 即使中途发生异常，只要进入 close，Jackson 也会尝试闭合结构
            if (!gen.isClosed()) {
                gen.writeEndObject();
                gen.flush();
                gen.close();
            }
            os.close();
        } catch (IOException e) {
            throw new RuntimeException("关闭 JSON 构建器失败", e);
        }
    }
}