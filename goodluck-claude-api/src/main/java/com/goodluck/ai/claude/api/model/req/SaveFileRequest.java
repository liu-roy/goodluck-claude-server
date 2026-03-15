package com.goodluck.ai.claude.api.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存文件请求")
public class SaveFileRequest {

    @NotBlank
    @Schema(description = "项目ID", required = true)
    private String projectId;

    @NotBlank
    @Schema(description = "文件相对路径", required = true)
    private String filePath;

    @Schema(description = "文件内容")
    private String content;
}
