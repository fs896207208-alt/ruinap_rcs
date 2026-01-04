package com.ruinap.infra.framework.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.support.http.StatViewServlet;
import com.ruinap.infra.config.CoreYaml;
import com.ruinap.infra.config.DbSetting;
import com.ruinap.infra.config.common.PathUtils;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.annotation.Component;
import com.ruinap.infra.framework.annotation.Order;
import com.ruinap.infra.framework.annotation.PreDestroy;
import com.ruinap.infra.framework.boot.CommandLineRunner;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.web.bind.annotation.RequestMapping;
import com.ruinap.infra.framework.web.bind.annotation.RestController;
import com.ruinap.infra.framework.web.config.WebProperties;
import com.ruinap.infra.framework.web.filter.CorsFilter;
import com.ruinap.infra.framework.web.filter.DruidAdFilter;
import com.ruinap.infra.framework.web.filter.ResourceRepairFilter;
import com.ruinap.infra.framework.web.handler.JsonErrorHandler;
import com.ruinap.infra.framework.web.servlet.DispatcherServlet;
import com.ruinap.infra.framework.web.servlet.ReleasePageServlet;
import com.ruinap.infra.log.RcsLog;
import com.ruinap.infra.thread.VthreadPool;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.util.resource.Resource;
import org.h2.security.SHA256;
import org.h2.server.web.WebServlet;
import org.h2.util.MathUtils;
import org.h2.util.StringUtils;

import javax.servlet.DispatcherType;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;

/**
 * Jetty 嵌入式服务器启动类
 * <p>
 * 负责核心 Web 容器的生命周期管理，集成以下功能：
 * 1. 静态资源托管 (doc/webapps)
 * 2. 动态 SPA 项目挂载与路由回退
 * 3. Spring-MVC 模式的 DispatcherServlet
 * 4. 数据库监控与控制台 (H2/Druid)
 * </p>
 *
 * @author qianye
 * @create 2024-07-17 3:39
 */
@Component
@Order(5)
public class JettyServer implements CommandLineRunner {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private VthreadPool vthreadPool;
    @Autowired
    private CoreYaml coreYaml;
    @Autowired
    private WebProperties webProperties;
    @Autowired
    public DbSetting dbSetting;

    private static Server server;

    @Override
    public void run(String... args) throws Exception {
        startup();
    }

    /**
     * 启动 Jetty 服务
     */
    public void startup() {
        int webPort = coreYaml.getWebPort();
        try {
            server = new Server(webPort);
            HandlerList handlers = new HandlerList();

            // --------------------------------------------------------
            // 模块一：静态资源与 Webapps 挂载 (统一指向 doc/webapps)
            // --------------------------------------------------------
            // 1.1 纯静态资源 (/static -> doc/webapps/static)
            // 用于存放图片、CSS、JS 等，不涉及 SPA 回退，找不到即 404
            addStaticResourceHandler(handlers, webProperties.getStaticPath(), PathUtils.WEBAPPS_DIR + webProperties.getStaticPath());

            // 1.2 系统级固定 SPA 别名映射
            // /console -> doc/webapps/console/console.html
            addFixedSpaContext(handlers, webProperties.getConsolePath(), PathUtils.WEBAPPS_DIR + webProperties.getConsolePath(), "console.html");

            // 1.3 【核心】动态 Webapps 容器 (/webapps/** -> doc/webapps/**)
            // 支持热插拔：doc/webapps 下新增文件夹 xxx，即可通过 /webapps/xxx 访问
            // 内置智能 SPA 回退：/webapps/xxx/about (404) -> /webapps/xxx/index.html
            addDynamicWebappsContext(handlers, webProperties.getWebappsPath(), PathUtils.WEBAPPS_DIR.toString());


            // --------------------------------------------------------
            // 模块二：动态 Servlet 上下文 (MVC / API / Filter)
            // --------------------------------------------------------
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            // 2.1 全局错误处理
            // 处理 API 接口的 404/500，返回 JSON 格式
            context.setErrorHandler(new JsonErrorHandler(webProperties));
            // 设置根资源路径，确保 ReleasePageServlet 能读取文件
            context.setBaseResource(Resource.newResource(PathUtils.WEBAPPS_DIR.toUri()));

            // 2.2 注册系统 Servlet (H2, Druid)
            registerSystemServlets(context);

            // 2.3 注册发布页 (首页)
            context.addServlet(new ServletHolder(new ReleasePageServlet(webProperties)), "/");

            // 2.4 注册 MVC 核心调度器
            DispatcherServlet dispatcher = new DispatcherServlet(applicationContext);
            ServletHolder mvcHolder = new ServletHolder(dispatcher);
            registerMvcRoutes(context, mvcHolder);

            // --------------------------------------------------------
            // 模块三：启动服务
            // --------------------------------------------------------
            handlers.addHandler(context);
            server.setHandler(handlers);
            server.start();

            RcsLog.consoleLog.info(StrUtil.format("Web 服务启动成功，端口：{}", webPort));
            RcsLog.algorithmLog.info(StrUtil.format("Web 服务启动成功，端口：{}", webPort));
            RcsLog.consoleLog.info(StrUtil.format("Web 根目录已挂载: {}", PathUtils.WEBAPPS_DIR.toFile().getAbsolutePath()));

            // 使用虚拟线程池挂起主线程，防止退出
            vthreadPool.execute(() -> {
                try {
                    server.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 注册系统级 Servlet 和 Filter (H2, Druid, Cors)
     */
    private void registerSystemServlets(ServletContextHandler context) {
        // H2 Console
        ServletHolder h2Holder = new ServletHolder(new WebServlet());
        h2Holder.setInitParameter("webAllowOthers", "true");

        String rcsDbH2Group = "rcs_db_h2";
        String pwd = dbSetting.getKeyByGroupAndKey(rcsDbH2Group, "password");
        //重写encodeAdminPassword的编码方法，跳过H2最短12位密码的限制
        byte[] var1 = MathUtils.secureRandomBytes(32);
        byte[] var2 = SHA256.getHashWithSalt(pwd.getBytes(StandardCharsets.UTF_8), var1);
        byte[] var3 = Arrays.copyOf(var1, 64);
        System.arraycopy(var2, 0, var3, 32, 32);
        String pwdHash = StringUtils.convertBytesToHex(var3);
        h2Holder.setInitParameter("webAdminPassword", pwdHash);
        context.addServlet(h2Holder, webProperties.getH2Path() + "/*");

        //MySQL数据库配置分组名称
        String RCS_DB_MYSQL = "rcs_db_mysql";
        String username = dbSetting.getKeyByGroupAndKey(RCS_DB_MYSQL, "username");
        String password = dbSetting.getKeyByGroupAndKey(RCS_DB_MYSQL, "password");
        // Druid Monitor
        ServletHolder druidHolder = new ServletHolder(new StatViewServlet());
        druidHolder.setInitParameter("allow", "");
        druidHolder.setInitParameter("loginUsername", username);
        druidHolder.setInitParameter("loginPassword", password);
        druidHolder.setInitParameter("resetEnable", "true");
        context.addServlet(druidHolder, webProperties.getDruidPath() + "/*");
        context.addFilter(DruidAdFilter.class, webProperties.getDruidPath() + "/js/common.js", EnumSet.of(DispatcherType.REQUEST));

        // 注册资源修复过滤器
        // 把它放在比较靠前的位置，拦截所有请求 ("/*")
        // 这样 /assets/app.js 这种请求在还没报错前就会被修正
        // 创建 Filter 实例，注入 WebProperties
        ResourceRepairFilter repairFilter = new ResourceRepairFilter(webProperties);
        // 使用 FilterHolder 包装
        FilterHolder filterHolder = new FilterHolder(repairFilter);
        context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        // CORS Filter
        context.addFilter(CorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    /**
     * 扫描 IOC 容器，注册 @RestController 路由
     */
    private void registerMvcRoutes(ServletContextHandler context, ServletHolder mvcHolder) {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);
        if (controllers.isEmpty()) {
            return;
        }

        // 标记是否已配置 Multipart，避免重复设置
        boolean multipartConfigured = false;

        for (Object bean : controllers.values()) {
            if (bean.getClass().isAnnotationPresent(RequestMapping.class)) {
                String path = bean.getClass().getAnnotation(RequestMapping.class).value();
                path = StrUtil.prependIfMissing(path, "/");
                path = StrUtil.removeSuffix(path, "/");
                String mapping = path + "/*";

                // 1. 将 Servlet 挂载到路径
                context.addServlet(mvcHolder, mapping);
                RcsLog.sysLog.info("MVC Dispatcher mapped to: {}", mapping);

                // 2. 【核心修复】手动配置 MultipartConfigElement
                // 必须在 addServlet 之后调用，此时 Registration 才可用
                if (!multipartConfigured) {
                    // 空字符串表示使用系统临时目录，且不限制文件大小
                    mvcHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(""));
                    multipartConfigured = true;
                }
            }
        }
    }

    /**
     * 添加普通静态资源路由 (无 SPA 回退)
     */
    private void addStaticResourceHandler(HandlerList handlers, String contextPath, String resourceBasePath) throws Exception {
        File file = new File(resourceBasePath);
        if (!file.exists()) {
            RcsLog.sysLog.warn("⚠️ 静态资源路径不存在: {}", file.getAbsolutePath());
            return;
        }

        ContextHandler contextHandler = new ContextHandler(contextPath);
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(Resource.newResource(file.toURI()));
        resourceHandler.setDirectoriesListed(true); // 静态资源允许列出目录(可选)
        contextHandler.setHandler(resourceHandler);
        handlers.addHandler(contextHandler);
    }

    /**
     * 添加固定 SPA 上下文 (用于 /console, /monitor 等固定入口)
     */
    private void addFixedSpaContext(HandlerList handlers, String contextPath, String resourceBasePath, String welcomeFile) throws Exception {
        File file = new File(resourceBasePath);
        if (!file.exists()) {
            return;
        }

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(contextPath);
        context.setBaseResource(Resource.newResource(file.toURI()));

        if (StrUtil.isNotBlank(welcomeFile)) {
            context.setWelcomeFiles(new String[]{welcomeFile});
        }

        ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(defaultHolder, "/");

        // 简单的 SPA 回退：所有 404 都跳回 welcomeFile
        String spaEntry = StrUtil.prependIfMissing(welcomeFile, "/");
        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.addErrorPage(404, spaEntry);
        context.setErrorHandler(errorHandler);

        handlers.addHandler(context);
    }

    /**
     * 【核心】添加动态 Webapps 上下文
     * 功能：映射 /webapps/** 到 doc/webapps/**
     * 特性：支持动态新增子项目，并提供智能 SPA 404 路由回退
     */
    private void addDynamicWebappsContext(HandlerList handlers, String contextPath, String resourceBasePath) throws IOException {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(contextPath);

        File dir = new File(resourceBasePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        context.setBaseResource(Resource.newResource(dir.toURI()));

        // 1. 设置欢迎文件 (关键修复)
        // 这样访问 /webapps/arthas-doc/doc/ 时，会自动寻找 /doc/index.html
        context.setWelcomeFiles(new String[]{"index.html", "index.htm"});

        // 2. 为动态 Webapps 也注册路径修复过滤器
        // 必须在这里也注册一遍，否则子项目里的绝对路径修复功能在 /webapps 下会失效
        ResourceRepairFilter repairFilter = new ResourceRepairFilter(webProperties);
        FilterHolder filterHolder = new FilterHolder(repairFilter);
        context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        // 3. 配置 DefaultServlet
        ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        // 开启欢迎页重定向，确保目录访问正常跳转
        defaultHolder.setInitParameter("redirectWelcome", "true");
        context.addServlet(defaultHolder, "/");

        // 4. 注入 SPA 错误处理器
        context.setErrorHandler(new DynamicSpaErrorHandler(context, contextPath, resourceBasePath));

        handlers.addHandler(context);
        RcsLog.consoleLog.info("Web 动态Webapps容器已挂载: {} -> {}", contextPath, dir.getAbsolutePath());
    }

    /**
     * 停止服务
     */
    @PreDestroy
    public void shutdown() {
        if (server != null) {
            try {
                int webPort = coreYaml.getWebPort();
                server.stop();
                RcsLog.consoleLog.warn(StrUtil.format("Web 服务停止成功，释放端口：{}", webPort));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // =================================================================================
    // 内部类：动态 SPA 错误处理器
    // =================================================================================

    /**
     * 智能 SPA 错误处理器
     * <p>
     * 当访问 /webapps/项目名/路由 时发生 404，
     * 此处理器会自动尝试寻找 /doc/webapps/项目名/index.html，
     * 如果存在，则将 index.html 内容返回给前端，从而支持 Vue/React 的 History 模式。
     * </p>
     */
    private static class DynamicSpaErrorHandler extends ErrorPageErrorHandler {
        private final ServletContextHandler context;
        private final String contextPath;
        private final String resourceBasePath;

        public DynamicSpaErrorHandler(ServletContextHandler context, String contextPath, String resourceBasePath) {
            this.context = context;
            this.contextPath = contextPath;
            this.resourceBasePath = resourceBasePath;
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            // 仅拦截 404 错误进行 SPA 回退判断
            if (response.getStatus() == 404) {
                String uri = request.getRequestURI();
                // 1. 获取子路径，例如: /webapps/console/about -> /console/about
                String subPath = uri.substring(contextPath.length());

                // 2. 提取第一级目录名作为项目名
                String[] parts = subPath.split("/");
                // parts[0] 为空, parts[1] 为项目名 (如 routetest)
                if (parts.length >= 2 && StrUtil.isNotBlank(parts[1])) {
                    String projectName = parts[1];

                    // 3. 构造 index.html 物理路径
                    File projectDir = new File(resourceBasePath, projectName);
                    File indexFile = new File(projectDir, "index.html");

                    // 4. 如果该项目下存在 index.html，则视为 SPA 项目
                    if (indexFile.exists() && indexFile.isFile()) {
                        // 重置响应，准备发送 index.html 内容
                        response.resetBuffer();
                        response.setStatus(200); // 返回 200 让前端 Router 接管
                        response.setContentType("text/html;charset=UTF-8");

                        // 流式写出文件内容 (比 forward 更稳健)
                        FileUtil.writeToStream(indexFile, response.getOutputStream());

                        baseRequest.setHandled(true);
                        return;
                    }
                }
            }
            // 其他情况 (如真的找不到文件，或不是 SPA) 执行默认错误处理
            super.handle(target, baseRequest, request, response);
        }
    }
}