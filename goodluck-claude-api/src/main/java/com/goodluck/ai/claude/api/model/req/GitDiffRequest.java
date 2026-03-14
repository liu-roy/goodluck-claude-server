package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "代码差异比对入参")
@Builder
public class GitDiffRequest {
    /**
     * 源提交ID（如：a1b2c3d4e5f67890abcdef1234567890abcdef12）
     *
     */
    @Schema(description = "当前分支ID,本次需要比对的CommitId")
    private String sourceCommitId;

    /**
     * 目标提交ID（如：f1e2d3c4b5a67890fedcba0987654321abcdef12）
     *
     */
    @Schema(description = "目标分支ID,上一次的CommitId")
    private String targetCommitId;

    @Schema(description = "项目名称", example = "my-java-project")
    private String projectName;

    /**
     * 不应该修改的文件列表（JSON中长字段名映射）
     * 包含：不能修改但已修改的文件、预期修改与实际修改不同的文件
     */
    @Schema(description = "需求列表")
    private List<Requirement> requirements;


    @Schema(description = "预期修改的文件")
    private List<ExpectedChangedFile> expectedChangedFiles;


    /**
     * 需求
     */
    @Data
    @Builder
    public static class Requirement {
        //需求ID
        private String id;
        //需求描述
        private String description;
        //预期修改的文件列表
        //private List<ExpectedChangedFile> expectedChangedFiles;
    }

    /**
     * 预期修改的文件
     */
    @Data
    @Builder
    public static class ExpectedChangedFile {
        //文件路径
        String filePath;
        //需求id
        String requirementId;
    }
}
