package com.ruinap.adapter.communicate.event;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruinap.adapter.communicate.base.ClientAttribute;
import com.ruinap.adapter.communicate.base.event.AbstractClientEvent;
import com.ruinap.infra.log.RcsLog;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.concurrent.CompletableFuture;

/**
 * 中转事件处理器
 *
 * @author qianye
 * @create 2025-02-25 19:26
 */
public class TransferWebSocketClientEvent extends AbstractClientEvent<TextWebSocketFrame> {

    /**
     * 接收消息
     *
     * @param attribute 属性
     * @param frame     消息帧
     */
    @Override
    public void receiveMessage(ClientAttribute attribute, TextWebSocketFrame frame) {
        // 检查文本帧是否是null
        if (frame != null) {
            String pact = attribute.getProtocol().getProtocol();
            String equipmentType = attribute.getEquipmentType().getEquipmentType();
            String clientId = attribute.getClientId();
            String message = ((TextWebSocketFrame) frame).text();
            //判断是否是json格式
            boolean typeJsonObj = JSONUtil.isTypeJSONObject(message);
            if (typeJsonObj) {
                JSONObject jsonObject = JSONUtil.parseObj(message);
                String event = jsonObject.getStr("event");
                // 根据客户端 ID和数据戳 找到对应的 CompletableFuture 并完成它
                String lookupKey = StrUtil.format("{}_{}_{}", equipmentType, clientId, jsonObject.getInt("request_id", -1));
                CompletableFuture<JSONObject> future = attribute.removeFuture(lookupKey, JSONObject.class);
                if (future != null) {
                    future.complete(new JSONObject(message));
                }

                //记录日志
                RcsLog.communicateLog.info(RcsLog.formatTemplateRandom(equipmentType + "_" + clientId, "rec_data", jsonObject.getInt("request_id"), jsonObject));
            } else {
                //记录日志
                RcsLog.communicateLog.error(RcsLog.formatTemplateRandom(equipmentType + "_" + clientId, "rec_data", -1, "AgvEvent解析异常，非json格式数据:" + message));
            }
        }
    }

}