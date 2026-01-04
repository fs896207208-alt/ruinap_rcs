package com.ruinap.infra.framework.web.servlet;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruinap.infra.framework.core.ApplicationContext;
import com.ruinap.infra.framework.web.bind.annotation.*;
import com.ruinap.infra.framework.web.http.AjaxResult;
import com.ruinap.infra.log.RcsLog;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 【核心组件】Mini-SpringMVC 中央调度器 (Ultimate Version)
 * <p>
 * 1. 支持标准 RESTful 路由映射与参数绑定。
 * 2. 支持 JSON / Form-Data / UrlEncoded 多种请求体格式。
 * 3. 强制统一输出 AjaxResult 格式响应。
 * 4. 集成全自动 AOP 审计日志。
 *
 * @author qianye
 * @create 2025-12-15 18:05
 */
@MultipartConfig
public class DispatcherServlet extends HttpServlet {

    private final ApplicationContext applicationContext;
    private final Map<String, HandlerMethod> handlerMapping = new HashMap<>();

    public DispatcherServlet(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void init() throws ServletException {
        // 1. 从 IOC 容器中获取所有 Controller
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);

        // 2. 遍历构建路由
        for (Object controller : controllers.values()) {
            Class<?> clazz = controller.getClass();
            String basePath = "";

            // --- 处理类级别的 @RequestMapping (BasePath) ---
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                basePath = clazz.getAnnotation(RequestMapping.class).value();
                // 规范化: 确保以 / 开头，且不以 / 结尾
                if (StrUtil.isNotEmpty(basePath) && !basePath.startsWith("/")) {
                    basePath = "/" + basePath;
                }
                if (basePath.endsWith("/")) {
                    basePath = basePath.substring(0, basePath.length() - 1);
                }
            }

            // --- 遍历方法，处理方法级别的映射 ---
            for (Method method : clazz.getDeclaredMethods()) {
                String path = null;
                String httpMethod = "GET";

                if (method.isAnnotationPresent(GetMapping.class)) {
                    path = method.getAnnotation(GetMapping.class).value();
                    httpMethod = "GET";
                } else if (method.isAnnotationPresent(PostMapping.class)) {
                    path = method.getAnnotation(PostMapping.class).value();
                    httpMethod = "POST";
                } else if (method.isAnnotationPresent(RequestMapping.class)) {
                    path = method.getAnnotation(RequestMapping.class).value();
                    httpMethod = "ALL";
                }

                if (path != null) {
                    // 规范化: 确保方法 path 以 / 开头
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    // 拼接完整路径
                    String fullPath = basePath + path;
                    // 注册到路由表
                    handlerMapping.put(fullPath, new HandlerMethod(controller, method, httpMethod));
                    RcsLog.sysLog.info("Mapped: [{}] {}", httpMethod, fullPath);
                }
            }
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        String methodType = req.getMethod();

        // 统一设置响应头
        resp.setContentType("application/json;charset=utf-8");

        long start = System.currentTimeMillis();
        String requestBodyLog = "{}";
        String resultLog = "{}";
        boolean responseWritten = false; // 标记响应是否已写入

        HandlerMethod handler = null;

        try {
            // 1. 查找路由 (处理 404)
            handler = handlerMapping.get(uri);
            if (handler == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                // 返回统一 404 JSON
                JSONObject errorObj = AjaxResult.error("Request path not found: " + uri);
                errorObj.put("code", 404);

                resultLog = errorObj.toString();
                resp.getWriter().write(resultLog);
                responseWritten = true;
                return;
            }

            // 2. 方法校验 (处理 405)
            if (!"ALL".equals(handler.httpMethod) && !handler.httpMethod.equalsIgnoreCase(methodType) && !"OPTIONS".equals(methodType)) {
                resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                // 返回统一 405 JSON
                JSONObject errorObj = AjaxResult.error("Request method '" + methodType + "' not supported");
                errorObj.put("code", 405);

                resultLog = errorObj.toString();
                resp.getWriter().write(resultLog);
                responseWritten = true;
                return;
            }

            // 3. 参数绑定 (包含 JSON/Form 解析)
            ParamBindResult bindResult = resolveParameters(req, handler.method);
            if (StrUtil.isNotEmpty(bindResult.bodyString)) {
                // 压缩请求体日志，去除换行
                requestBodyLog = compactJson(bindResult.bodyString);
            }

            // 4. 反射调用
            Object result = handler.method.invoke(handler.bean, bindResult.args);

            // 5. 统一响应封装
            JSONObject responseJson;
            if (result == null) {
                responseJson = AjaxResult.success();
            } else if (result instanceof AjaxResult) {
                // 如果 Controller 已经返回了 AjaxResult，直接转 JSON
                responseJson = new JSONObject(result);
            } else if (result instanceof JSONObject) {
                // 已经是 JSONObject 视为数据
                responseJson = (JSONObject) result;
            } else {
                // 其他类型包裹在 data 中
                responseJson = AjaxResult.success(result);
            }

            // 序列化并写入
            // 使用 compactJson 确保日志和返回给前端的都是单行紧凑格式
            resultLog = compactJson(responseJson.toString());
            resp.getWriter().write(resultLog);
            responseWritten = true;

        } catch (Exception e) {
            // 6. 统一异常处理 (500)
            resp.setStatus(500);

            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();

            // 封装异常信息
            JSONObject errorObj = AjaxResult.error(errorMsg);

            resultLog = errorObj.toString();
            if (!responseWritten) {
                resp.getWriter().write(resultLog);
            }

        } finally {
            // 7. 审计日志记录
            if (handler != null && handler.method.isAnnotationPresent(AccessLog.class)) {
                long duration = System.currentTimeMillis() - start;
                logMethodInvocation(uri, methodType, requestBodyLog, resultLog, duration);
            }
        }
    }

    /**
     * 参数解析器 (全能版：智能识别 JSON / Form-Data / UrlEncoded)
     */
    private ParamBindResult resolveParameters(HttpServletRequest req, Method method) throws IOException {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        String bodyCache = null;

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> type = param.getType();

            // A. 注入 HttpServletRequest
            if (type.equals(HttpServletRequest.class)) {
                args[i] = req;
            }
            // B. 处理 @RequestBody
            else if (param.isAnnotationPresent(RequestBody.class)) {
                String contentType = req.getContentType();
                if (contentType == null) {
                    contentType = "";
                }

                // 分支 1: JSON
                if (contentType.toLowerCase().contains("application/json")) {
                    if (bodyCache == null) {
                        bodyCache = IoUtil.read(req.getReader());
                    }
                    if (StrUtil.isNotEmpty(bodyCache)) {
                        try {
                            args[i] = JSONUtil.toBean(bodyCache, type);
                        } catch (Exception e) {
                            RcsLog.sysLog.warn("JSON解析失败: {}", e.getMessage());
                        }
                    }
                }
                // 分支 2: Form Data / UrlEncoded
                else {
                    // 利用容器能力解析 (需配合 @MultipartConfig)
                    Map<String, String[]> paramMap = req.getParameterMap();
                    if (!paramMap.isEmpty()) {
                        Map<String, Object> flatMap = new HashMap<>();
                        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                            // 简化处理：取第一个值，如果是数组可以扩展逻辑
                            if (entry.getValue().length == 1) {
                                flatMap.put(entry.getKey(), entry.getValue()[0]);
                            } else {
                                flatMap.put(entry.getKey(), entry.getValue());
                            }
                        }

                        // 伪造 JSON 字符串用于日志
                        if (bodyCache == null) {
                            bodyCache = JSONUtil.toJsonStr(flatMap);
                        }

                        // 赋值
                        if (Map.class.isAssignableFrom(type)) {
                            args[i] = flatMap;
                        } else {
                            args[i] = JSONUtil.toBean(JSONUtil.toJsonStr(flatMap), type);
                        }
                    }
                }

                // 保底策略: 防止 NPE
                if (args[i] == null) {
                    if (Map.class.isAssignableFrom(type)) {
                        args[i] = new HashMap<>();
                    } else {
                        try {
                            args[i] = type.getDeclaredConstructor().newInstance();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            // C. 处理 @RequestParam
            else if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = param.getAnnotation(RequestParam.class);
                String paramName = StrUtil.isEmpty(rp.value()) ? param.getName() : rp.value();
                String value = req.getParameter(paramName);

                if (value == null && rp.required()) {
                    throw new IllegalArgumentException("Missing required parameter: " + paramName);
                }
                args[i] = Convert.convert(type, value);
            }
        }
        return new ParamBindResult(args, bodyCache);
    }

    /**
     * 【日志优化】将 JSON 字符串压缩为单行 (移除换行和多余空格)
     */
    private String compactJson(String jsonStr) {
        if (StrUtil.isEmpty(jsonStr)) {
            return "{}";
        }
        try {
            // 利用 Hutool 识别并重新输出：toString() 默认输出紧凑格式
            if (JSONUtil.isTypeJSON(jsonStr)) {
                return JSONUtil.parse(jsonStr).toString();
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        // 暴力移除换行符
        return StrUtil.removeAll(jsonStr, '\r', '\n');
    }

    /**
     * 记录日志
     */
    private void logMethodInvocation(String method, String httpType, String requestBody, String result, long duration) {
        // 这里的 requestBody 和 result 已经被 compactJson 处理过，没有换行
        if (StrUtil.isEmpty(requestBody)) {
            requestBody = "{}";
        }
        if (StrUtil.isEmpty(result)) {
            result = "{}";
        }

        // 按照你指定的 RcsLog 格式记录
        RcsLog.httpLog.info(RcsLog.getTemplate(6),
                RcsLog.randomInt(),
                method,
                httpType,
                new JSONObject(requestBody).toString(),
                new JSONObject(result).toString(),
                duration + "ms");
    }

    // 内部类：简单的 Handler 包装
    private record HandlerMethod(Object bean, Method method, String httpMethod) {
    }

    // 内部类：用于传递解析结果
    private record ParamBindResult(Object[] args, String bodyString) {
    }
}