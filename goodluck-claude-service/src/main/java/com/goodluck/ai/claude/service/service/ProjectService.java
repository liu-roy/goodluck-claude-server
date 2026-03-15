package com.goodluck.ai.claude.service.service;

import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.ai.claude.api.model.req.CodeGenerationRequest;
import com.goodluck.ai.claude.api.model.resp.ProjectInfoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodluck.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * 项目管理服务
 */
@Slf4j
@Service
public class ProjectService {

    @Autowired
    private ClaudeProperties claudeProperties;

    @Autowired
    private ClaudeExecutorService claudeExecutorService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 生成代码项目
     */
    public ProjectInfoResponse generateCode(CodeGenerationRequest request) {
        // 确保有有效的 sessionId (UUID格式)
        boolean isNewSession = true;
        String sessionId = request.getSessionId();
        if (StringUtils.isBlank(sessionId)) {
            // 生成新的UUID格式的sessionId
            sessionId = UUID.randomUUID().toString();
            request.setSessionId(sessionId);
            log.info("未提供sessionId，自动生成: {}", sessionId);
        } else {
            // 验证是否为有效的UUID格式
            try {
                UUID.fromString(sessionId);
                isNewSession = false;
            } catch (IllegalArgumentException e) {
                throw new BusinessException("无效的sessionId: " + sessionId);
            }
        }


        log.info("执行项目操作，项目名: {}, 会话ID: {}, 提示: {}",
                request.getProjectName(), sessionId, request.getPrompt());

        try {

            // 执行 Claude 命令，传入会话ID、系统提示和是否为新项目标识
            ClaudeExecutorService.CommandResult result = claudeExecutorService.executeCommand(
                    request.getPrompt(),
                    request.getProjectName(),
                    claudeProperties.getTimeout(),
                    request.getSessionId(),
                    claudeProperties.getGenerateCodePrompt(),
                    isNewSession);

            // 扫描生成的文件
            ProjectInfoResponse projectInfo = new ProjectInfoResponse();
            projectInfo.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            projectInfo.setSessionId(sessionId);
            if (!result.isSuccess()) {
                projectInfo.setError(result.getError());
            }

            log.info("代码项目生成完成，项目ID: {}, 会话ID: {}, 状态: {}",
                    request.getProjectName(), sessionId, projectInfo.getStatus());

            return projectInfo;

        } catch (Exception e) {
            log.error("生成代码项目失败，项目ID: {}, 会话ID: {}", request.getProjectName(), sessionId, e);

            // 创建失败的项目信息
            ProjectInfoResponse projectInfo = ProjectInfoResponse.builder()
                    .projectName(request.getProjectName())
                    .sessionId(sessionId)
                    .status("FAILED")
                    .error("生成失败: " + e.getMessage())
                    .createdTime(new Date())
                    .build();

            return projectInfo;
        }
    }

    /**
     * 删除项目
     */
    public boolean deleteProject(String projectId) {
        try {
            Path projectDir = getProjectDirectory(projectId);
            if (Files.exists(projectDir)) {
                FileUtils.deleteDirectory(projectDir.toFile());
                log.info("项目删除成功，项目ID: {}", projectId);
                return true;
            }
        } catch (Exception e) {
            log.error("删除项目失败，项目ID: {}", projectId, e);
        }
        return false;
    }

    /**
     * 获取文件内容
     */
    public String getFileContent(String projectId, String filePath) throws IOException {
        Path projectDir = getProjectDirectory(projectId);
        Path fullFilePath = projectDir.resolve(filePath);

        // 安全检查
        if (!fullFilePath.normalize().startsWith(projectDir.normalize())) {
            throw new SecurityException("非法文件路径");
        }

        if (!Files.exists(fullFilePath)) {
            throw new IOException("文件不存在: " + filePath);
        }

        return Files.readString(fullFilePath);
    }

    /**
     * 获取文件
     */
    public Path getFile(String projectId, String filePath) throws IOException {
        Path projectDir = getProjectDirectory(projectId);
        Path fullFilePath = projectDir.resolve(filePath);

        // 安全检查
        if (!fullFilePath.normalize().startsWith(projectDir.normalize())) {
            throw new SecurityException("非法文件路径");
        }

        if (!Files.exists(fullFilePath)) {
            throw new IOException("文件不存在: " + filePath);
        }

        return fullFilePath;
    }

    /**
     * 生成项目ID
     */
    private String generateProjectId(String projectName) {
        if (projectName != null && !projectName.trim().isEmpty()) {
            // 使用项目名 + 时间戳
            return projectName.replaceAll("[^a-zA-Z0-9\\-_]", "") + "-" +
                    String.valueOf(System.currentTimeMillis()).substring(8);
        } else {
            // 生成随机ID
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 获取项目目录
     */
    private Path getProjectDirectory(String projectId) {
        return Paths.get(claudeProperties.getWorkspaceDir()).resolve(projectId);
    }

}
