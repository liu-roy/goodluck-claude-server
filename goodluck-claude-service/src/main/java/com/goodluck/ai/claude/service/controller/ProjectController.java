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





    @PostMapping("/clone")
    @Operation(summary = "首次克隆项目", description = "将 Git 仓库克隆到工作目录，作为可用的项目；之后方可进行生成代码等操作")
    public R<ProjectInfoResponse> cloneProject(@Valid @RequestBody GitCloneRequest request) {
        try {
            ProjectInfoResponse info = projectManager.cloneProject(request);
            return R.success(info);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("克隆项目失败", e);
            return R.error(500, "克隆失败: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    @Operation(summary = "生成代码", description = "使用Claude AI生成代码（要求项目已存在，不存在时请先调用 clone）")
    @Override
    public R<ProjectInfoResponse> generateProject(
            @Valid @RequestBody CodeGenerationRequest request) {

        try {
            log.info("收到代码生成请求: {}", JsonUtil.toJSONString(request));

            ProjectInfoResponse projectInfo = projectManager.generateCode(request);

            return R.success(projectInfo);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("代码生成失败", e);
            return R.error(500, "代码生成失败: " + e.getMessage());
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
