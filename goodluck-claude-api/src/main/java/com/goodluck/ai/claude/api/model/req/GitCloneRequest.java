package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Git克隆请求DTO
 * @author liuleyi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Git克隆并初始化Claude规则请求")
public class GitCloneRequest {

    @Schema(description = "Git仓库URL", example = "https://github.com/example/project.git", required = true)
    @NotBlank(message = "Git仓库URL不能为空")
    private String gitUrl;

    @Schema(description = "分支名称（可选，默认为主分支）", example = "develop")
    private String branch;

    @Schema(description = "项目目录名（可选，不填则从 gitUrl 解析，如 xxx/repo.git -> repo）", example = "my-project")
    private String projectName;

    @Schema(description = "Git 用户名（私有仓库必填）", example = "your-username")
    private String gitUsername;

    @Schema(description = "Git 密码或 Token（私有仓库必填）", example = "ghp_xxx 或 password")
    private String gitPassword;
}