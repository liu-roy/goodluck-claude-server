package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 代码生成请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成请求")
public class CodeGenerationRequest {

    @Schema(description = "代码生成提示", example = "创建一个Java的Hello World程序", required = true)
    @NotBlank(message = "代码生成提示不能为空")
    private String prompt;

    @Schema(description = "系统提示词", example = "有则作为系统提示词")
    private String systemPrompt;

    @Schema(description = "项目名称", example = "my-java-project")
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "会话ID（可选，用于多轮对话）", example = "uuid")
    private String sessionId;

    @Schema(description = "gitCommitMessage", example = "git自动提交的信息")
    private String gitCommitMessage;

}
