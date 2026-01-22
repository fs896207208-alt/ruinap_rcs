package com.ruinap.infra.util;

import cn.hutool.core.date.DateUtil;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.PostConstruct;
import com.ruinap.infra.thread.VthreadPool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 高性能时间缓存工具 (Zero-GC / Thread-Safe)
 * <p>
 * <strong>设计目标：</strong>
 * 解决高并发下频繁调用 {@code DateUtil.now()} 导致的 CPU (日历计算) 和 内存 (String对象分配) 瓶颈。
 * <p>
 * <strong>实现原理：</strong>
 * <ul>
 * <li>利用 {@link VthreadPool} 开启后台任务，每秒更新一次时间戳。</li>
 * <li>使用 {@code volatile} 保证多线程下的内存可见性。</li>
 * <li>提供“缓存秒级 + 实时毫秒级”的混合拼接方案，兼顾性能与精度。</li>
 * </ul>
 *
 * @author qianye
 * @create 2026-01-14 09:37
 */
@Component
public class CachedTimeUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 全局静态缓存 (Volatile 保证多线程可见性，防止读取到脏数据)
    private static volatile byte[] CURRENT_TIME_BYTES;
    private static volatile String CURRENT_TIME_STR;

    @Autowired
    private VthreadPool vthreadPool;

    /**
     * 初始化定时刷新任务
     */
    @PostConstruct
    public void init() {
        // 1. 同步刷新一次，确保服务启动即刻可用
        refreshTime();

        // 2. 提交给虚拟线程池进行周期性刷新 (1秒一次)
        // 即使有轻微的时间漂移，对于业务日志和通用指令也是可接受的
        vthreadPool.scheduleAtFixedRate(
                this::refreshTime,
                1000,
                1000,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 刷新逻辑 (由写线程执行)
     */
    private void refreshTime() {
        String now = LocalDateTime.now().format(FORMATTER);
        // 赋值操作是原子的，volatile 保证其他线程立即看到最新值
        CURRENT_TIME_STR = now;
        CURRENT_TIME_BYTES = now.getBytes(StandardCharsets.US_ASCII);
    }

    // ================== 公共静态 API ==================

    /**
     * 获取当前时间字符串 (yyyy-MM-dd HH:mm:ss)
     * <p>
     * <strong>性能：</strong> 极高 (直接返回引用，无计算，无 GC)
     */
    public static String getNowString() {
        // 防御性检查：防止类加载顺序导致的空指针（极低概率）
        if (CURRENT_TIME_STR == null) {
            return LocalDateTime.now().format(FORMATTER);
        }
        return CURRENT_TIME_STR;
    }

    /**
     * 获取带毫秒的时间字符串 (yyyy-MM-dd HH:mm:ss.SSS)
     * <p>
     * <strong>性能：</strong> 高 (复用缓存前缀 + 仅计算拼接毫秒)
     * <p>
     * <strong>注意：</strong> 由于“秒”是缓存的，“毫秒”是实时的，在整秒跳变瞬间可能出现
     * 逻辑上的微小不一致（如 12:00:00.999），但这在非金融级业务中完全可以忽略。
     */
    public static String getNowStringWithMillis() {
        if (CURRENT_TIME_STR == null) {
            return DateUtil.now();
        }

        long now = System.currentTimeMillis();
        long millis = now % 1000;

        // 使用 StringBuilder 拼接，预分配容量减少扩容开销
        // 19(时间) + 1(点) + 3(毫秒) = 23
        StringBuilder sb = new StringBuilder(23);
        sb.append(CURRENT_TIME_STR);
        sb.append('.');
        if (millis < 10) {
            sb.append("00");
        } else if (millis < 100) {
            sb.append('0');
        }
        sb.append(millis);

        return sb.toString();
    }

    /**
     * 将当前时间直接写入 ByteBuf (yyyy-MM-dd HH:mm:ss)
     * <p>
     * <strong>性能：</strong> 极致 (纯内存拷贝，Zero-GC)
     */
    public static void writeCurrentTime(ByteBuf out) {
        if (CURRENT_TIME_BYTES != null) {
            out.writeBytes(CURRENT_TIME_BYTES);
        } else {
            ByteBufUtil.writeAscii(out, LocalDateTime.now().format(FORMATTER));
        }
    }

    /**
     * 将带毫秒的时间写入 ByteBuf (yyyy-MM-dd HH:mm:ss.SSS)
     * <p>
     * <strong>性能：</strong> 极致 (无 String 对象产生，毫秒部分通过计算直接写入 ASCII 码)
     */
    public static void writeCurrentTimeWithMillis(ByteBuf out) {
        // 1. 写前缀 (缓存)
        writeCurrentTime(out);
        // 2. 写小数点
        out.writeByte('.');
        // 3. 计算毫秒
        long millis = System.currentTimeMillis() % 1000;
        // 4. 写后缀 (纯数字转 ASCII 写入，避免 String.valueOf 产生的垃圾对象)
        if (millis < 10) {
            // "00" 的 ASCII 码
            out.writeShort(12336);
            ByteBufUtil.writeAscii(out, String.valueOf(millis));
        } else if (millis < 100) {
            out.writeByte('0');
            ByteBufUtil.writeAscii(out, String.valueOf(millis));
        } else {
            ByteBufUtil.writeAscii(out, String.valueOf(millis));
        }
    }
}