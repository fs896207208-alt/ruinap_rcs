package com.ruinap.infra.framework.web.http;

import cn.hutool.json.JSONObject;

import java.io.Serial;
import java.util.HashMap;

/**
 * 操作消息结果
 *
 * @author qianye
 * @create 2024-08-10 2:42
 */
public class AjaxResult extends HashMap<String, Object> {
    @Serial
    private static final long serialVersionUID = 4529735776423624383L;

    /**
     * 状态码
     */
    private static final String CODE_TAG = "code";

    /**
     * 返回内容
     */
    private static final String MSG_TAG = "msg";

    /**
     * 数据对象
     */
    private static final String DATA_TAG = "data";

    /**
     * 状态类型
     */
    public enum HttpCode {
        /**
         * 成功
         */
        SUCCESS(200),
        /**
         * 错误
         */
        ERROR(500);
        private final int value;

        HttpCode(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param type 状态类型
     * @param msg  返回内容
     */
    public AjaxResult(HttpCode type, String msg) {
        super.put(CODE_TAG, type.value);
        super.put(MSG_TAG, msg);
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param type 状态类型
     * @param msg  返回内容
     * @param data 数据对象
     */
    public AjaxResult(HttpCode type, String msg, Object data) {
        super.put(CODE_TAG, type.value);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 方便链式调用
     *
     * @param key   键
     * @param value 值
     * @return 数据对象
     */
    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    /**
     * 响应返回结果
     *
     * @param rows 影响行数
     * @return 操作结果
     */
    protected JSONObject toAjax(int rows) {
        return rows > 0 ? success() : error();
    }

    /**
     * 响应返回结果
     *
     * @param result 结果
     * @return 操作结果
     */
    protected JSONObject toAjax(boolean result) {
        return result ? success() : error();
    }

    /**
     * 返回错误码消息
     */
    public AjaxResult error(HttpCode type, String message) {
        return new AjaxResult(type, message);
    }

    /**
     * 返回成功消息
     *
     * @return 成功消息
     */
    public static JSONObject success() {
        return AjaxResult.success("操作成功");
    }

    /**
     * 返回成功数据
     *
     * @return 成功消息
     */
    public static JSONObject success(Object data) {
        return AjaxResult.success("操作成功", data);
    }

    /**
     * 返回成功消息
     *
     * @param msg 返回内容
     * @return 成功消息
     */
    public static JSONObject success(String msg) {
        return AjaxResult.success(msg, null);
    }

    /**
     * 返回成功消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 成功消息
     */
    public static JSONObject success(String msg, Object data) {
        AjaxResult ajaxResult = new AjaxResult(HttpCode.SUCCESS, msg, data);
        return new JSONObject(ajaxResult, false);
    }

    /**
     * 返回错误消息
     *
     * @return
     */
    public static JSONObject error() {
        return AjaxResult.error("操作失败");
    }

    /**
     * 返回错误消息
     *
     * @param msg 返回内容
     * @return 警告消息
     */
    public static JSONObject error(String msg) {
        return AjaxResult.error(msg, null);
    }

    /**
     * 返回错误消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static JSONObject error(String msg, Object data) {
        AjaxResult ajaxResult = new AjaxResult(HttpCode.ERROR, msg, data);
        return new JSONObject(ajaxResult);
    }
}
