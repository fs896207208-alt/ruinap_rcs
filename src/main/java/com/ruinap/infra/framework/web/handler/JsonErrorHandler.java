package com.ruinap.infra.framework.web.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.infra.framework.web.config.WebProperties;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 全局智能异常处理器 (JSON + SPA 适配版)
 * <p>
 * 核心策略：根据请求类型“看人下菜碟”
 * 1. <b>API 接口</b> (/api/**)：返回标准 JSON 格式错误信息，方便前端捕获。
 * 2. <b>静态资源</b> (.js/.css/.png)：直接返回 404 状态码，防止图片裂图显示成 HTML。
 * 3. <b>普通页面</b> (其他)：视为 SPA 前端路由或误输路径，智能转发回 index.html (首页/发布页)。
 * </p>
 *
 * @author qianye
 * @create 2025-12-16 14:42
 */
public class JsonErrorHandler extends ErrorHandler {
    private final WebProperties webProperties;

    /**
     * 构造方法
     *
     * @param webProperties 路径配置类
     */
    public JsonErrorHandler(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String uri = request.getRequestURI();

        // ---------------------------------------------------------
        // 1. 判定是否为 API 请求
        // ---------------------------------------------------------
        // 满足以下任一条件视为 API：
        // a. 路径以 /openapi/ 开头
        // b. 路径以 /druid/ 开头 (监控数据通常也是接口形式)
        // c. 请求头 Accept 包含 application/json (标准 RESTful 调用)
        boolean isApi = StrUtil.startWithIgnoreCase(uri, webProperties.getOpenApiPath() + "/") ||
                uri.startsWith(webProperties.getDruidPath() + "/") ||
                (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));

        // ---------------------------------------------------------
        // 2. 判定是否为纯静态资源
        // ---------------------------------------------------------
        // 如果这些文件找不到，绝不能返回 index.html，否则浏览器会尝试把 HTML 当作图片/脚本解析，报错非常难看。
        boolean isAsset = uri.startsWith(webProperties.getStaticPath() + "/") ||
                StrUtil.endWithAnyIgnoreCase(uri, ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".ttf");

        // ---------------------------------------------------------
        // 3. 分策略处理
        // ---------------------------------------------------------

        if (isApi) {
            // === 场景 A: API 接口报错 ===
            // 返回 JSON，告诉前端具体错哪了
            handleJsonError(baseRequest, request, response);

        } else if (isAsset) {
            // === 场景 B: 静态资源丢失 ===
            // 保持 Jetty 默认行为 (返回简单的 404)，或者什么都不输出直接结束
            super.handle(target, baseRequest, request, response);

        } else {
            // === 场景 C: 疑似 SPA 路由 或 普通页面访问 ===
            // 策略：认为是用户在浏览器地址栏乱输了路径，或者刷新了 Vue/React 的路由
            // 动作：温柔地带他回首页 (index.html)

            // 必须重置缓冲区，否则如果之前已经写入了一部分数据（比如报错堆栈），会导致页面乱码
            response.resetBuffer();

            // 返回 200 OK，让浏览器认为页面加载成功，从而渲染 index.html
            // (注：如果是严格的 API 设计应返回 404，但对 SPA 刷新回退，返回 200 是常规做法)
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html;charset=UTF-8");

            // 执行内部转发 (Forward)
            RequestDispatcher dispatcher = request.getRequestDispatcher("/index.html");
            if (dispatcher != null) {
                dispatcher.forward(request, response);
                // 标记请求已被处理，防止 Jetty 继续执行默认逻辑
                baseRequest.setHandled(true);
            } else {
                // 如果连首页都找不到（极端情况），降级为返回 JSON 提示
                handleJsonError(baseRequest, request, response);
            }
        }
    }

    /**
     * 辅助方法：生成标准 JSON 错误响应
     */
    private void handleJsonError(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        int code = response.getStatus();

        // 获取异常信息 (如果有)
        String errorMessage = (String) request.getAttribute("javax.servlet.error.message");
        if (errorMessage == null) {
            Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception");
            if (th != null) {
                errorMessage = th.getMessage();
            }
        }
        if (errorMessage == null) {
            errorMessage = "Unknown Error";
        }

        // 构建 JSON 对象 (使用 Hutool)
        JSONObject json = new JSONObject();
        json.set("code", code);
        json.set("success", false);
        json.set("message", StrUtil.format("Request failed: {} ({})", code, errorMessage));
        json.set("path", request.getRequestURI());
        json.set("timestamp", System.currentTimeMillis());

        // 设置响应头
        response.setContentType("application/json;charset=UTF-8");
        // 强制设置状态码 (防止被 forward 逻辑篡改)
        response.setStatus(code);

        // 输出 JSON
        try (PrintWriter writer = response.getWriter()) {
            writer.write(json.toString());
            writer.flush();
        }

        // 标记已处理
        baseRequest.setHandled(true);
    }
}