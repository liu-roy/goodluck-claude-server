package com.goodluck.ai.claude.api.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件树节点（前端代码树展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件树节点")
public class FileTreeNode {

    @Schema(description = "显示名称")
    private String name;

    @Schema(description = "相对项目根的路径")
    private String path;

    @Schema(description = "是否目录")
    private boolean dir;

    @Schema(description = "子节点（仅目录有）")
    private List<FileTreeNode> children;
}
