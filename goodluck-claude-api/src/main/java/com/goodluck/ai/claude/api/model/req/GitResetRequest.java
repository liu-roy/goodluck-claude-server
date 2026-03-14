package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Git分支创建请求")
public class GitResetRequest {

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "reset commitId ", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reset commitId 不能为空")
    private String commitId;

    @Schema(description = "reset branch ,为空默认当前分支")
    private String branch;

}
