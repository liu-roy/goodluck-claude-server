package com.goodluck.ai.claude.api.feign;

import com.goodluck.ai.claude.api.model.req.*;
import com.goodluck.ai.claude.api.model.resp.GitDiffResponse;
import com.goodluck.ai.claude.api.model.resp.ProjectInfoResponse;
import com.goodluck.common.resp.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * Project Management Feign Client
 */
@FeignClient(url = "${ai-coding-claude-service.url:http://localhost:8081}", name = "ai-coding-claude-service", contextId = "projectApiClient", path = "/projects")
public interface ProjectFeignClient {

    @PostMapping("/git-clone-init")
    R<ProjectInfoResponse> gitCloneAndInit(@Valid @RequestBody GitCloneRequest request);

    @PostMapping("/git-clone")
    R<ProjectInfoResponse> gitClone(@Valid @RequestBody GitCloneRequest request);

    @PostMapping("/git-branch")
    R<ProjectInfoResponse> createGitBranch(@Valid @RequestBody GitBranchRequest request);

    @PostMapping("/git-reset")
    R<ProjectInfoResponse> gitReset(@Valid @RequestBody GitResetRequest request);

    @PostMapping("/git/diff")
    R<GitDiffResponse> gitDiff(@RequestBody GitDiffRequest request);

    @PostMapping("/claudeInit")
    R<ProjectInfoResponse> claudeInit(@Valid @RequestBody ClaudeInitRequest request);

    @PostMapping("/generate")
    R<ProjectInfoResponse> generateProject(@Valid @RequestBody CodeGenerationRequest request);

    @PostMapping("/generateSql")
    R<ProjectInfoResponse> generateSql(@Valid @RequestBody CodeGenerationRequest request);

    @PostMapping("/delete/{projectId}")
    R<Void> deleteProject(@PathVariable("projectId") String projectId);

    @GetMapping("/files/getContent")
    R<String> getWorkSpaceFileContent(@RequestParam("projectId") String projectId, @RequestParam("filePath") String filePath);

    @GetMapping("/file/download/")
    ResponseEntity<Resource> downloadWorkSpaceFile(@RequestParam("projectId") String projectId, @RequestParam("filePath") String filePath);

}
