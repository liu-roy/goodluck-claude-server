package com.goodluck.ai.claude.service.controller;

import com.google.common.base.Throwables;
import com.goodluck.ai.claude.api.feign.ProjectFeignClient;
import com.goodluck.ai.claude.api.model.req.*;
import com.goodluck.ai.claude.api.model.resp.GitDiffResponse;
import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.common.exception.BusinessException;
import com.goodluck.common.resp.R;
import com.goodluck.ai.claude.api.model.resp.ProjectInfoResponse;
import com.goodluck.ai.claude.service.manager.ProjectManager;
import com.goodluck.common.utils.JsonUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.nio.file.Path;

/**
 * 项目管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/projects")
@Tag(name = "项目管理", description = "代码生成项目的管理接口")
@Validated
public class ProjectController implements ProjectFeignClient {

    @Autowired
    private ProjectManager projectManager;

    @Autowired
    private ClaudeProperties claudeProperties;

    @PostMapping("/git-clone-init")
    @Operation(summary = "克隆Git仓库并初始化Claude规则", description = "克隆Git仓库并执行claude init生成规则，返回sessionId")
    @Override
    public R<ProjectInfoResponse> gitCloneAndInit(
            @Valid @RequestBody GitCloneRequest request) {

        try {
            log.info("收到Git克隆并初始化请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.cloneAndInitProject(request);
            return R.success("项目初始化成功", projectInfo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Git克隆和初始化失败", e);
            return R.error(500, "项目初始化失败: " + e.getMessage());
        }
    }

    @PostMapping("/git-clone")
    @Operation(summary = "克隆Git仓库", description = "克隆Git仓库")
    @Override
    public R<ProjectInfoResponse> gitClone(
            @Valid @RequestBody GitCloneRequest request) {

        try {
            log.info("收到Git克隆请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.gitClone(request);

            return R.success("git clone成功", projectInfo);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Git克隆和初始化失败", e);
            return R.error(500, "项目初始化失败: " + e.getMessage());
        }
    }

    @PostMapping("/git-branch")
    @Operation(summary = "创建并推送新分支", description = "基于旧分支创建新分支并推送到远程仓库")
    @Override
    public R<ProjectInfoResponse> createGitBranch(
            @Valid @RequestBody GitBranchRequest request) {

        try {
            log.info("收到创建分支请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.createAndPushBranch(request);
            return R.success("创建分支成功", projectInfo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建分支失败", e);
            return R.error(500, "创建分支失败: " + e.getMessage());
        }
    }

    @PostMapping("/git-reset")
    @Operation(summary = "git 重新 reset", description = "reset到某一个提交")
    @Override
    public R<ProjectInfoResponse> gitReset(
            @Valid @RequestBody GitResetRequest request) {

        try {
            log.info("收到Git重置请求: {}", JsonUtil.toJSONString(request));
            ProjectInfoResponse projectInfo = projectManager.resetToCommit(request);
            return R.success("Git重置成功", projectInfo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Git重置失败", e);
            return R.error(500, "Git重置失败: " + e.getMessage());
        }
    }

    @PostMapping("/git/diff")
    @Operation(summary = "代码差异比对", description = "本地仓库中的两次提交进行差异比对")
    @Override
    public R<GitDiffResponse> gitDiff(@RequestBody GitDiffRequest request) {
        try {
            log.info("收到比对代码差异请求: request:{}", JsonUtil.toJSONString(request));
            GitDiffResponse response = projectManager.getLocalCommitDiff(request);
            return R.success("比对成功", response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("比对代码失败", e);
            return R.error(500, "比对代码失败: " + e.getMessage());
        }
    }

    @PostMapping("/claudeInit")
    @Operation(summary = "初始化Claude rule", description = "初始化Claude rule")
    @Override
    public R<ProjectInfoResponse> claudeInit(
            @Valid @RequestBody ClaudeInitRequest request) {

        try {
            log.info("收到claude初始化请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.claudeInitProject(request);
            if ("SUCCESS".equals(projectInfo.getStatus())) {
                log.info("Git克隆和Claude初始化成功，sessionId: {}", projectInfo.getSessionId());
                return R.success("项目初始化成功", projectInfo);
            } else {
                return R.error(500, "项目初始化失败: " + projectInfo.getError());
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Git克隆和初始化失败", e);
            return R.error(500, "项目初始化失败: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    @Operation(summary = "生成代码", description = "使用Claude AI生成代码并创建新项目")
    @Override
    public R<ProjectInfoResponse> generateProject(
            @Valid @RequestBody CodeGenerationRequest request) {

        try {
            log.info("收到代码生成请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.generalGenerateCode(request);

            return R.success(projectInfo);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("代码生成失败", e);
            return R.error(500, "代码生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/generateSql")
    @Operation(summary = "完成 sql 实现（弃用））", description = "完成 sql 生成")
    @Override
    public R<ProjectInfoResponse> generateSql(
            @Valid @RequestBody CodeGenerationRequest request) {

        try {
            log.info("收到完成 sql请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.generateSql(request);

            if ("SUCCESS".equals(projectInfo.getStatus())) {
                return R.success("sql生成成功", projectInfo);
            } else {
                return R.error(500, "sql生成失败: " + projectInfo.getError());
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("sql生成失败", e);
            return R.error(500, "sql生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete/{projectId}")
    @Operation(summary = "删除项目", description = "删除指定的项目及其所有文件")
    @Override
    public R<Void> deleteProject(
            @Parameter(description = "项目ID", example = "abc123") @PathVariable String projectId) {

        try {
            log.info("收到删除项目请求， {}", JsonUtil.toJSONString(projectId));
            boolean deleted = projectManager.deleteProject(projectId);
            if (deleted) {
                return R.success();
            } else {
                return R.error("项目不存在或删除失败");
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除项目失败，项目ID: {}", projectId, e);
            return R.error("删除项目失败: " + e.getMessage());
        }
    }

    @GetMapping("/files/getContent")
    @Operation(summary = "获取工作空间文件内容", description = "获取项目中指定文件的内容")
    @Override
    public R<String> getWorkSpaceFileContent(
            @Parameter(description = "项目名称", example = "abc123") @RequestParam String projectId,
            @Parameter(description = "文件路径绝对路径", example = "HelloWorld.java") @RequestParam String filePath) {

        try {
            if (!filePath.startsWith(claudeProperties.getWorkspaceDir())) {
                return R.error("文件路径非法,只能访问工作目录");
            }
            String content = projectManager.getFileContent(projectId, filePath);

            return R.success(content);

        } catch (BusinessException e) {
            throw e;
        } catch (SecurityException e) {
            log.error("文件访问被拒绝，项目ID: {}, 文件: {}, exception: {}", projectId, filePath, Throwables.getStackTraceAsString(e));
            return R.error("文件访问被拒绝");
        } catch (Exception e) {
            log.error("获取文件内容失败，项目ID: {}, 文件: {}", projectId, filePath, e);
            return R.error("文件不存在或读取失败: " + e.getMessage());
        }
    }

    @Override
    @GetMapping("/file/download/")
    @Operation(summary = "下载工作空间文件", description = "下载项目中的指定文件")
    public ResponseEntity<Resource> downloadWorkSpaceFile(
            @Parameter(description = "项目ID", example = "abc123") @RequestParam String projectId,
            @Parameter(description = "文件路径绝对路径", example = "HelloWorld.java") @RequestParam String filePath) {

        try {
            if (!filePath.startsWith(claudeProperties.getWorkspaceDir())) {
                log.error("文件路径非法");
                return ResponseEntity.status(403).build();
            }
            Path file = projectManager.getFile(projectId, filePath);
            Resource resource = new FileSystemResource(file.toFile());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + file.getFileName().toString() + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (BusinessException e) {
            throw e;
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("下载文件失败，项目ID: {}, 文件: {}", projectId, filePath, e);
            return ResponseEntity.notFound().build();
        }
    }
}
