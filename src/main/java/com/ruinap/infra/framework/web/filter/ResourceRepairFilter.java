package com.ruinap.infra.framework.web.filter;

import cn.hutool.core.util.StrUtil;
import com.ruinap.infra.framework.web.config.WebProperties;
import com.ruinap.infra.log.RcsLog;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 静态资源/页面路径自动修复过滤器 (重定向版)
 * <p>
 * 作用：解决第三方静态网站写死绝对路径（如 /assets/..., /doc/...）导致 404 的问题。
 * 原理：拦截 404 请求，检查 Referer 头，发现来自子项目时，发送 302 重定向指令，
 * 让浏览器自动跳转到 /webapps/项目名/ 下的正确路径。
 * </p>
 *
 * @author qianye
 * @create 2025-12-17 14:03
 */
public class ResourceRepairFilter implements Filter {

    private final WebProperties webProperties;

    public ResourceRepairFilter(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String uri = req.getRequestURI();

        // 1. 如果是系统已知路径，直接放行
        for (String prefix : webProperties.getSystemPrefixes()) {
            if (uri.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 2. 检查 Referer (来源页面)
        // 只有从某个页面点出来的请求，或者页面里引用的资源，才会有 Referer
        String referer = req.getHeader("Referer");

        if (StrUtil.isNotBlank(referer) && referer.contains(webProperties.getWebappsPath() + "/")) {
            try {
                // 3. 提取项目前缀
                // 假设 Referer 是 http://locahost:9090/webapps/arthas-doc/index.html
                // 我们要提取出 arthas-doc
                int webappsIndex = referer.indexOf(webProperties.getWebappsPath() + "/");
                String pathAfterPort = referer.substring(webappsIndex);
                String[] parts = pathAfterPort.split("/");

                if (parts.length >= 3) {
                    String projectName = parts[2];
                    // arthas-doc
                    String projectPrefix = webProperties.getWebappsPath() + "/" + projectName;

                    // 4. 构建修正后的路径
                    // 浏览器请求: /doc/install.html
                    // 修正目标: /webapps/arthas-doc/doc/install.html
                    String newPath = projectPrefix + uri;

                    // 5. 【核心修改】发送 302 重定向
                    // 告诉浏览器："你走错路了，请去 newPath 找"
                    // 这样浏览器的地址栏会变更为正确的路径，后续的相对链接也就都正常了
                    RcsLog.sysLog.debug("🚀 自动重定向路径: {} -> {} (来源: {})", uri, newPath, projectName);
                    resp.sendRedirect(newPath);
                    return;
                }
            } catch (Exception e) {
                // 解析失败，忽略
            }
        }

        // 正常放行 (如果这里没被重定向，后面大概率会被 JsonErrorHandler 捕获报 404)
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
