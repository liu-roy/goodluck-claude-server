package com.goodluck.ai.claude.service.controller;

import com.goodluck.ai.claude.api.model.resp.SessionInfoResponse;
import com.goodluck.ai.claude.api.model.resp.SessionMessageResponse;
import com.goodluck.ai.claude.service.service.SessionService;
import com.goodluck.common.resp.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/sessions")
@Tag(name = "会话管理", description = "ClaudeCode 会话的创建、列表与对话历史")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    @PostMapping
    @Operation(summary = "新建会话")
    public R<SessionInfoResponse> createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        SessionInfoResponse resp = sessionService.createSession();
        if (request != null && StringUtils.isNotBlank(request.getProjectName())) {
            sessionService.recordUsage(resp.getSessionId(), request.getProjectName().trim());
            resp.setProjectName(request.getProjectName().trim());
        }
        return R.success(resp);
    }

    @GetMapping
    @Operation(summary = "会话列表")
    public R<List<SessionInfoResponse>> listSessions() {
        return R.success(sessionService.listSessions());
    }

    @GetMapping("/messages")
    @Operation(summary = "对话历史", description = "获取指定会话+项目下的完整对话记录")
    public R<List<SessionMessageResponse>> getMessages(
            @Parameter(description = "会话ID") @RequestParam String sessionId,
            @Parameter(description = "项目名称") @RequestParam String projectName) {
        return R.success(sessionService.getMessages(sessionId, projectName));
    }

    @Data
    public static class CreateSessionRequest {
        private String projectName;
    }
}
