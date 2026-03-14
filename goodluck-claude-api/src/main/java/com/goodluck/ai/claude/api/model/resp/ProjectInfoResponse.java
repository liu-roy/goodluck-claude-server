package com.goodluck.ai.claude.api.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 项目信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "项目信息")
public class ProjectInfoResponse {

    @Schema(description = "项目名称", example = "Java Hello World")
    private String projectName;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdTime;

    @Schema(description = "最后修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastModified;

    @Schema(description = "生成状态", example = "SUCCESS")
    private String status;

    @Schema(description = "错误信息（如果有）")
    private String error;

    @Schema(description = "会话ID（UUID格式）", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;

    @Schema(description = "Git提交ID")
    private String commitId;

}
