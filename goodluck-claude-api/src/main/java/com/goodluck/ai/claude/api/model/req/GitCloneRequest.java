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

}