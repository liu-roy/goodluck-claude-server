package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量代码生成请求DTO
 * 支持传入多个提示词，按顺序逐个执行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量代码生成请求")
public class BatchCodeGenerationRequest {

    @Schema(description = "代码生成提示列表", example = "[\"创建User实体类\", \"创建UserService\"]", required = true)
    @NotEmpty(message = "提示词列表不能为空")
    private List<String> prompts;

    @Schema(description = "项目名称", example = "my-java-project", required = true)
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "会话ID（可选，用于多轮对话）", example = "uuid")
    private String sessionId;

    @Schema(description = "是否在失败时继续执行", example = "false")
    @Builder.Default
    private Boolean continueOnFailure = false;
}
