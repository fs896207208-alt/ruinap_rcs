package com.ruinap.api.controller;

import com.ruinap.infra.config.GlobalConfigManager;
import com.ruinap.infra.framework.annotation.Autowired;
import com.ruinap.infra.framework.web.bind.annotation.*;

import java.util.Map;

/**
 * HTTP接口
 *
 * @author qianye
 * @create 2025-12-18 17:23
 */
@RestController
@RequestMapping("/openapi/")
public class HttpController {

    @Autowired
    private GlobalConfigManager globalConfigManager;

    // 场景1: 正常请求，带日志，有 Body，有返回值
    @AccessLog
    @PostMapping("echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> data) {
        data.put("processed", true);
        return data;
    }

    // 场景2: 不带日志注解
    @GetMapping("silent")
    public String silent() {
        return "shhh";
    }

    // 场景3: 发生异常，带日志
    @AccessLog
    @GetMapping("error")
    public void error() {
        throw new RuntimeException("Test Exception");
    }

    // 场景4: 空 Body，Void 返回值
    @AccessLog
    @PostMapping("void")
    public void voidMethod() {
        // do nothing
        globalConfigManager.reloadAll();
    }
}
