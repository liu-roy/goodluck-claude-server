package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Git分支创建请求")
public class GitBranchRequest {

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "源分支名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "源分支名称不能为空")
    private String sourceBranch;

    @Schema(description = "新分支名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新分支名称不能为空")
    private String newBranch;
}
