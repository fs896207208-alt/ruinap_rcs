package com.ruinap.infra.framework.web.servlet;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.json.JSONObject;
import com.ruinap.infra.config.common.PathUtils;
import com.ruinap.infra.framework.web.config.WebProperties;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页发布页 Servlet
 * <p>
 * 功能：
 * 1. 扫描 doc/webapps 目录，动态生成项目列表。
 * 2. 只有访问根路径 (/) 或 index.html 时才渲染列表，其他路径抛出 404 (交给 JsonErrorHandler)。
 * 3. 生成连续的 HTML ID，供前端 ms.js 进行延迟测速。
 * </p>
 *
 * @author qianye
 * @create 2025-07-09 15:48
 */
public class ReleasePageServlet extends HttpServlet {

    private final TemplateEngine engine;
    private final WebProperties webProperties;

    public ReleasePageServlet(WebProperties webProperties) {
        this.webProperties = webProperties;
        // 初始化模板引擎，指向 doc/webapps/release，模式为 FILE (支持热修改)
        this.engine = TemplateUtil.createEngine(
                new TemplateConfig(PathUtils.WEBAPPS_DIR + "/release", TemplateConfig.ResourceMode.FILE)
        );
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String uri = req.getRequestURI();

        // 1. 安全拦截：只允许访问首页，其他 API 路径或深层路径直接抛 404
        // 这样 JettyServer 的 JsonErrorHandler 才能捕获并返回 API JSON 错误
        if (!"/".equals(uri) && !"/index".equals(uri) && !"/index.html".equals(uri) && !"/favicon.ico".equals(uri)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");
        List<JSONObject> releaseList = new ArrayList<>();

        // 获取 Web 根目录
        File webRoot = PathUtils.WEBAPPS_DIR.toFile();
        if (!webRoot.exists()) {
            webRoot.mkdirs();
        }

        File[] webappDirectorys = FileUtil.ls(webRoot.getAbsolutePath());

        // 【关键逻辑】独立计数器
        // 作用：无论中间跳过多少文件夹，确保生成的 HTML ID (lineMs0, lineMs1...) 是连续的。
        // 如果 ID 不连续，前端 ms.js 循环时会找不到元素导致报错。
        int counter = 0;

        // ----------------------------------------------------
        // 步骤一：扫描 doc/webapps 下的动态项目
        // ----------------------------------------------------
        if (webappDirectorys != null) {
            for (File dir : webappDirectorys) {
                String name = dir.getName();

                // 排除系统保留目录
                if ("release".equals(name) || "static".equals(name) ||
                        "console".equals(name) || "monitor".equals(name)) {
                    continue;
                }

                JSONObject item = new JSONObject();
                item.set("li_i_class", "ms" + counter);
                item.set("li_i_id", "lineMs" + counter);

                // 动态项目路径: /webapps/项目名
                String link = "/webapps/" + name;
                item.set("li_span_content", link);
                item.set("li_a_href", link);
                item.set("li_a_title", StrUtil.format("点击访问 {}", name));
                item.set("li_a_strong", name);

                releaseList.add(item);
                counter++; // 成功添加一项，计数器递增
            }
        }

        // ----------------------------------------------------
        // 步骤二：追加固定系统模块 (继续使用 counter)
        // ----------------------------------------------------
        // 1. 调度控制台
        addFixedItem(releaseList, counter++, webProperties.getConsolePath(), "调度控制台", "点击访问调度控制台");

        // 2. H2 数据库管理
        addFixedItem(releaseList, counter++, webProperties.getH2Path(), "数据库管理", "点击访问数据库管理");

        // 3. Druid 连接池监控
        addFixedItem(releaseList, counter++, webProperties.getDruidPath(), "Druid监控", "点击访问Druid监控");

        // 4. Arthas 诊断工具
        addFixedItem(releaseList, counter++, StrUtil.format("http://{}:8563/", InetAddress.getLocalHost().getHostAddress()), "Arthas诊断", "点击访问Arthas诊断工具");


        // ----------------------------------------------------
        // 步骤三：渲染模板
        // ----------------------------------------------------
        Template template = engine.getTemplate("index.html");
        Map<String, Object> bindingMap = new HashMap<>(1);
        bindingMap.put("releaseList", releaseList);
        template.render(bindingMap, resp.getWriter());
    }

    /**
     * 辅助方法：添加固定菜单项
     */
    private void addFixedItem(List<JSONObject> list, int id, String url, String name, String title) {
        JSONObject item = new JSONObject();
        item.set("li_i_class", "ms" + id);
        item.set("li_i_id", "lineMs" + id);
        item.set("li_span_content", url);
        item.set("li_a_href", url);
        item.set("li_a_title", title);
        item.set("li_a_strong", name);
        list.add(item);
    }
}