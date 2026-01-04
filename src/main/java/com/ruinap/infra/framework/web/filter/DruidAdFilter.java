package com.ruinap.infra.framework.web.filter;

import cn.hutool.core.io.IoUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 【组件】Druid 广告屏蔽过滤器
 * 作用：拦截 common.js，去除底部的 Alibaba 广告、禁止顶部标题跳转、修改系统名称
 *
 * @author qianye
 * @create 2025-12-16 10:13
 */
public class DruidAdFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;
        String uri = httpReq.getRequestURI();

        if (uri.endsWith("common.js")) {
            String filePath = "support/http/resources/js/common.js";
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);

            if (in != null) {
                String content = IoUtil.read(in, StandardCharsets.UTF_8);

                // 1. 去除底部广告调用 (保留这行，防止同步渲染的残留)
                content = content.replace("this.buildFooter();", "");

                // =========================================================
                // 2. 【核心修复：异步轮询补丁】
                // =========================================================
                String jsPatch = "\n\n" +
                        "// --- IOC Framework Auto Patch (Async Fix) ---\n" +
                        "$(document).ready(function() {\n" +
                        "    // 启动一个定时器，每 50ms 检查一次 Header 是否加载完成\n" +
                        "    var patchInterval = setInterval(function() {\n" +
                        "        var $brand = $('.navbar .brand');\n" +
                        "        // 如果找到了 brand 元素，说明 AJAX 加载完成了\n" +
                        "        if ($brand.length > 0) {\n" +
                        "            // --- 执行修改逻辑 ---\n" +
                        "            $brand.attr('href', 'javascript:void(0)');\n" +
                        "            $brand.removeAttr('target');\n" +
                        "            $brand.css('cursor', 'default');\n" +
                        "            $brand.text('Druid Monitor'); \n" +
                        "            \n" +
                        "            // 顺便把 footer 也隐藏掉 (防止漏网之鱼)\n" +
                        "            $('footer').hide();\n" +
                        "            \n" +
                        "            // 修改成功后，清除定时器，停止轮询\n" +
                        "            clearInterval(patchInterval);\n" +
                        "        }\n" +
                        "    }, 50);\n" + // 每 50 毫秒检查一次
                        "});";

                content += jsPatch;
                // =========================================================

                httpResp.reset();
                httpResp.setContentType("text/javascript;charset=utf-8");
                httpResp.getWriter().write(content);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
