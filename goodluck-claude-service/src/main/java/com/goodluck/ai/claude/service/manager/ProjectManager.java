package com.goodluck.ai.claude.service.manager;

import com.google.common.collect.Lists;
import com.goodluck.ai.claude.api.model.req.*;
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
     * 获取项目目录
     */
    private Path getProjectDirectory(String projectName) {
        return Paths.get(claudeProperties.getWorkspaceDir()).resolve(projectName);
    }




    /**
     * 生成代码项目（委托给ProjectService）
     */
    public ProjectInfoResponse generateCode(CodeGenerationRequest request) {
        return projectService.generateCode(request);
    }


}
