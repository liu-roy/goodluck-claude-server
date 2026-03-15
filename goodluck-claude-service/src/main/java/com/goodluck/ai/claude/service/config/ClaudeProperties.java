package com.goodluck.ai.claude.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Claude 配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "claude")
public class ClaudeProperties {

    /**
     * Claude 模型名称
     */
    private Boolean enableThirdModel = true;
    private String thirdModelType = "claude";

    /**
     * Claude API Key
     */
    private String apiKey;
    /**
     *
     */
    private String baseUrl;
    /**
     * 模型名称
     */
    private String model;
    /**
     *
     */
    private String fastModel;

    /**
     * Claude 可执行文件路径
     */
    private String executable = "claude";

    /**
     * 工作空间目录
     */
    private String workspaceDir = "./generated";

    /**
     * 命令执行超时时间（秒）
     */
    private Integer timeout = 600;

    /**
     * 最大并发进程数
     */
    private Integer maxConcurrentProcesses = 5;

    /**
     * 是否启用代理
     */
    private String initCommand = "/init";

    private String generateCodePrompt = "";


    private String loadPrompt(Resource resource) throws Exception {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 代理配置
     */
    private ProxyConfig proxy = new ProxyConfig();

    @Data
    public static class ProxyConfig {
        /**
         * 是否启用代理
         */
        private boolean enabled = false;

        /**
         * HTTP 代理
         */
        private String http;

        /**
         * HTTPS 代理
         */
        private String https;

        /**
         * SOCKS 代理
         */
        private String socks;
    }
}
