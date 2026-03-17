package com.goodluck.ai.claude.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.goodluck.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("claude_session")
public class SessionDO extends BaseDO<Long> {

    @TableField("session_id")
    private String sessionId;

    @TableField("project_name")
    private String projectName;
}
