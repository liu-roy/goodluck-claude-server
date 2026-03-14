package com.goodluck.ai.claude.service.controller;

import com.goodluck.ai.claude.service.service.ClaudeExecutorService;
import com.goodluck.common.resp.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/system")
@Tag(name = "系统监控", description = "系统健康检查和监控相关接口")
public class SystemController {

    @Autowired
    private ClaudeExecutorService claudeExecutorService;

    @GetMapping("/info")
    @Operation(summary = "检查系统信息", description = "检查服务器运行状态和基本信息")
    public R<Map<String, Object>> health() {
        try {
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", "UP");
            healthInfo.put("timestamp", LocalDateTime.now());
            healthInfo.put("claudeVersion", claudeExecutorService.getClaudeVersion());
            healthInfo.put("activeProcesses", claudeExecutorService.getActiveProcessCount());
            healthInfo.put("javaVersion", System.getProperty("java.version"));
            healthInfo.put("osName", System.getProperty("os.name"));
            healthInfo.put("osVersion", System.getProperty("os.version"));

            return R.success("系统运行正常",healthInfo);

        } catch (Exception e) {
            log.error("系统检查失败", e);
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("status", "DOWN");
            errorInfo.put("error", e.getMessage());
            errorInfo.put("timestamp", LocalDateTime.now());

            return R.error("系统检查失败");
        }
    }
}
