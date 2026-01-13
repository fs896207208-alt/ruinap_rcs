package com.ruinap.adapter.communicate.base.event;

import com.slamopto.communicate.base.ClientAttribute;
import com.slamopto.log.RcsLog;

/**
 * 客户端抽象事件
 *
 * @param <T> 消息帧类型，请传入基础数据类型，禁止使用包装类型，如：Integer、Long、Float、Double、Boolean、Character、Byte、Short、String等
 * @author qianye
 * @create 2025-05-13 14:43
 */
public abstract class AbstractClientEvent<T> {
    /**
     * 处理事件
     *
     * @param attribute 客户端属性
     * @param frame     消息帧
     */
    public void receiveMessage(ClientAttribute attribute, T frame) {
        RcsLog.consoleLog.error(RcsLog.formatTemplateRandom(attribute.getClientId(), "请重写 AbstractClientEvent.receiveMessage 方法进行消息处理"));
    }
}
