package com.goodluck.ai.claude.api.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Schema(description = "代码比对结果信息")
public class GitDiffResponse {

    @Schema(description = "代码比对结果true|false（在允许修改的列表之外返回false）")
    private Boolean diffCodeResult;

    @Schema(description = "提示信息")
    private String message;
    /**
     * 源提交ID（如：a1b2c3d4e5f67890abcdef1234567890abcdef12）
     *
     */
    @Schema(description = "当前分支ID")
    private String sourceCommitId;

    /**
     * 目标提交ID（如：f1e2d3c4b5a67890fedcba0987654321abcdef12）
     *
     */
    @Schema(description = "目标分支ID")
    private String targetCommitId;

    @Schema(description = "项目名称", example = "my-java-project")
    private String projectName;
    /**
     * 不应该修改的文件列表（JSON中长字段名映射）
     * 包含：不能修改但已修改的文件、预期修改与实际修改不同的文件
     */
    @Schema(description = "错误修改的文件列表")
    private List<ErrorModifiedFile> errorModifiedFileList;


    @Schema(description = "比对差异结果列表")
    private List<GitCommitDiffDetail> diffList;
    /**
     * Git提交差异详情类：增强行号与内容记录
     */
    @Data
    public static class GitCommitDiffDetail {
        /** 文件路径（本地仓库为绝对路径，GitLab为项目内相对路径） */
        private String filePath;
        /** 旧文件路径（重命名后的文件会有值） */
        private String oldFilePath;
        /** 变更类型：ADD（新增）、DELETE（删除）、MODIFY（修改） */
        private ChangeType changeType;
        /** 父commitId（用于追溯对比源） */
        private String parentCommitId;
        /** 目标commitId（当前对比的commit） */
        private String targetCommitId;
        // ========== 新增：单行级修改详情（替代原lineRanges，更精确） ==========
        /** 单行修改列表：记录每一行的旧行号、新行号、修改类型、具体内容 */
        private List<ModifiedLineDetail> modifiedLineDetails = new ArrayList<>();


        /** 变更类型枚举 */
        public enum ChangeType {
            ADD, DELETE, MODIFY, RENAME
        }
    }
    /**
     * 单行修改详情内部类
     * 记录单行的精确修改信息，支持区分新增/删除/上下文行
     */
    @Data
    public static class ModifiedLineDetail {
        /** 旧行号：删除/修改时有效（新增行旧行号为null） */
        private Integer oldLine;
        /** 新行号：新增/修改时有效（删除行新行号为null） */
        private Integer newLine;
        /** 行修改类型：ADD（新增行）、DELETE（删除行）、CONTEXT修改位置的前后代码（上下文行，用于定位） */
        private LineChangeType lineChangeType;
        /** 该行的具体内容（代码/文本） */
        private String content;

        /** 行修改类型枚举 */
        public enum LineChangeType {
            ADD, DELETE, CONTEXT
        }
    }
    /**
     * 错误修改文件内部类
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorModifiedFile {
        /** 文件路径 */
        private String filePath;
        /** 操作类型 */
        private GitCommitDiffDetail.ChangeType changeType;
    }
}
