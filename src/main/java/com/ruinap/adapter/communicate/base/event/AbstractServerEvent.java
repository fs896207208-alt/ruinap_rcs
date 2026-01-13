package com.ruinap.adapter.communicate.base.event;


import com.ruinap.infra.log.RcsLog;

/**
 * 服务端抽象事件
 *
 * @author qianye
 * @create 2025-05-13 14:43
 */
public abstract class AbstractServerEvent {
    /**
     * 处理事件
     *
     * @param id    ID
     * @param frame 消息帧
     */
    public static void receiveMessage(String id, Object frame) {
        RcsLog.consoleLog.error(RcsLog.getTemplate(2), RcsLog.randomInt(), "请重写 AbstractServerEvent.receiveMessage 方法进行消息处理");
    }
}
