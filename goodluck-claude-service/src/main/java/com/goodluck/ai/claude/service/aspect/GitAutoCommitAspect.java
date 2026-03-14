package com.goodluck.ai.claude.service.aspect;

import com.goodluck.ai.claude.service.annotation.AutoGitCommit;
import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.ai.claude.api.model.resp.ProjectInfoResponse;
import com.goodluck.ai.claude.service.service.GitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Git自动提交切面
 * 基于 @AutoGitCommit 注解自动提交代码到Git仓库
 *
 * @author Claude AI
 */
@Slf4j
@Aspect
@Component
public class GitAutoCommitAspect {

    @Autowired
    private GitService gitService;

    @Autowired
    private ClaudeProperties claudeProperties;

    @Value("${git.auto-commit.enabled:true}")
    private boolean autoCommitEnabled;

    @Value("${git.auto-commit.author:Claude AI}")
    private String defaultAuthor;

    @Value("${git.auto-commit.email:claude@ai.com}")
    private String defaultEmail;

    @Value("${gitlab.access-token:}")
    private String gitlabAccessToken;

    @Value("${gitlab.host:}")
    private String gitlabHost;

    /**
     * 拦截带有 @AutoGitCommit 注解的方法
     */
    @Around("@annotation(com.goodluck.ai.claude.service.annotation.AutoGitCommit)")
    public Object autoCommitAroundMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // 检查全局开关
        if (!autoCommitEnabled) {
            log.debug("Git自动提交已禁用，跳过提交");
            return joinPoint.proceed();
        }

        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            exception = t;
            throw t;
        } finally {
            try {
                handleAutoCommit(joinPoint, result, exception);
            } catch (Exception e) {
                log.error("Git自动提交处理失败", e);
            }
        }
    }

    /**
     * 处理逻辑
     */
    private void handleAutoCommit(JoinPoint joinPoint, Object result, Throwable exception) {
        try {
            // 获取方法签名和注解
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AutoGitCommit annotation = method.getAnnotation(AutoGitCommit.class);

            if (annotation == null) {
                return;
            }

            // 检查执行状态
            boolean isFailure = (exception != null);
            ProjectInfoResponse projectInfo = null;

            if (result instanceof ProjectInfoResponse) {
                projectInfo = (ProjectInfoResponse) result;
                if (!"SUCCESS".equals(projectInfo.getStatus())) {
                    isFailure = true;
                }
            } else if (result != null) {
                log.warn("方法返回值不是ProjectInfoResponse类型，result: {}", result.getClass().getName());
            }

            // 检查执行状态
            if (annotation.skipOnFailure() && isFailure) {
                log.info("方法执行失败，根据注解配置跳过Git提交");
                return;
            }

            // 获取项目名
            String projectName = extractProjectName(joinPoint, annotation, projectInfo);
            if (StringUtils.isBlank(projectName) && annotation.skipIfProjectNameEmpty()) {
                log.warn("无法获取项目名，跳过Git提交");
                return;
            }

            // 获取commit message
            String commitMessage = extractCommitMessage(joinPoint, annotation);

            // 截断message
            commitMessage = truncateMessage(commitMessage, annotation.maxMessageLength());

            log.info("开始自动提交代码，项目: {}, commit message: {}", projectName, commitMessage);

            // 执行Git提交
            String commitId = performGitCommit(projectName, commitMessage);

            // 将commitId设置到返回结果中
            if (StringUtils.isNotBlank(commitId) && projectInfo != null) {
                projectInfo.setCommitId(commitId);
            }

            // 如果配置了自动推送
            if (annotation.autoPush()) {
                log.info("注解配置了自动推送，开始推送到远程仓库");
                performGitPush(projectName);
            }

        } catch (Exception e) {
            log.error("Git自动提交逻辑执行异常", e);
        }
    }

    /**
     * 从方法参数或返回值中提取项目名
     */
    private String extractProjectName(JoinPoint joinPoint, AutoGitCommit annotation, ProjectInfoResponse result) {
        // 优先从方法参数获取
        if (StringUtils.isNotBlank(annotation.projectNameFromParam())) {
            String fieldName = annotation.projectNameFromParam();
            Object[] args = joinPoint.getArgs();

            for (Object arg : args) {
                if (arg == null)
                    continue;

                try {
                    // 尝试调用getXxx方法
                    String methodName = "get" + StringUtils.capitalize(fieldName);
                    Method getter = arg.getClass().getMethod(methodName);
                    Object value = getter.invoke(arg);

                    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                        return (String) value;
                    }
                } catch (Exception e) {
                    // 忽略异常，继续尝试下一个参数
                }
            }
        }

        // 从返回值获取
        return result != null ? result.getProjectName() : null;
    }

    /**
     * 从方法参数中提取commit message
     */
    private String extractCommitMessage(JoinPoint joinPoint, AutoGitCommit annotation) {
        String fieldName = annotation.messageFromParam();
        if (!StringUtils.isBlank(fieldName)) {
            Object[] args = joinPoint.getArgs();
            // 尝试从方法参数获取
            for (Object arg : args) {
                if (arg == null)
                    continue;

                try {
                    // 尝试调用getXxx方法
                    String methodName = "get" + StringUtils.capitalize(fieldName);
                    Method getter = arg.getClass().getMethod(methodName);
                    Object value = getter.invoke(arg);

                    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                        return (String) value;
                    } else if (value instanceof List) {
                        // 处理 StringList 类型
                        List<?> list = (List<?>) value;
                        if (!list.isEmpty() && list.get(0) instanceof String) {
                            // 如果是字符串列表，则拼接成一个字符串
                            @SuppressWarnings("unchecked")
                            List<String> stringList = (List<String>) list;
                            return String.join(", ", stringList);
                        }
                    }
                } catch (Exception e) {
                    // 忽略异常，继续尝试下一个参数
                }
            }
        }

        // 使用默认值
        return annotation.defaultMessage();
    }

    /**
     * 执行Git提交操作
     * 
     * @return 提交成功返回commitId，否则返回null
     */
    private String performGitCommit(String projectName, String commitMessage) {
        try {
            Path projectDir = Paths.get(claudeProperties.getWorkspaceDir()).resolve(projectName);

            log.info("执行Git操作，项目目录: {}", projectDir);

            // 1. 获取分支并尝试拉取最新代码，防止冲突
            String currentBranch = gitService.getCurrentBranch(projectDir);
            if (StringUtils.isNotBlank(currentBranch)) {
                log.info("提交前尝试拉取最新代码，分支: {}", currentBranch);
                boolean pullSuccess = gitService.pull(projectDir, "origin", currentBranch);
                if (!pullSuccess) {
                    log.warn("Git pull 失败，可能是新仓库或存在冲突，将尝试继续提交");
                }
            }

            // 2. 检查仓库状态
            GitService.GitStatus status = gitService.getStatus(projectDir);
            if (status == null) {
                log.warn("无法获取Git仓库状态，可能不是Git仓库");
                return null;
            }

            if (!status.isHasChanges()) {
                log.info("没有文件变更，跳过提交");
                return null;
            }

            log.info("检测到文件变更: 新增={}, 修改={}, 删除={}, 未跟踪={}",
                    status.getAdded().size(),
                    status.getModified().size(),
                    status.getDeleted().size(),
                    status.getUntracked().size());

            // 2. 添加所有变更到暂存区
            boolean addSuccess = gitService.add(projectDir, ".");
            if (!addSuccess) {
                log.error("Git add 失败");
                return null;
            }
            log.info("成功添加文件到暂存区");

            // 3. 提交变更
            String commitId = gitService.commit(projectDir, commitMessage, defaultAuthor, defaultEmail);
            if (commitId != null) {
                log.info("成功提交到本地仓库，commit ID: {}", commitId);
                return commitId;
            } else {
                log.error("Git commit 失败");
                return null;
            }

            // 4. 推送到远程仓库（可选，根据配置决定）
            // 注意：这里可能需要认证信息
            // boolean pushSuccess = gitService.push(projectDir, "origin", null, null,
            // null);
            // if (pushSuccess) {
            // log.info("成功推送到远程仓库");
            // }

        } catch (Exception e) {
            log.error("执行Git提交操作失败，项目: {}", projectName, e);
            throw new RuntimeException("Git提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行Git推送操作
     */
    private void performGitPush(String projectName) {
        try {
            Path projectDir = Paths.get(claudeProperties.getWorkspaceDir()).resolve(projectName);

            log.info("推送到远程仓库，项目目录: {}", projectDir);

            // 调用GitService的push方法
            // 使用 "oauth2" 作为用户名，GitLab access token 作为密码
            String username = "oauth2";
            String password = gitlabAccessToken;

            if (StringUtils.isBlank(password)) {
                log.warn("GitLab access token未配置，无法推送到远程仓库");
                return;
            }

            log.debug("使用GitLab OAuth2认证进行推送");
            boolean pushSuccess = gitService.push(projectDir, "origin", null, username, password);

            if (pushSuccess) {
                log.info("成功推送到远程仓库");
            } else {
                log.error("推送到远程仓库失败");
            }

        } catch (Exception e) {
            log.error("执行Git推送操作失败，项目: {}, 错误: {}", projectName, e.getMessage());
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 截断commit message，避免过长
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null || message.trim().isEmpty()) {
            return "AI generated code by Claude";
        }

        // 去除前后空格和换行符
        String cleaned = message.trim().replaceAll("\\n+", " ");

        // 如果超过最大长度，截断并添加省略号
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength - 3) + "...";
        }

        return cleaned;
    }
}
