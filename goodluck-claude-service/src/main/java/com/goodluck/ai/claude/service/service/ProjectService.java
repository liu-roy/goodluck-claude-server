package com.goodluck.ai.claude.service.service;

import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.ai.claude.api.model.req.CodeGenerationRequest;
import com.goodluck.ai.claude.api.model.req.GitCloneRequest;
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

    @Autowired
    private GitService gitService;

    /**
     * 校验项目是否存在（工作目录下是否存在对应目录）
     */
    public boolean projectExists(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return false;
        }
        Path projectDir = getProjectDirectory(projectId);
        return Files.exists(projectDir) && Files.isDirectory(projectDir);
    }

    /**
     * 克隆 Git 仓库到工作目录，作为新项目
     *
     * @param request 克隆请求（gitUrl 必填，branch、projectName 可选）
     * @return 项目信息，含 projectName
     */
    public ProjectInfoResponse cloneProject(GitCloneRequest request) {
        String projectName = StringUtils.isNotBlank(request.getProjectName())
                ? request.getProjectName().trim()
                : parseProjectNameFromGitUrl(request.getGitUrl());
        if (StringUtils.isBlank(projectName)) {
            throw new BusinessException("无法从 gitUrl 解析项目名，请传入 projectName");
        }
        Path targetDir = getProjectDirectory(projectName);
        if (Files.exists(targetDir)) {
            throw new BusinessException("项目已存在: " + projectName + "，请更换 projectName 或先删除该目录");
        }
        boolean ok = gitService.cloneRepository(
                request.getGitUrl(),
                request.getBranch(),
                targetDir,
                request.getGitUsername(),
                request.getGitPassword());
        if (!ok) {
            throw new BusinessException("克隆失败，请检查 gitUrl、分支及网络/凭证");
        }
        ProjectInfoResponse resp = new ProjectInfoResponse();
        resp.setProjectName(projectName);
        resp.setStatus("SUCCESS");
        resp.setCreatedTime(new Date());
        return resp;
    }

    private static String parseProjectNameFromGitUrl(String gitUrl) {
        if (gitUrl == null || gitUrl.isEmpty()) return null;
        String s = gitUrl.trim();
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        int last = s.lastIndexOf('/');
        return last >= 0 ? s.substring(last + 1) : null;
    }

    /**
     * 生成代码项目
     */
    public ProjectInfoResponse generateCode(CodeGenerationRequest request) {
        // 入口校验：项目必须已存在（需先 clone）
        if (!projectExists(request.getProjectName())) {
            throw new BusinessException("项目不存在: " + request.getProjectName() + "，请先通过 /projects/clone 接口克隆仓库");
        }
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
