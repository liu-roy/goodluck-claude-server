package com.goodluck.ai.claude.service.manager;

import com.google.common.collect.Lists;
import com.goodluck.ai.claude.api.model.req.*;
import com.goodluck.ai.claude.service.annotation.AutoGitCommit;
import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.ai.claude.service.config.IgnoreModifyConfig;
import com.goodluck.ai.claude.service.constant.ErrorCode;
import com.goodluck.ai.claude.api.model.resp.*;
import com.goodluck.ai.claude.api.model.resp.ProjectInfoResponse;
import com.goodluck.ai.claude.service.service.ClaudeExecutorService;
import com.goodluck.ai.claude.service.service.GitService;
import com.goodluck.ai.claude.service.service.ProjectService;
import com.goodluck.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.ResetCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * 项目管理Manager
 * 用于协调多个Service之间的调用
 */
@Slf4j
@Component
public class ProjectManager {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ClaudeExecutorService claudeExecutorService;

    @Autowired
    private GitService gitService;

    @Autowired
    private ClaudeProperties claudeProperties;

    @Autowired
    private IgnoreModifyConfig ignoreModifyConfig;
    /**
     * 克隆Git仓库并初始化Claude规则
     * 使用整合后的GitlabService，自动选择JGit或GitLab API
     *
     * @param request Git克隆请求
     * @return 包含sessionId的项目信息
     */
    @AutoGitCommit(messageFromParam = "", defaultMessage = "Initialize Claude rules and project setup",
            projectNameFromParam = "", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse cloneAndInitProject(GitCloneRequest request) {

        // 生成sessionId
        String sessionId = UUID.randomUUID().toString();
        log.info("生成sessionId: {}", sessionId);

        String projectName = extractProjectNameFromGitUrl(request.getGitUrl());

        Path projectDir = getProjectDirectory(projectName);
        try {
            Files.createDirectories(projectDir);
        } catch (IOException e) {
            throw new BusinessException(e);
        }

        // 第一步：使用整合的GitService克隆仓库（内部会选择JGit或GitLab API）
        log.info("开始克隆Git仓库: {} 到项目: {}", request.getGitUrl(), projectName);

        boolean cloneSuccess = gitService.cloneRepository(request.getGitUrl(), request.getBranch(), projectDir);

        if (!cloneSuccess) {
            throw new BusinessException("Git仓库克隆失败");
        }

        // 第二步：执行 claude init 命令生成规则
        log.info("执行claude init命令，项目ID: {}, sessionId: {}", projectName, sessionId);
        ClaudeExecutorService.CommandResult initResult = claudeExecutorService.executeCommand(
                claudeProperties.getInitCommand(),
                projectName,
                claudeProperties.getTimeout(),
                sessionId,
                null,
                true);

        // 扫描项目文件
        ProjectInfoResponse projectInfo = new ProjectInfoResponse();
        projectInfo.setProjectName(projectName);
        projectInfo.setStatus(initResult.isSuccess() ? "SUCCESS" : "FAILED");
        projectInfo.setSessionId(sessionId);

        if (!initResult.isSuccess()) {
            projectInfo.setError(initResult.getError());
        }

        log.info("Git克隆和Claude初始化完成，项目ID: {}, sessionId: {}", projectName, sessionId);
        return projectInfo;

    }

    @AutoGitCommit(messageFromParam = "", defaultMessage = "Initialize Claude rules",
            projectNameFromParam = "", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse claudeInitProject(ClaudeInitRequest request) {

        // 生成sessionId
        String sessionId = UUID.randomUUID().toString();
        log.info("生成sessionId: {}", sessionId);

        String projectName = request.getProjectName();

        Path projectDir = getProjectDirectory(projectName);
        if (!Files.exists(projectDir)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_EXIST, "项目不存在");
        }

        // 第二步：执行 claude init 命令生成规则
        log.info("执行claude init命令，项目ID: {}, sessionId: {}", projectName, sessionId);
        ClaudeExecutorService.CommandResult initResult = claudeExecutorService.executeCommand(
                claudeProperties.getInitCommand(),
                projectName,
                claudeProperties.getTimeout(),
                sessionId,
                null,
                true);

        // 扫描项目文件
        ProjectInfoResponse projectInfo = new ProjectInfoResponse();
        projectInfo.setProjectName(projectName);
        projectInfo.setStatus(initResult.isSuccess() ? "SUCCESS" : "FAILED");
        projectInfo.setSessionId(sessionId);

        if (!initResult.isSuccess()) {
            projectInfo.setError(initResult.getError());
        }

        log.info("Claude初始化完成，项目ID: {}, sessionId: {}", projectName, sessionId);
        return projectInfo;
    }

    public ProjectInfoResponse gitClone(GitCloneRequest request) {

        String projectName = extractProjectNameFromGitUrl(request.getGitUrl());

        Path projectDir = getProjectDirectory(projectName);
        if (Files.exists(projectDir)) {
            throw new BusinessException("项目已存在");
        }
        try {
            Files.createDirectories(projectDir);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        // 第一步：使用整合的GitService克隆仓库（内部会选择JGit或GitLab API）
        log.info("开始克隆Git仓库: {} 到项目: {}", request.getGitUrl(), projectName);

        boolean cloneSuccess = gitService.cloneRepository(request.getGitUrl(), request.getBranch(), projectDir);

        if (!cloneSuccess) {
            throw new BusinessException("Git仓库克隆失败");
        }

        // 扫描项目文件
        ProjectInfoResponse projectInfo = new ProjectInfoResponse();
        projectInfo.setProjectName(projectName);
        projectInfo.setStatus("SUCCESS");
        log.info("Git克隆完成，项目ID: {}", projectName);
        return projectInfo;

    }


    @AutoGitCommit(messageFromParam = "gitCommitMessage", defaultMessage = "AI generated code",
            projectNameFromParam = "projectName", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse generalGenerateCode(CodeGenerationRequest request) {
        // 生成sessionId
        String sessionId = request.getSessionId();
        log.info("sessionId: {}, projectName: {}", sessionId, request.getProjectName());
        String projectName = request.getProjectName();
        Path projectDir = getProjectDirectory(projectName);
        if (!Files.exists(projectDir)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_EXIST, "项目不存在");
        }

        // 第二步：执行 claude init 命令生成规则
        log.info("执行claude 命令，项目ID: {}, sessionId: {}", projectName, sessionId);
        ClaudeExecutorService.CommandResult initResult = claudeExecutorService.executeCommand(
                request.getPrompt(),
                projectName,
                claudeProperties.getTimeout(),
                sessionId,
                request.getSystemPrompt(),
                false);

        // 扫描项目文件
        ProjectInfoResponse projectInfo = new ProjectInfoResponse();
        projectInfo.setStatus(initResult.isSuccess() ? "SUCCESS" : "FAILED");
        projectInfo.setSessionId(sessionId);
        projectInfo.setProjectName(projectName);

        if (!initResult.isSuccess()) {
            projectInfo.setError(initResult.getError());
        }

        log.info("生成代码完成，项目ID: {}, sessionId: {}", projectName, sessionId);
        return projectInfo;


    }


    /**
     * 删除项目（委托给ProjectService）
     */
    public boolean deleteProject(String projectId) {
        return projectService.deleteProject(projectId);
    }

    /**
     * 获取文件内容（委托给ProjectService）
     */
    public String getFileContent(String projectId, String filePath) throws Exception {
        return projectService.getFileContent(projectId, filePath);
    }

    /**
     * 获取文件路径（委托给ProjectService）
     */
    public Path getFile(String projectId, String filePath) throws Exception {
        return projectService.getFile(projectId, filePath);
    }

    /**
     * 从Git URL中提取项目名
     */
    private String extractProjectNameFromGitUrl(String gitUrl) {
        try {
            String url = gitUrl.trim();
            if (url.endsWith(".git")) {
                url = url.substring(0, url.length() - 4);
            }
            int lastSlash = url.lastIndexOf("/");
            if (lastSlash >= 0) {
                return url.substring(lastSlash + 1);
            }
        } catch (Exception e) {
            log.warn("无法从Git URL提取项目名: {}", gitUrl);
        }
        return "git-project-" + System.currentTimeMillis();
    }

    /**
     * 获取项目目录
     */
    private Path getProjectDirectory(String projectName) {
        return Paths.get(claudeProperties.getWorkspaceDir()).resolve(projectName);
    }


    /**
     * 创建并推送新分支
     */
    public ProjectInfoResponse createAndPushBranch(GitBranchRequest request) {
        String projectName = request.getProjectName();
        String sourceBranch = request.getSourceBranch();
        String newBranch = request.getNewBranch();

        Path projectDir = getProjectDirectory(projectName);
        if (!Files.exists(projectDir)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_EXIST, "项目不存在");
        }

        log.info("开始创建新分支: {} -> {}, 项目: {}", sourceBranch, newBranch, projectName);

        // 1. 切换到源分支并拉取最新代码
        gitService.checkout(projectDir, sourceBranch);
        gitService.pull(projectDir, "origin", sourceBranch);

        // 2. 创建新分支
        boolean createSuccess = gitService.createBranch(projectDir, newBranch, sourceBranch);
        if (!createSuccess) {
            throw new BusinessException("创建分支失败");
        }

        // 3. 切换到新分支
        gitService.checkout(projectDir, newBranch);

        // 4. 推送到远程
        boolean pushSuccess = gitService.push(projectDir, "origin", newBranch, null, null);
        if (!pushSuccess) {
            throw new BusinessException("推送到远程失败");
        }

        ProjectInfoResponse projectInfo = new ProjectInfoResponse();
        projectInfo.setProjectName(projectName);
        projectInfo.setStatus("SUCCESS");
        log.info("创建并推送分支成功: {}", newBranch);
        return projectInfo;
    }

    /**
     * 重置到指定提交
     */
    public ProjectInfoResponse resetToCommit(GitResetRequest request) {
        String projectName = request.getProjectName();
        String commitId = request.getCommitId();

        Path projectDir = getProjectDirectory(projectName);
        if (!Files.exists(projectDir)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_EXIST, "项目不存在");
        }
        if (StringUtils.isNotBlank(request.getBranch())) {
            gitService.checkout(projectDir, request.getBranch());
        }
        log.info("开始重置项目: {}, commitId: {}",
                projectName, commitId);

        boolean success = gitService.reset(projectDir, commitId, ResetCommand.ResetType.HARD.name(), true);
        if (!success) {
            throw new BusinessException("Git重置失败");
        }

        ProjectInfoResponse projectInfo = new ProjectInfoResponse();
        projectInfo.setProjectName(projectName);
        projectInfo.setStatus("SUCCESS");
        log.info("重置成功: commitId {}", commitId);
        return projectInfo;
    }


    /**
     * 生成代码项目（委托给ProjectService）
     */
    @Deprecated
    @AutoGitCommit(messageFromParam = "gitCommitMessage", defaultMessage = "AI generated code by Claude",
            projectNameFromParam = "projectName", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse generateCode(CodeGenerationRequest request) {
        return projectService.generateCode(request);
    }

    @Deprecated
    @AutoGitCommit(messageFromParam = "gitCommitMessage", defaultMessage = "AI generated code - generate sql",
            projectNameFromParam = "projectName", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse generateSql(CodeGenerationRequest request) {
        // 生成sessionId
        String sessionId = request.getSessionId();
        log.info("sessionId: {}", sessionId);
        String projectName = request.getProjectName();
        Path projectDir = getProjectDirectory(projectName);
        if (!Files.exists(projectDir)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_EXIST, "项目不存在");
        }
        try {
            // 第二步：执行 claude init 命令生成规则
            log.info("执行claude 命令，项目ID: {}, sessionId: {}", projectName, sessionId);
            ClaudeExecutorService.CommandResult initResult = claudeExecutorService.executeCommand(
                    request.getPrompt(),
                    projectName,
                    claudeProperties.getTimeout(),
                    sessionId,
                    claudeProperties.loadClaudeGenerateSqlPrompt(),
                    false);

            // 扫描项目文件
            ProjectInfoResponse projectInfo = new ProjectInfoResponse();
            projectInfo.setStatus(initResult.isSuccess() ? "SUCCESS" : "FAILED");
            projectInfo.setSessionId(sessionId);
            projectInfo.setProjectName(projectName);

            if (!initResult.isSuccess()) {
                projectInfo.setError(initResult.getError());
            }

            log.info("生成sql代码完成，项目ID: {}, sessionId: {}", projectName, sessionId);
            return projectInfo;

        } catch (Exception e) {
            log.error("生成sql代码失败", e);

            return ProjectInfoResponse.builder()
                    .projectName(projectName)
                    .sessionId(sessionId)
                    .status("FAILED")
                    .error("代码sql生成失败: " + e.getMessage())
                    .createdTime(new Date())
                    .build();
        }
    }

    @Deprecated
    @AutoGitCommit(messageFromParam = "prompts", defaultMessage = "AI generated code - step by step implementation",
            projectNameFromParam = "projectName", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse batchStepByStepImplTodo(BatchCodeGenerationRequest request) {
        String projectName = request.getProjectName();
        String sessionId = request.getSessionId();

        log.info("开始批量执行TODO实现，项目: {}, sessionId: {}, 提示词数量: {}",
                projectName, sessionId, request.getPrompts().size());

        ProjectInfoResponse responseBuilder = ProjectInfoResponse.builder()
                .projectName(projectName)
                .sessionId(sessionId)
                .build();

        int successCount = 0;
        int failureCount = 0;
        ProjectInfoResponse lastProjectInfo = null;

        // 逐个执行每个提示词
        for (int i = 0; i < request.getPrompts().size(); i++) {
            String prompt = request.getPrompts().get(i);
            long startTime = System.currentTimeMillis();

            log.info("执行第 {}/{} 个提示词: {}", i + 1, request.getPrompts().size(), prompt);

            try {
                // 创建单个请求
                CodeGenerationRequest singleRequest = CodeGenerationRequest.builder()
                        .prompt(prompt)
                        .projectName(projectName)
                        .sessionId(sessionId)
                        .build();

                // 执行单个提示词（不使用注解，避免重复提交）
                ProjectInfoResponse projectInfo = executeStepByStepImplTodoWithoutCommit(singleRequest);

                long duration = System.currentTimeMillis() - startTime;

                if ("SUCCESS".equals(projectInfo.getStatus())) {
                    successCount++;
                    responseBuilder.setStatus("SUCCESS");
                    lastProjectInfo = projectInfo;
                    log.info("第 {}/{} 个提示词执行成功，耗时: {}ms", i + 1, request.getPrompts().size(), duration);
                } else {
                    failureCount++;
                    responseBuilder.setStatus("FAILED");
                    responseBuilder.setError(projectInfo.getError());
                    log.error("第 {}/{} 个提示词执行失败: {}", i + 1, request.getPrompts().size(), projectInfo.getError());

                    // 如果不继续执行，直接退出
                    if (!Boolean.TRUE.equals(request.getContinueOnFailure())) {

                        log.warn("遇到失败且未配置继续执行，停止批量处理");
                        break;
                    }
                }
                ;

            } catch (Exception e) {
                failureCount++;
                long duration = System.currentTimeMillis() - startTime;

                log.error("第 {}/{} 个提示词执行异常", i + 1, request.getPrompts().size(), e);
                responseBuilder.setStatus("FAIL");
                responseBuilder.setError("执行异常: " + e.getMessage());

                // 如果不继续执行，直接退出
                if (!Boolean.TRUE.equals(request.getContinueOnFailure())) {
                    log.warn("遇到异常且未配置继续执行，停止批量处理");
                    break;
                }
            }

        }

        // 确定整体状态
        String overallStatus;
        if (failureCount == 0) {
            overallStatus = "SUCCESS";
        } else if (successCount == 0) {
            overallStatus = "FAILED";
        } else {
            overallStatus = "PARTIAL_SUCCESS";
        }

        // 如果有最后的项目信息，设置到响应中
        if (lastProjectInfo != null) {
            lastProjectInfo.setSessionId(sessionId);
        }

        log.info("批量执行完成，项目: {}, 总数: {}, 成功: {}, 失败: {}, 状态: {}",
                projectName, request.getPrompts().size(), successCount, failureCount, overallStatus);

        return responseBuilder;
    }

    /**
     * 执行单个TODO实现（不触发Git自动提交）
     * 仅供批量执行内部调用
     */
    private ProjectInfoResponse executeStepByStepImplTodoWithoutCommit(CodeGenerationRequest request) {
        String sessionId = request.getSessionId();
        String projectName = request.getProjectName();

        try {
            log.debug("执行claude命令，项目: {}, sessionId: {}, prompt: {}",
                    projectName, sessionId, request.getPrompt());

            ClaudeExecutorService.CommandResult result = claudeExecutorService.executeCommand(
                    request.getPrompt(),
                    projectName,
                    claudeProperties.getTimeout(),
                    sessionId,
                    claudeProperties.loadClaudeImplTodoPrompt(),
                    false);

            // 扫描项目文件
            ProjectInfoResponse projectInfo = new ProjectInfoResponse();
            projectInfo.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            projectInfo.setSessionId(sessionId);
            projectInfo.setProjectName(projectName);

            if (!result.isSuccess()) {
                projectInfo.setError(result.getError());
            }

            return projectInfo;

        } catch (Exception e) {
            log.error("执行TODO实现失败", e);

            return ProjectInfoResponse.builder()
                    .projectName(projectName)
                    .sessionId(sessionId)
                    .status("FAILED")
                    .error("代码生成失败: " + e.getMessage())
                    .createdTime(new Date())
                    .build();
        }
    }


    @Deprecated
    @AutoGitCommit(messageFromParam = "gitCommitMessage", defaultMessage = "AI generated code - step by step implementation",
            projectNameFromParam = "projectName", skipOnFailure = true, autoPush = true)
    public ProjectInfoResponse stepByStepImplTodo(CodeGenerationRequest request) {
        // 生成sessionId
        String sessionId = request.getSessionId();
        log.info("sessionId: {}, projectName: {}", sessionId, request.getProjectName());
        String projectName = request.getProjectName();
        Path projectDir = getProjectDirectory(projectName);
        if (!Files.exists(projectDir)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_EXIST, "项目不存在");
        }
        try {
            // 第二步：执行 claude init 命令生成规则
            log.info("执行claude 命令，项目ID: {}, sessionId: {}", projectName, sessionId);
            ClaudeExecutorService.CommandResult initResult = claudeExecutorService.executeCommand(
                    request.getPrompt(),
                    projectName,
                    claudeProperties.getTimeout(),
                    sessionId,
                    claudeProperties.loadClaudeImplTodoPrompt(),
                    false);

            // 扫描项目文件
            ProjectInfoResponse projectInfo = new ProjectInfoResponse();
            projectInfo.setStatus(initResult.isSuccess() ? "SUCCESS" : "FAILED");
            projectInfo.setSessionId(sessionId);
            projectInfo.setProjectName(projectName);

            if (!initResult.isSuccess()) {
                projectInfo.setError(initResult.getError());
            }

            log.info("生成代码完成，项目ID: {}, sessionId: {}", projectName, sessionId);
            return projectInfo;

        } catch (Exception e) {
            log.error("生成代码失败", e);

            return ProjectInfoResponse.builder()
                    .projectName(projectName)
                    .sessionId(sessionId)
                    .status("FAILED")
                    .error("代码生成失败: " + e.getMessage())
                    .createdTime(new Date())
                    .build();
        }

    }

    public GitDiffResponse getLocalCommitDiff(GitDiffRequest request) {
        String projectName = request.getProjectName();
        String sourceCommitId = request.getSourceCommitId();
        String targetCommitId = request.getTargetCommitId();
        Path projectDir = null;
        //检查入参是否合法如果不合法不报错，组装返回结果
        if (projectName==null || projectName.isBlank()
                || sourceCommitId==null || sourceCommitId.isBlank()) {
            log.error("ProjectMapper-getLocalCommitDiff 入参信息有误");
            throw new BusinessException("入参信息有误projectName:"+projectName+" sourceCommitId:"+sourceCommitId+" targetCommitId:"+targetCommitId);
        }
        if(sourceCommitId.equals(targetCommitId)) {
            targetCommitId = "";
        }
        if (!Files.exists(projectDir = getProjectDirectory(projectName))) {
            throw new BusinessException("项目不存在,projectName:"+projectName);
        }
        //不能修改的文件
        //ignoreFiles
        List<String> ignoreFiles = Optional.ofNullable(ignoreModifyConfig.getIgnoreModifyFiles())
                .orElse(new ArrayList<>());
        //预期修改的文件列表
     /*   List<String> expectedChangedFiles = Optional.ofNullable(request.getRequirements())
                .orElse(Collections.emptyList()).stream()
                .map(req -> req.getExpectedChangedFiles())  // Stream<List<ExpectedChangedFile>>
                .flatMap(list -> list.isEmpty() ? Stream.empty() : list.stream())  // 空列表时提供默认值
                .map(GitDiffRequest.ExpectedChangedFile::getFilePath)
                .toList();  // 收集为 List<String>*/
        //预期修改的文件列表
        List<String> expectedChangedFiles = Optional.ofNullable(request.getExpectedChangedFiles())
                .orElse(Collections.emptyList())
                .stream()
                .map(GitDiffRequest.ExpectedChangedFile::getFilePath)
                .toList();

        //本地仓库文件差异列表
        List<GitDiffResponse.GitCommitDiffDetail> diffDetails = Lists.newArrayList();
                try {
                    diffDetails = gitService.getLocalCommitDiff(projectDir, sourceCommitId, targetCommitId);
                }catch (Exception e){
                    log.error("分支差异比对失败：", e);
                    throw new BusinessException("分支差异比对失败");
                }

        // 错误修改的文件列表
        List<GitDiffResponse.ErrorModifiedFile> errorModifiedFiles = new ArrayList<>();
        diffDetails.parallelStream().forEach(detail -> {
            String oldFilePath = detail.getOldFilePath() == null ? "" : detail.getOldFilePath();
            String filePath = detail.getFilePath() == null ? "" : detail.getFilePath();
            String notModelNameFilePath = getStringAfterFirstSlash(detail.getFilePath());
            String notModelNameOldFilePath = getStringAfterFirstSlash(detail.getOldFilePath());
            /*
            目前oldfilePath和filePath都有值的情况是rename的状态，这里考虑如果：
             1、重命名之前的文件是不可修改的或者不在预期修改范围内的 认为出错  记录重命名之前的文件名字
             2、重名名之后的文件名是不可修改的或者不在预期修改范围内的 认为出错 记录重命名之后的文件名字
            */
            if (!filePath.isBlank() && (!expectedChangedFiles.contains(notModelNameFilePath)
                    || ignoreFiles.contains(filePath) || ignoreFiles.contains(notModelNameFilePath))) {
                errorModifiedFiles.add(GitDiffResponse.ErrorModifiedFile.builder()
                        .filePath(filePath)
                        .changeType(detail.getChangeType())
                        .build());
            }

            //修改前的文件名字
            if (!oldFilePath.isBlank() && (!expectedChangedFiles.contains(notModelNameOldFilePath)
                    || ignoreFiles.contains(oldFilePath) || ignoreFiles.contains(notModelNameOldFilePath))) {
                errorModifiedFiles.add(GitDiffResponse.ErrorModifiedFile.builder()
                        .filePath(oldFilePath)
                        .changeType(detail.getChangeType())
                        .build());
            }
  //          detail.setOldFilePath(oldFilePath);
  //          detail.setFilePath(filePath);
        });
        return GitDiffResponse.builder()
                .diffCodeResult(CollectionUtils.isEmpty(errorModifiedFiles))
                .projectName(projectName)
                .sourceCommitId(sourceCommitId)
                .targetCommitId(targetCommitId)
                .errorModifiedFileList(errorModifiedFiles)
                .diffList(diffDetails)
                .message(CollectionUtils.isEmpty(diffDetails) ? "无差异文件" : "")
                .build();
    }
    /**
     * 提取文件路径中第一条斜线之后的字符串
     * @param path 原始文件路径
     * @return 第一条斜线后的字符串（无斜线则返回原路径）
     */
    public static String getStringAfterFirstSlash(String path) {
        // 判空（避免NullPointerException）
        if (Objects.isNull(path) || path.isEmpty()) {
            return "";
        }

        // 统一路径分隔符：将反斜杠\替换为正斜杠/（处理Windows路径）
        String unifiedPath = path.replaceAll("\\\\", "/");

        // 找到第一条斜线的索引
        int firstSlashIndex = unifiedPath.indexOf('/');

        // 截取字符串：若有斜线，取index+1之后；若无，返回原路径
        if (firstSlashIndex != -1) {
            return unifiedPath.substring(firstSlashIndex + 1);
        } else {
            return path; // 无斜线时返回原路径（也可根据需求返回""）
        }
    }

}
