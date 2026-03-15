package com.goodluck.ai.claude.service.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import com.goodluck.ai.claude.api.model.resp.GitDiffResponse;
import com.goodluck.common.exception.BusinessException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

/**
 * GitLab 服务实现类
 * 整合了JGit和GitLab API，实现完整的Git操作
 * - 使用JGit处理本地Git操作（clone, add, commit, push, pull等）
 * - 使用GitLab API处理平台特定操作（创建项目、合并请求、权限管理等）
 *
 * @author 24026533
 */
@Slf4j
@Service
public class GitServiceImpl implements GitService {

    public static final String MASTER_BRANCH = "master";

    public static final String README_FILE_NAME = "REDME.md";
    public static final String README_FILE_CONTENT = "# %s\n\n%s";

    @Value("${gitlab.host:https://hgit.goodluck.net/}")
    private String gitlabHost;

    @Value("${gitlab.access-token:}")
    private String gitLabAccessToken;

    @Value("${gitlab.default-group-id:14152}")
    private Long gitlabDefaultGroupId;

    @Value("${gitlab.webhook-enable-ssl:true}")
    private boolean gitlabWebhookEnableSsl;

    @Value("${gitlab.push-event-handle-enabled:false}")
    private boolean pushEventHandleEnabled;

    @Value("${gitlab.protect-branch-enabled:false}")
    private boolean protectBranchEnabled;

    private GitLabApi getGitLabApi() {
        try {
            return new GitLabApi(gitlabHost, gitLabAccessToken);
        } catch (Exception e) {
            throw new BusinessException("Git服务连接失败!");
        }
    }

    public Project createProject(String projectPath, String projectName, String projectDesc, List<String> userCodeList,
            String webhookUrl) {
        Project project = null;
        try {
            GitLabApi gitLabApi = getGitLabApi();
            Project paramProject = new Project();
            paramProject.setName(projectName);
            paramProject.setPath(projectPath);
            project = gitLabApi.getProjectApi().createProject(gitlabDefaultGroupId, paramProject);
            if (project == null) {
                throw new BusinessException("GitLab项目创建失败!");
            }

            Long projectId = project.getId();

            try {
                RepositoryFile repositoryFile = new RepositoryFile();
                repositoryFile.setFilePath(README_FILE_NAME);
                repositoryFile.setContent(String.format(README_FILE_CONTENT, projectName, projectDesc));
                gitLabApi.getRepositoryFileApi().createFile(projectId, repositoryFile, MASTER_BRANCH, "Initial commit");
            } catch (Exception e) {
                log.error("初始化文件失败!", e);
            }

            addMembers(userCodeList, gitLabApi, projectId);

            if (StringUtils.isNotBlank(webhookUrl)) {
                ProjectHook projectHook = new ProjectHook();
                projectHook.setMergeRequestsEvents(true);
                projectHook.setPushEvents(pushEventHandleEnabled);
                gitLabApi.getProjectApi().addHook(projectId, webhookUrl, projectHook, gitlabWebhookEnableSsl, null);
            }
        } catch (GitLabApiException e) {
            log.error("GitLab项目创建失败", e);
            throw new BusinessException("GitLab项目创建失败!");
        }

        return project;
    }

    /**
     * 添加git权限
     *
     * @param userCodeList
     * @param gitLabApi
     * @param projectId
     * @throws GitLabApiException
     */
    private void addMembers(List<String> userCodeList, GitLabApi gitLabApi, Long projectId) {
        if (CollectionUtils.isEmpty(userCodeList)) {
            return;
        }

        for (String userCode : userCodeList) {
            try {
                List<User> users = gitLabApi.getUserApi().findUsers(userCode);
                if (CollectionUtils.isEmpty(users)) {
                    continue;
                }
                Long userId = users.get(0).getId();
                gitLabApi.getProjectApi().addMember(projectId, userId, AccessLevel.DEVELOPER);
            } catch (GitLabApiException e) {
                log.error("添加git成员[" + userCode + "]失败", e);
            }
        }
    }

    public void createBranch(Long projectId, String branchName, String refBranchName) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            gitLabApi.getRepositoryApi().createBranch(projectId, branchName, refBranchName);
            if (protectBranchEnabled) {
                gitLabApi.getRepositoryApi().protectBranch(projectId, branchName);
            }
        } catch (GitLabApiException e) {
            log.error("GitLab创建分支失败 projectId={},branchName={},refBranchName={}", projectId, branchName, refBranchName);
            log.error("GitLab创建分支失败", e);
            throw new BusinessException("GitLab创建分支失败!");
        }
    }

    public Branch getBranch(Long projectId, String branchName) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            return gitLabApi.getRepositoryApi().getBranch(projectId, branchName);
        } catch (GitLabApiException e) {
            // 没获取到不用打印日志
            return null;
        }
    }

    public void deleteBranch(Long projectId, String branchName) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            gitLabApi.getRepositoryApi().deleteBranch(projectId, branchName);
        } catch (GitLabApiException e) {
            log.error("GitLab删除分支失败", e);
            throw new BusinessException("GitLab删除分支失败!");
        }
    }

    public void commitFile(Long projectId, String branchName, String commitMessage, String filePath,
            String fileContent) {
        GitLabApi gitLabApi = getGitLabApi();
        RepositoryFile repositoryFile = new RepositoryFile();
        repositoryFile.setFilePath(filePath);
        repositoryFile.setContent(fileContent);
        try {
            Optional<RepositoryFile> optionalFileInfo = gitLabApi.getRepositoryFileApi().getOptionalFileInfo(projectId,
                    filePath, branchName);
            if (optionalFileInfo.isPresent()) {
                gitLabApi.getRepositoryFileApi().updateFile(projectId, repositoryFile, branchName, commitMessage);
            } else {
                gitLabApi.getRepositoryFileApi().createFile(projectId, repositoryFile, branchName, commitMessage);
            }
        } catch (GitLabApiException e) {
            log.error("GitLab提交文件失败", e);
            throw new BusinessException("GitLab提交文件失败!");
        }
    }

    public void batchCommitFile(Long projectId, String branchName, String commitMessage,
            List<CommitAction> commitActionList) {
        if (projectId == null || StringUtils.isBlank(branchName) || CollectionUtils.isEmpty(commitActionList)) {
            return;
        }
        GitLabApi gitLabApi = getGitLabApi();
        try {

            List<List<CommitAction>> partitionList = Lists.partition(commitActionList, 800);
            for (List<CommitAction> commitActions : partitionList) {
                CommitPayload commitPayload = new CommitPayload();
                commitPayload.setBranch(branchName);
                commitPayload.setCommitMessage(commitMessage);
                commitPayload.setActions(commitActions);
                gitLabApi.getCommitsApi().createCommit(projectId, commitPayload);
            }
        } catch (GitLabApiException e) {
            log.error("批量提交文件失败", e);
            throw new BusinessException("批量提交文件失败!");
        }
    }

    public void deleteFile(Long projectId, String branchName, String commitMessage, String filePath) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            Optional<RepositoryFile> optionalFileInfo = gitLabApi.getRepositoryFileApi().getOptionalFileInfo(projectId,
                    filePath, branchName);
            if (optionalFileInfo.isPresent()) {
                gitLabApi.getRepositoryFileApi().deleteFile(projectId, filePath, branchName, commitMessage);
            }
        } catch (GitLabApiException e) {
            log.error("GitLab删除文件失败", e);
            throw new BusinessException("GitLab删除文件失败!");
        }
    }

    public MergeRequest createMergeRequest(Long projectId, String sourceBranch, String targetBranch, String title,
            String description, boolean removeSourceBranch) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            MergeRequestParams mergeRequestParams = new MergeRequestParams();
            mergeRequestParams.withSourceBranch(sourceBranch).withTargetBranch(targetBranch).withTitle(title)
                    .withDescription(description).withRemoveSourceBranch(removeSourceBranch);
            return gitLabApi.getMergeRequestApi().createMergeRequest(projectId, mergeRequestParams);
        } catch (GitLabApiException e) {
            log.error("GitLab创建合并请求失败", e);
            throw new BusinessException("GitLab创建合并请求失败!");
        }
    }

    public MergeRequest createMergeRequest(Long projectId, String sourceBranch, String targetBranch, String title,
            String description) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            return gitLabApi.getMergeRequestApi().createMergeRequest(projectId, sourceBranch, targetBranch, title,
                    description, null);
        } catch (GitLabApiException e) {
            log.error("GitLab创建合并请求失败", e);
            throw new BusinessException("GitLab创建合并请求失败!");
        }
    }

    public MergeRequest acceptMergeRequest(Long projectId, Long mergeRequestIid) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            return gitLabApi.getMergeRequestApi().acceptMergeRequest(projectId, mergeRequestIid);
        } catch (GitLabApiException e) {
            log.error("GitLab执行合并请求失败", e);
            throw new BusinessException("GitLab执行合并请求失败!");
        }
    }

    public void deleteMergeRequest(Long projectId, Long mergeRequestIid) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            gitLabApi.getMergeRequestApi().deleteMergeRequest(projectId, mergeRequestIid);
        } catch (GitLabApiException e) {
            log.error("GitLab删除合并请求失败", e);
        }
    }

    public MergeRequest getMergeRequestChanges(Long projectId, Long mergeRequestIid) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            return gitLabApi.getMergeRequestApi().getMergeRequestChanges(projectId, mergeRequestIid);
        } catch (GitLabApiException e) {
            log.error("GitLab获取变更内容失败", e);
            throw new BusinessException("GitLab获取变更内容失败!");
        }
    }

    public void createTag(Long projectId, String tagName, String refBranchName) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            gitLabApi.getTagsApi().createTag(projectId, tagName, refBranchName);
        } catch (GitLabApiException e) {
            log.error("GitLab创建Tag失败", e);
            // Tag v1.0.3 already exists
            // if (e.getMessage().startsWith("Tag") && e.getMessage().endsWith("already
            // exists")) {
            // try {
            // gitLabApi.getTagsApi().deleteTag(projectId, tagName);
            // } catch (GitLabApiException e1) {
            // log.error("GitLab删除Tag失败", e1);
            // throw new BusinessException("GitLab删除Tag失败!");
            // }
            // }
            throw new BusinessException("GitLab创建Tag失败!");
        }
    }

    public void deleteTag(Long projectId, String tagName) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            gitLabApi.getTagsApi().deleteTag(projectId, tagName);
        } catch (GitLabApiException e) {
            log.error("GitLab删除Tag失败", e);
            throw new BusinessException("GitLab删除Tag失败!");
        }
    }

    public String getFileContent(Long projectId, String branchName, String filePath) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            InputStream inputStream = gitLabApi.getRepositoryFileApi().getRawFile(projectId, branchName, filePath);
            if (inputStream == null) {
                return null;
            }

            return IOUtils.toString(inputStream);
        } catch (GitLabApiException | IOException e) {
            log.error("GitLab获取文件内容失败", e);
            return null;
        }
    }

    public void deleteFileListByDir(Long projectId, String branchName, String dir, String commitMessage) {
        if (projectId == null || StringUtils.isBlank(branchName) || StringUtils.isBlank(dir)) {
            return;
        }
        GitLabApi gitLabApi = getGitLabApi();

        try {
            List<TreeItem> tree = gitLabApi.getRepositoryApi().getTree(projectId, dir, branchName, 1000).all();
            if (CollectionUtils.isEmpty(tree)) {
                return;
            }

            List<CommitAction> actions = Lists.newArrayList();
            for (TreeItem treeItem : tree) {
                if (TreeItem.Type.TREE == treeItem.getType()) {
                    continue;
                }
                CommitAction commitAction = new CommitAction();
                commitAction.setAction(CommitAction.Action.DELETE);
                commitAction.setFilePath(treeItem.getPath());
                actions.add(commitAction);
            }

            if (CollectionUtils.isEmpty(actions)) {
                return;
            }

            CommitPayload commitPayload = new CommitPayload();
            commitPayload.setBranch(branchName);
            commitPayload.setCommitMessage(commitMessage);
            commitPayload.setActions(actions);
            gitLabApi.getCommitsApi().createCommit(projectId, commitPayload);
        } catch (GitLabApiException e) {
            log.error("删除目录下的文件异常", e);
        }
    }

    /**
     * 添加GitLab成员
     *
     * @param userCodeList
     * @param projectId
     */

    public void addMembers(List<String> userCodeList, Long projectId) {
        GitLabApi gitLabApi = getGitLabApi();
        addMembers(userCodeList, gitLabApi, projectId);
    }

    /**
     * 获取GitLab成员
     *
     * @param projectId
     * @return
     */

    public List<String> getMembers(Long projectId) {
        GitLabApi gitLabApi = getGitLabApi();
        try {
            List<Member> members = gitLabApi.getProjectApi().getMembers(projectId);
            if (CollectionUtils.isEmpty(members)) {
                return null;
            }

            return members.stream().map(Member::getName).collect(Collectors.toList());
        } catch (GitLabApiException e) {
            log.error("获取项目成员异常", e);
        }

        return null;
    }

    /**
     * 上传项目文件到GitLab
     */
    private void uploadProjectToGitLab(Long projectId, String branchName, String localPath) {
        try {
            List<CommitAction> commitActions = new ArrayList<>();
            Path rootPath = Paths.get(localPath);

            // 遍历所有文件
            try (Stream<Path> paths = Files.walk(rootPath)) {
                List<Path> files = paths
                        .filter(Files::isRegularFile)
                        .filter(this::shouldUploadFile)
                        .collect(Collectors.toList());

                log.info("找到 {} 个文件需要上传", files.size());

                for (Path file : files) {
                    // 计算相对路径
                    String relativePath = rootPath.relativize(file).toString();
                    relativePath = relativePath.replace('\\', '/');

                    // 读取文件内容
                    String content = Files.readString(file, StandardCharsets.UTF_8);

                    // 创建CommitAction
                    CommitAction action = new CommitAction();
                    action.setAction(CommitAction.Action.CREATE);
                    action.setFilePath(relativePath);
                    action.setContent(content);
                    commitActions.add(action);

                    log.debug("准备上传文件: {}", relativePath);
                }
            }

            if (!commitActions.isEmpty()) {
                // 批量提交文件
                String commitMessage = "Initial commit - AI generated code";
                batchCommitFile(projectId, branchName, commitMessage, commitActions);
                log.info("成功上传 {} 个文件到GitLab", commitActions.size());
            }

        } catch (IOException e) {
            log.error("上传文件到GitLab失败", e);
            throw new BusinessException("上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 判断文件是否应该上传
     */
    private boolean shouldUploadFile(Path file) {
        String fileName = file.getFileName().toString();
        String filePath = file.toString();

        // 排除不需要上传的文件
        if (fileName.startsWith(".") && !fileName.equals(".gitignore")) {
            return false;
        }

        // 排除target目录
        if (filePath.contains("/target/") || filePath.contains("\\target\\")) {
            return false;
        }

        // 排除编译输出文件
        if (fileName.endsWith(".class")) {
            return false;
        }

        // 排除IDE文件
        if (fileName.endsWith(".iml") || filePath.contains(".idea")) {
            return false;
        }

        return true;
    }

    // ================== GitService 接口实现（使用JGit） ==================

    /**
     * 使用 JGit 克隆 Git 仓库
     * 支持公开仓库；私有仓库优先使用传入的 username/password，否则使用配置的 GitLab token
     *
     * @param gitUrl    Git 仓库 URL
     * @param branch    分支名称
     * @param targetDir 目标目录
     * @param username  用户名（可选）
     * @param password  密码或 Token（可选）
     * @return 是否成功
     */
    @Override
    public boolean cloneRepository(String gitUrl, String branch, Path targetDir, String username, String password) {
        try {
            log.info("开始使用JGit克隆仓库: {} 到 {}", gitUrl, targetDir);

            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(gitUrl)
                    .setDirectory(targetDir.toFile())
                    .setCloneAllBranches(false);

            if (branch != null && !branch.isEmpty()) {
                cloneCommand.setBranch(branch);
            }

            // 优先使用请求传入的用户名密码（支持带用户名密码的 clone）
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                log.info("使用请求传入的凭证进行认证");
                cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
            } else if (gitUrl != null
                    && gitUrl.contains(gitlabHost.replace("https://", "").replace("http://", "").replace("/", ""))
                    && StringUtils.isNotBlank(gitLabAccessToken)) {
                log.info("检测到GitLab仓库，使用配置的 access token 进行认证");
                cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", gitLabAccessToken));
            }

            cloneCommand.setProgressMonitor(new SimpleProgressMonitor());

            try (Git git = cloneCommand.call()) {
                log.info("仓库克隆成功: {}", gitUrl);
                return true;
            }

        } catch (GitAPIException e) {
            log.error("克隆仓库失败: {}", gitUrl, e);
            return false;
        }
    }

    /**
     * 使用JGit添加文件到暂存区
     */
    @Override
    public boolean add(Path repoDir, String filePattern) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return false;

            AddCommand addCommand = git.add();
            addCommand.addFilepattern(filePattern != null ? filePattern : ".");
            addCommand.call();

            log.info("文件已添加到暂存区: {}", filePattern);
            return true;

        } catch (GitAPIException e) {
            log.error("添加文件失败", e);
            return false;
        }
    }

    /**
     * 使用JGit提交更改
     */
    @Override
    public String commit(Path repoDir, String message, String author, String email) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return null;

            CommitCommand commitCommand = git.commit()
                    .setMessage(message);

            // 设置提交者信息
            if (author != null && email != null) {
                PersonIdent personIdent = new PersonIdent(author, email);
                commitCommand.setAuthor(personIdent);
                commitCommand.setCommitter(personIdent);
            }

            RevCommit commit = commitCommand.call();
            String commitId = commit.getId().getName();

            log.info("提交成功: {}", commitId);
            return commitId;

        } catch (GitAPIException e) {
            log.error("提交失败", e);
            return null;
        }
    }

    /**
     * 使用JGit推送到远程仓库
     */
    @Override
    public boolean push(Path repoDir, String remote, String branch, String username, String password) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return false;

            PushCommand pushCommand = git.push();

            // 设置远程和分支
            if (remote != null) {
                pushCommand.setRemote(remote);
            }
            if (StringUtils.isNotBlank(branch)) {
                pushCommand.setRefSpecs(new org.eclipse.jgit.transport.RefSpec(branch + ":" + branch));
            }

            // 设置认证
            if (username != null && password != null) {
                CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
                pushCommand.setCredentialsProvider(credentialsProvider);
            } else if (StringUtils.isNotBlank(gitLabAccessToken)) {
                // 如果没有提供用户名密码，尝试使用配置的access token
                CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("oauth2",
                        gitLabAccessToken);
                pushCommand.setCredentialsProvider(credentialsProvider);
            }

            Iterable<PushResult> results = pushCommand.call();
            boolean success = true;
            for (PushResult result : results) {
                for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                            && update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        log.error("推送失败: {} -> status: {}, message: {}", update.getRemoteName(), update.getStatus(),
                                update.getMessage());
                        success = false;
                    }
                }
            }

            if (success) {
                log.info("推送成功{}", StringUtils.isNotBlank(branch) ? " (branch: " + branch + ")" : "");
            }
            return success;

        } catch (GitAPIException e) {
            log.error("推送失败", e);
            return false;
        }
    }

    /**
     * 使用JGit拉取最新代码
     */
    @Override
    public boolean pull(Path repoDir, String remote, String branch) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return false;

            PullCommand pullCommand = git.pull();

            if (remote != null) {
                pullCommand.setRemote(remote);
            }

            if (branch != null) {
                pullCommand.setRemoteBranchName(branch);
            }

            if (StringUtils.isNotBlank(gitLabAccessToken)) {
                // 使用配置的access token
                CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("oauth2",
                        gitLabAccessToken);
                pullCommand.setCredentialsProvider(credentialsProvider);
            }

            // 特殊处理：拉取时自动尝试合并/变基
            pullCommand.setRebase(true); // 使用 rebase 模式同步远程代码

            PullResult result = pullCommand.call();

            log.info("拉取结果: {} (successful: {})", result.getRebaseResult().getStatus(), result.isSuccessful());
            return result.isSuccessful();

        } catch (GitAPIException e) {
            log.error("拉取失败", e);
            return false;
        }
    }

    /**
     * 创建分支
     * 优先使用GitLab API，如果是本地操作则使用JGit
     */
    @Override
    public boolean createBranch(Path repoDir, String branchName, String baseBranch) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return false;

            CreateBranchCommand createBranchCommand = git.branchCreate()
                    .setName(branchName);

            if (baseBranch != null) {
                createBranchCommand.setStartPoint(baseBranch);
            }

            Ref ref = createBranchCommand.call();

            log.info("分支创建成功: {}", branchName);
            return ref != null;

        } catch (GitAPIException e) {
            log.error("创建分支失败: {}", branchName, e);
            return false;
        }
    }

    /**
     * 使用JGit切换分支
     */
    @Override
    public boolean checkout(Path repoDir, String branchName) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return false;

            git.checkout()
                    .setName(branchName)
                    .call();

            log.info("切换到分支: {}", branchName);
            return true;

        } catch (GitAPIException e) {
            log.error("切换分支失败: {}", branchName, e);
            return false;
        }
    }

    /**
     * 使用JGit获取当前分支名
     */
    @Override
    public String getCurrentBranch(Path repoDir) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return null;

            Repository repository = git.getRepository();
            String branch = repository.getBranch();

            log.info("当前分支: {}", branch);
            return branch;

        } catch (IOException e) {
            log.error("获取当前分支失败", e);
            return null;
        }
    }

    /**
     * 使用JGit获取仓库状态
     */
    @Override
    public GitStatus getStatus(Path repoDir) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return null;

            Status status = git.status().call();

            GitStatus gitStatus = new GitStatus();
            gitStatus.setAdded(new ArrayList<>(status.getAdded()));
            gitStatus.setModified(new ArrayList<>(status.getModified()));
            gitStatus.setDeleted(new ArrayList<>(status.getRemoved()));
            gitStatus.setUntracked(new ArrayList<>(status.getUntracked()));
            gitStatus.setHasChanges(!status.isClean());

            return gitStatus;

        } catch (GitAPIException e) {
            log.error("获取仓库状态失败", e);
            return null;
        }
    }

    /**
     * 使用JGit获取提交历史
     */
    @Override
    public List<CommitInfo> getCommitHistory(Path repoDir, String branch, int maxCount) {
        List<CommitInfo> commitInfos = new ArrayList<>();

        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return commitInfos;

            LogCommand logCommand = git.log();

            if (branch != null) {
                Repository repository = git.getRepository();
                logCommand.add(repository.resolve(branch));
            }

            if (maxCount > 0) {
                logCommand.setMaxCount(maxCount);
            }

            Iterable<RevCommit> commits = logCommand.call();

            for (RevCommit commit : commits) {
                CommitInfo info = new CommitInfo();
                info.setCommitId(commit.getId().getName());
                info.setAuthor(commit.getAuthorIdent().getName());
                info.setEmail(commit.getAuthorIdent().getEmailAddress());
                info.setMessage(commit.getFullMessage());
                info.setTimestamp(commit.getCommitTime() * 1000L);
                commitInfos.add(info);
            }

        } catch (Exception e) {
            log.error("获取提交历史失败", e);
        }
        return commitInfos;
    }

    /**
     * 使用JGit重置到指定提交
     */
    @Override
    public boolean reset(Path repoDir, String commitId, String type, boolean pushToRemote) {
        try (Git git = openRepository(repoDir)) {
            if (git == null)
                return false;

            ResetCommand resetCommand = git.reset();
            resetCommand.setRef(commitId);

            if (StringUtils.isNotBlank(type)) {
                try {
                    resetCommand.setMode(ResetCommand.ResetType.valueOf(type.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("未知的重置类型: {}, 使用默认 MIXED", type);
                    resetCommand.setMode(ResetCommand.ResetType.MIXED);
                }
            }

            resetCommand.call();
            log.info("重置成功: {} ({})", commitId, type);

            if (pushToRemote) {
                log.info("开始强制推送到远程...");
                PushCommand pushCommand = git.push();
                pushCommand.setForce(true);

                if (StringUtils.isNotBlank(gitLabAccessToken)) {
                    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("oauth2",
                            gitLabAccessToken);
                    pushCommand.setCredentialsProvider(credentialsProvider);
                }
                Iterable<PushResult> results = pushCommand.call();
                for (PushResult result : results) {
                    for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                        if (update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                                && update.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                            log.error("强制推送失败: {} -> status: {}, message: {}", update.getRemoteName(),
                                    update.getStatus(), update.getMessage());
                            return false;
                        }
                    }
                }
                log.info("强制推送成功");
            }

            return true;

        } catch (Exception e) {
            log.error("重置失败", e);
            return false;
        }
    }

    /**
     * 本地仓库场景：根据commitId获取与上次提交（parent commit）的差异文件列表
     * @param repoDir 本地Git仓库路径（如 D:/projects/my-git-repo）
     * @param commitId 目标commit的SHA-1值（如 a1b2c3d4e5f6...）
     * @return 差异文件详情列表（含修改位置）
     */
    @Override
    public List<GitDiffResponse.GitCommitDiffDetail> getLocalCommitDiff(Path repoDir, String commitId, String parentCommitId) {
        Integer maxLineCount = 5;//默认展示上下文的行数
        //展示修改文件的代码
        List<GitDiffResponse.GitCommitDiffDetail.ChangeType> filterChangeTypes = Arrays.asList(GitDiffResponse.GitCommitDiffDetail.ChangeType.MODIFY);
        return getLocalCommitDiff(repoDir, commitId, parentCommitId, maxLineCount, filterChangeTypes);
    }


    /**
     * 获取本地commit差异（对比目标commit与父commit）
     * @param repoDir Git仓库目录
     * @param commitId 目标commitId
     * @param maxLineCount 上下文最大行数
     * @param filterChangeTypes 过滤变更类型
     * @return
     */
    private List<GitDiffResponse.GitCommitDiffDetail> getLocalCommitDiff(Path repoDir, String commitId,
                                                                         String parentCommitId, Integer maxLineCount,
                                                                         List<GitDiffResponse.GitCommitDiffDetail.ChangeType> filterChangeTypes) {
        List<GitDiffResponse.GitCommitDiffDetail> diffDetails = new ArrayList<>(0);
        log.info("执行Git操作，项目目录: {}", repoDir);
        // 1. 打开本地Git仓库（复用现有工具方法）
        try (Git git = openRepository(repoDir)) {
            if (git == null) {
                log.error("获取本地commit差异失败：无效的Git仓库路径 {}", repoDir);
                return diffDetails;
            }
            Repository repo = git.getRepository();

            // 2. 解析目标commit
            ObjectId targetCommitObj = repo.resolve(commitId);

            // 校验 commitId 合法性
            if (StringUtils.isBlank(commitId)) {
                log.error("获取本地commit差异失败：commitId 不能为空");
                return diffDetails;
            }
            if (targetCommitObj == null) {
                log.error("获取本地commit差异失败：commitId {} 不存在", commitId);
                return diffDetails;
            }
            RevCommit targetCommit;
            RevCommit parentCommit = null;

            // 2.1 获取父commit（若为首次提交，父commit为null）
            try (RevWalk revWalk = new RevWalk(repo)) {
                targetCommit = revWalk.parseCommit(targetCommitObj);
                if (!parentCommitId.isBlank()) { //如果targetId不为空 本次提交的id
                    ObjectId parentCommitObject = repo.resolve(parentCommitId);
                    parentCommit = revWalk.parseCommit(parentCommitObject);
                } else { //取上一次提交的 id
                    if (targetCommit.getParentCount() > 0) {
                        parentCommit = revWalk.parseCommit(targetCommit.getParent(0).getId()); // 取第一个父commit（常规提交仅1个父）
                    }else {
                        // 初始commit（无父节点）：对比“空树”与“当前commit”（获取所有初始提交的文件）
                        parentCommit = revWalk.parseCommit(ObjectId.zeroId()); // Git空树的固定ID
                    }
                }
            }
            // 3. 对比目标commit与父commit的差异（JGit DiffCommand）
            DiffCommand diffCommand = git.diff();
            if (parentCommit != null) {
                diffCommand.setOldTree(prepareTreeParser(repo, parentCommit.getId().getName())); // 上次提交的树
            }
            diffCommand.setNewTree(prepareTreeParser(repo, targetCommit.getId().getName())); // 目标提交的树

            // 4. 解析差异结果（含文件状态和行号）
            List<DiffEntry> diffEntries = diffCommand.call();
            // 5处理DiffEntry 整理detail
            diffDetails = processDiffEntry(diffEntries, repo, commitId, parentCommit, maxLineCount, filterChangeTypes);

            log.info("本地仓库 {} 中，commit {} 与上次提交的差异文件共 {} 个",
                    repoDir, commitId, diffDetails.size());
            return diffDetails;

        } catch (Exception e) {
            // 增加最外层catch扩大异常捕获范围 防止影响整体流程
            log.error("获取本地commit差异失败：repoDir={}, commitId={}", repoDir, commitId, e);
            throw new BusinessException("获取本地commit差异失败", e);
        }
    }
    /**
     * 辅助方法1：构建JGit树解析器（用于DiffCommand对比）
     */
    private CanonicalTreeParser prepareTreeParser(Repository repo, String commitId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repo)) {
            RevCommit commit = revWalk.parseCommit(repo.resolve(commitId));
            ObjectId treeId = commit.getTree().getId();
            try (ObjectReader reader = repo.newObjectReader()) {
                CanonicalTreeParser treeParser = new CanonicalTreeParser();
                treeParser.reset(reader, treeId);
                return treeParser;
            } finally {
                revWalk.dispose();
            }
        }
    }

    /**
     * 填充diffdetail属性，处理差异代码
     */
    private List<GitDiffResponse.GitCommitDiffDetail> processDiffEntry(List<DiffEntry> diffEntries, Repository repo,
                                                                       String commitId,
                                                                       RevCommit parentCommit,
                                                                       Integer maxLineCount,
                                                                       List<GitDiffResponse.GitCommitDiffDetail.ChangeType> filterChangeTypes) {
        List<GitDiffResponse.GitCommitDiffDetail> diffDetails = new ArrayList<>(0);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DiffFormatter diffFormatter = new DiffFormatter(outputStream)) {
            diffFormatter.setRepository(repo);
            //diffFormatter.setCharset(StandardCharsets.UTF_8);
            diffFormatter.setContext(maxLineCount); // 不显示上下文行，仅显示变更行
            diffFormatter.setDetectRenames(true); // 检测文件重命名（可选）

            for (DiffEntry diffEntry : diffEntries) {
                GitDiffResponse.GitCommitDiffDetail detail = new GitDiffResponse.GitCommitDiffDetail();
                detail.setTargetCommitId(commitId);
                detail.setParentCommitId(parentCommit != null ? parentCommit.getId().getName() : null);

                // 4.1 处理文件路径和变更类型
                switch (diffEntry.getChangeType()) {
                    case ADD:
                        detail.setFilePath(diffEntry.getNewPath());// 新增文件仅新路径有效
                        detail.setChangeType(GitDiffResponse.GitCommitDiffDetail.ChangeType.ADD);
                        break;
                    case DELETE:
                        detail.setOldFilePath(diffEntry.getOldPath()); // 删除文件仅旧路径有效
                        detail.setChangeType(GitDiffResponse.GitCommitDiffDetail.ChangeType.DELETE);
                        break;
                    case RENAME:
                        detail.setFilePath(diffEntry.getNewPath());
                        detail.setOldFilePath(diffEntry.getOldPath());
                        detail.setChangeType(GitDiffResponse.GitCommitDiffDetail.ChangeType.RENAME);
                        break;
                    default: //copy &  modify
                        detail.setFilePath(diffEntry.getNewPath());
                        detail.setChangeType(GitDiffResponse.GitCommitDiffDetail.ChangeType.MODIFY);
                }
                // 4.2 处理修改位置
                if (filterChangeTypes.contains(detail.getChangeType())) {
                    detail.setModifiedLineDetails(parseModifiedLines(diffFormatter, diffEntry, outputStream));
                }
                diffDetails.add(detail);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return diffDetails;
    }



    /**
     * 新增：解析单行修改详情（含行号+内容）
     * 替代原getModifiedLineRanges，支持精确到单行的修改记录
     */
    private List<GitDiffResponse.ModifiedLineDetail> parseModifiedLines(DiffFormatter diffFormatter, DiffEntry diffEntry, ByteArrayOutputStream outputStream) throws IOException {
        List<GitDiffResponse.ModifiedLineDetail> lineDetails = new ArrayList<>();
        //ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. 获取完整diff内容（包含Hunk头和每行变更）
        diffFormatter.format(diffEntry);
        String diffContent = outputStream.toString(StandardCharsets.UTF_8);
        String[] diffLines = diffContent.split("\n");

        // 2. 解析每个Hunk的行号基准（Hunk头格式：@@ -oldStart,oldCount +newStart,newCount @@）
        int currentOldLine = 0; // 当前Hunk的旧行号计数器
        int currentNewLine = 0; // 当前Hunk的新行号计数器
        boolean inHunk = false; // 是否处于Hunk内容中

        for (String line : diffLines) {
            if (line.startsWith("@@")) {
                // 2.1 解析Hunk头，初始化当前Hunk的行号基准
                String[] hunkParts = line.split(" ");
                String oldPart = hunkParts[1].substring(1); // 去除前缀"-"，如 "5,4"
                String newPart = hunkParts[2].substring(1); // 去除前缀"+"，如 "5,4"

                // 解析旧行起始号（如 "5,4" → 起始号5）
                currentOldLine = Integer.parseInt(oldPart.split(",")[0]);
                // 解析新行起始号（如 "5,4" → 起始号5）
                currentNewLine = Integer.parseInt(newPart.split(",")[0]);
                inHunk = true;
                continue;
            }

            if (!inHunk) {
                // 跳过非Hunk内容（如文件头、空行）
                continue;
            }

            // 3. 逐行解析：区分删除/新增/上下文行，生成单行详情
            GitDiffResponse.ModifiedLineDetail lineDetail = new GitDiffResponse.ModifiedLineDetail();
            if (line.startsWith("-")) {
                // 3.1 删除行：旧行号有效，新行号为null，内容去除"-"
                lineDetail.setLineChangeType(GitDiffResponse.ModifiedLineDetail.LineChangeType.DELETE);
                lineDetail.setOldLine(currentOldLine++);
                lineDetail.setNewLine(null);
                lineDetail.setContent(line.substring(1)); // 去除前缀"-"
            } else if (line.startsWith("+")) {
                // 3.2 新增行：新行号有效，旧行号为null，内容去除"+"
                lineDetail.setLineChangeType(GitDiffResponse.ModifiedLineDetail.LineChangeType.ADD);
                lineDetail.setOldLine(null);
                lineDetail.setNewLine(currentNewLine++);
                lineDetail.setContent(line.substring(1)); // 去除前缀"+"
            } else if (line.startsWith(" ")) {
                // 3.3 上下文行：旧/新行号均有效，内容保留空格前缀（维持格式）
                lineDetail.setLineChangeType(GitDiffResponse.ModifiedLineDetail.LineChangeType.CONTEXT);
                lineDetail.setOldLine(currentOldLine++);
                lineDetail.setNewLine(currentNewLine++);
                lineDetail.setContent(line); // 保留空格前缀，便于定位上下文
            } else {
                // 跳过特殊行（如Hunk结束标记、空行）
                continue;
            }

            lineDetails.add(lineDetail);
        }
        outputStream.reset();
        return lineDetails;
    }
    /**
     * 打开Git仓库
     */
    private Git openRepository(Path repoDir) {
        try {
            File gitDir = repoDir.resolve(".git").toFile();
            if (!gitDir.exists()) {
                log.error("不是有效的Git仓库: {}", repoDir);
                return null;
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build();

            return new Git(repository);

        } catch (IOException e) {
            log.error("打开仓库失败: {}", repoDir, e);
            return null;
        }
    }

    /**
     * 简单的进度监控器
     */
    private static class SimpleProgressMonitor implements ProgressMonitor {
        private String currentTask = "";

        @Override
        public void start(int totalTasks) {
            log.info("开始执行，共 {} 个任务", totalTasks);
        }

        @Override
        public void beginTask(String title, int totalWork) {
            currentTask = title;
            log.info("开始任务: {} (共 {} 个工作单元)", title, totalWork);
        }

        @Override
        public void update(int completed) {
            // 可以在这里实现进度更新逻辑
        }

        @Override
        public void endTask() {
            log.info("任务完成: {}", currentTask);
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void showDuration(boolean enabled) {
            // 可选实现
        }
    }

    public static void main(String[] args) throws GitLabApiException {
        String gitlabHost = "https://hgit.goodluck.net";
        String gitLabAccessToken = "glpat-rinNQA7PRji_xFbS";
        Long defaultGroupId = 14152L;
        GitLabApi gitLabApi = new GitLabApi(gitlabHost, gitLabAccessToken);
        Long projectId = 4422L;
        // 创建项目
        Project paramProject = new Project();
        paramProject.setName("ai-coding-123123123");
        paramProject.setDefaultBranch("master");
        paramProject.setInitializeWithReadme(true);
        Project project = gitLabApi.getProjectApi().createProject(defaultGroupId, paramProject);

        // System.out.println(JSON.toJSONString(project));
        // gitLabApi.getTagsApi().deleteTag();
        // gitLabApi.getTagsApi().deleteTag(projectId, "v1.0.15");

        /*
         * 授权用户
         * List<User> users = gitLabApi.getUserApi().findUsers("01490486");
         * System.out.println(JSON.toJSONString(users));
         * if (CollectionUtils.isNotEmpty(users)) {
         * gitLabApi.getProjectApi().addMember(4276L, users.get(0).getId(),
         * AccessLevel.DEVELOPER);
         * }
         */

        // 创建文件
        /*
         * RepositoryFile repositoryFile = new RepositoryFile();
         * repositoryFile.setFilePath("src/web");
         * repositoryFile.setContent("{aaa:bbb}");
         * gitLabApi.getRepositoryFileApi().createFile(projectId, repositoryFile,
         * "master", "init code");
         */

        /*
         * RepositoryFile repositoryFile = gitLabApi.getRepositoryFileApi()
         * .getOptionalFileInfo(projectId, "src/web2", "master").orElse(null);
         * System.out.println(JSON.toJSONString(repositoryFile));
         */
        // InputStream in = gitLabApi.getRepositoryFileApi().getRawFile(projectId,
        // "master", "src/web");
        // 更新文件
        /*
         * RepositoryFile repositoryFile2 = new RepositoryFile();
         * repositoryFile2.setFilePath("src/web");
         * repositoryFile2.setContent("{\"abc\": \"bbbd\"}");
         * gitLabApi.getRepositoryFileApi().updateFile(projectId, repositoryFile2,
         * "默认开发分支2", "update code");
         */

        // 创建分支
        /*
         * Branch branch = gitLabApi.getRepositoryApi().createBranch(projectId,
         * "默认开发分支2", "master");
         * System.out.println(JSON.toJSONString(branch));
         */

        /*
         * MergeRequest mergeRequest = gitLabApi.getMergeRequestApi()
         * .createMergeRequest(projectId, "默认开发分支2", "master", "合并", "合并", null);
         * System.out.println(JSON.toJSONString(mergeRequest));
         */
        /*
         * MergeRequest mergeRequest =
         * gitLabApi.getMergeRequestApi().getMergeRequest(projectId, 11L);
         * System.out.println("==>" + JSON.toJSONString(mergeRequest));
         */
        /*
         * MergeRequest mergeRequest1 = gitLabApi.getMergeRequestApi()
         * .acceptMergeRequest(projectId, 4L);
         * System.out.println(JSON.toJSONString(mergeRequest1));
         */

        /*
         * MergeRequest mergeRequest1 = gitLabApi.getMergeRequestApi()
         * .getMergeRequestChanges(projectId, 11L);
         * System.out.println("==>" + JSON.toJSONString(mergeRequest1));
         */

    }
}
