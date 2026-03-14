package com.goodluck.ai.claude.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "")
public class IgnoreModifyConfig {
    /**
     * 不可修改的文件列表
     */
    private List<String> ignoreModifyFiles;
}
