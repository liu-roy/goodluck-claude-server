package com.goodluck.ai.claude.service.manager;

import com.goodluck.ai.claude.api.model.req.*;
import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.ai.claude.api.model.resp.FileTreeNode;
import com.goodluck.ai.claude.api.model.resp.ProjectInfoResponse;
import com.goodluck.ai.claude.service.service.ClaudeExecutorService;
import com.goodluck.ai.claude.service.service.GitService;
import com.goodluck.ai.claude.service.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
     * 列出所有项目目录名
     */
    public List<String> listProjectIds() {
        return projectService.listProjectIds();
    }

    /**
     * 获取项目文件树
     */
    public List<FileTreeNode> getFileTree(String projectId) throws IOException {
        return projectService.getFileTree(projectId);
    }

    public void saveFileContent(String projectId, String filePath, String content) throws Exception {
        projectService.saveFileContent(projectId, filePath, content);
    }

    public String getCurrentBranch(String projectId) {
        return projectService.getCurrentBranch(projectId);
    }

    public List<String> listBranches(String projectId) {
        return projectService.listBranches(projectId);
    }

    public boolean checkoutBranch(String projectId, String branchName) {
        return projectService.checkoutBranch(projectId, branchName);
    }

    /**
     * 获取项目目录
     */
    private Path getProjectDirectory(String projectName) {
        return Paths.get(claudeProperties.getWorkspaceDir()).resolve(projectName);
    }




    /**
     * 校验项目是否存在
     */
    public boolean projectExists(String projectId) {
        return projectService.projectExists(projectId);
    }

    /**
     * 克隆仓库到工作目录（首次使用前调用）
     */
    public ProjectInfoResponse cloneProject(GitCloneRequest request) {
        return projectService.cloneProject(request);
    }

    /**
     * 生成代码项目（委托给ProjectService）
     */
    public ProjectInfoResponse generateCode(CodeGenerationRequest request) {
        return projectService.generateCode(request);
    }
}
