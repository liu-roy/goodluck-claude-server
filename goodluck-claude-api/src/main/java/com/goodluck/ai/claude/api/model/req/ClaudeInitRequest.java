package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "claude初始化请求")
public class ClaudeInitRequest {

    @Schema(description = "项目名称", example = "my-java-project")
    private String projectName;

}
