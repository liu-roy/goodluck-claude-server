package com.goodluck.ai.claude.service.service;




import com.goodluck.ai.claude.api.model.resp.GitDiffResponse;

import java.nio.file.Path;
import java.util.List;

/**
 * Git操作公共接口
 * 定义Git相关的通用操作，可由GitlabService或JGitService实现
 */
public interface GitService {

    /**
     * 克隆 Git 仓库
     *
     * @param gitUrl    Git 仓库 URL
     * @param branch    分支名称（可选，为 null 时使用默认分支）
     * @param targetDir 目标目录
     * @param username  用户名（可选，私有仓库时与 password 二选一或同时传）
     * @param password  密码或 Token（可选）
     * @return 克隆是否成功
     */
    boolean cloneRepository(String gitUrl, String branch, Path targetDir, String username, String password);

    /**
     * 添加文件到暂存区
     * 
     * @param repoDir     仓库目录
     * @param filePattern 文件模式（如 ".", "*.java" 等）
     * @return 是否成功
     */
    boolean add(Path repoDir, String filePattern);

    /**
     * 提交更改
     * 
     * @param repoDir 仓库目录
     * @param message 提交消息
     * @param author  作者信息（可选）
     * @param email   邮箱（可选）
     * @return 提交的commit ID，失败返回null
     */
    String commit(Path repoDir, String message, String author, String email);

    /**
     * 推送到远程仓库
     * 
     * @param repoDir  仓库目录
     * @param remote   远程仓库名称（默认origin）
     * @param branch   分支名称
     * @param username 用户名（可选）
     * @param password 密码或token（可选）
     * @return 是否成功
     */
    boolean push(Path repoDir, String remote, String branch, String username, String password);

    /**
     * 拉取最新代码
     * 
     * @param repoDir 仓库目录
     * @param remote  远程仓库名称（默认origin）
     * @param branch  分支名称
     * @return 是否成功
     */
    boolean pull(Path repoDir, String remote, String branch);

    /**
     * 创建分支
     * 
     * @param repoDir    仓库目录
     * @param branchName 新分支名称
     * @param baseBranch 基础分支（可选，为null时基于当前分支）
     * @return 是否成功
     */
    boolean createBranch(Path repoDir, String branchName, String baseBranch);

    /**
     * 切换分支
     * 
     * @param repoDir    仓库目录
     * @param branchName 分支名称
     * @return 是否成功
     */
    boolean checkout(Path repoDir, String branchName);

    /**
     * 获取当前分支名
     * 
     * @param repoDir 仓库目录
     * @return 当前分支名，失败返回null
     */
    String getCurrentBranch(Path repoDir);

    /**
     * 获取仓库状态
     * 
     * @param repoDir 仓库目录
     * @return 状态信息，包含修改、新增、删除的文件列表
     */
    GitStatus getStatus(Path repoDir);

    /**
     * 获取提交历史
     * 
     * @param repoDir  仓库目录
     * @param branch   分支名称（可选）
     * @param maxCount 最大返回数量
     * @return 提交历史列表
     */
    List<CommitInfo> getCommitHistory(Path repoDir, String branch, int maxCount);

    /**
     * 重置到指定提交
     * 
     * @param repoDir      仓库目录
     * @param commitId     提交ID
     * @param type         重置类型 (HARD, MIXED, SOFT)
     * @param pushToRemote 是否推送到远程 (Force Push)
     * @return 是否成功
     */
    boolean reset(Path repoDir, String commitId, String type, boolean pushToRemote);

    /**
     * 获取本地仓库的提交差异详情
     *
     * @param repoDir  仓库目录
     * @param commitId 提交ID
     * @return 差异详情列表
     */
    public List<GitDiffResponse.GitCommitDiffDetail> getLocalCommitDiff(Path repoDir, String commitId, String parentCommitId);

    /**
     * Git状态信息
     */
    class GitStatus {
        private List<String> added;
        private List<String> modified;
        private List<String> deleted;
        private List<String> untracked;
        private boolean hasChanges;

        public GitStatus() {
        }

        public List<String> getAdded() {
            return added;
        }

        public void setAdded(List<String> added) {
            this.added = added;
        }

        public List<String> getModified() {
            return modified;
        }

        public void setModified(List<String> modified) {
            this.modified = modified;
        }

        public List<String> getDeleted() {
            return deleted;
        }

        public void setDeleted(List<String> deleted) {
            this.deleted = deleted;
        }

        public List<String> getUntracked() {
            return untracked;
        }

        public void setUntracked(List<String> untracked) {
            this.untracked = untracked;
        }

        public boolean isHasChanges() {
            return hasChanges;
        }

        public void setHasChanges(boolean hasChanges) {
            this.hasChanges = hasChanges;
        }
    }

    /**
     * 提交信息
     */
    class CommitInfo {
        private String commitId;
        private String author;
        private String email;
        private String message;
        private long timestamp;

        public CommitInfo() {
        }

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }


}