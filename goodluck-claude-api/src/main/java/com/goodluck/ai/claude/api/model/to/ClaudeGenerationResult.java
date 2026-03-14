package com.goodluck.ai.claude.api.model.to;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Claude代码生成结果模型
 * 用于承接Claude AI代码生成的输出结果
 *
 * @author Claude AI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Claude代码生成结果")
public class ClaudeGenerationResult {

    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;

    @Schema(description = "项目名称", example = "user-management-system")
    private String projectName;

    @Schema(description = "项目目录路径", example = "/workspace/user-management-system")
    private String projectPath;

    @Schema(description = "生成状态", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED", "PARTIAL_SUCCESS", "IN_PROGRESS"})
    private String status;

    @Schema(description = "状态码", example = "200")
    private Integer statusCode;

    @Schema(description = "状态消息", example = "代码生成成功")
    private String message;

    @Schema(description = "错误信息（如果失败）", example = "Syntax error in generated code")
    private String errorMessage;

    @Schema(description = "错误详情", example = "NullPointerException at line 42")
    private String errorDetail;

    @Schema(description = "开始时间")
    private Date startTime;

    @Schema(description = "结束时间")
    private Date endTime;

    @Schema(description = "执行耗时（毫秒）", example = "5230")
    private Long durationMs;

    @Schema(description = "执行步骤记录")
    @Builder.Default
    private List<ExecutionStep> executionSteps = new ArrayList<>();

    @Schema(description = "附加元数据")
    private Map<String, Object> metadata;



    /**
     * 执行步骤信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "执行步骤信息")
    public static class ExecutionStep {
        @Schema(description = "步骤序号", example = "1")
        private Integer stepNumber;

        @Schema(description = "步骤名称", example = "生成实体类")
        private String stepName;

        @Schema(description = "步骤描述", example = "Creating User entity class with JPA annotations")
        private String description;

        @Schema(description = "步骤状态", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED", "SKIPPED", "IN_PROGRESS"})
        private String status;

        @Schema(description = "开始时间")
        private Date startTime;

        @Schema(description = "结束时间")
        private Date endTime;

        @Schema(description = "耗时（毫秒）", example = "1250")
        private Long durationMs;

        @Schema(description = "生成的文件列表")
        @Builder.Default
        private List<String> filesGenerated = new ArrayList<>();

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "输出内容")
        private String output;
    }
}
