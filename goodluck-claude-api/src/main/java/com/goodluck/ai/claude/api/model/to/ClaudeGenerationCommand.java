package com.goodluck.ai.claude.api.model.to;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Claude代码生成请求模型
 * 用于承接Claude AI代码生成的输入参数
 *
 * @author Claude AI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Claude代码生成请求")
public class ClaudeGenerationCommand {

    @Schema(description = "提示词/需求描述", example = "创建一个用户管理系统，包含增删改查功能", required = true)
    @NotBlank(message = "提示词不能为空")
    private String prompt;

    @Schema(description = "项目名称", example = "user-management-system", required = true)
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "会话ID（用于多轮对话上下文）", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;

    @Schema(description = "系统提示词（用于定制Claude行为）", example = "You are a Java expert focusing on Spring Boot applications")
    private String systemPrompt;

    @Schema(description = "是否为新会话", example = "true")
    @Builder.Default
    private Boolean newSession = true;

    @Schema(description = "超时时间(秒)", example = "600")
    @Builder.Default
    private Integer timeout = 600;

    @Schema(description = "是否跳过权限检查", example = "false")
    @Builder.Default
    private Boolean skipPermissions = false;
}
