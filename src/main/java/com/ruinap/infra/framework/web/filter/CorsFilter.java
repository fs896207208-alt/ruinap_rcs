package com.ruinap.infra.framework.web.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 跨域资源共享(CORS)过滤器
 *
 * @author qianye
 * @create 2025-11-27 11:56
 */
public class CorsFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {
        // 过滤器初始化，通常无需操作
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 1. 允许的源，"*" 代表允许所有前端域名访问
        httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        // 2. 允许的请求方法
        httpResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        // 3. 预检请求的缓存时间（秒）
        httpResponse.setHeader("Access-Control-Max-Age", "3600");
        // 4. 允许的请求头
        httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");

        // 5. 处理 OPTIONS 预检请求
        // 浏览器在发送 POST 等请求前会先发送 OPTIONS 请求询问权限
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // 继续执行后续的 Servlet 逻辑
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 过滤器销毁
    }
}
