package com.goodluck.ai.claude.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Claude Code 服务器主应用类
 * 
 * @author Claude Server Team
 * @version 1.0.0
 */
@EnableScheduling
@ComponentScan("com.goodluck")
@SpringBootApplication
@EnableConfigurationProperties
public class ClaudeServerApplication {

    public static void main(String[] args) {
        System.out.println("🚀 启动 Claude Code Server (Spring Boot版)...");
        System.out.println("📚 API文档将在启动后访问: http://localhost:8080/docs");
        System.out.println("🔍 健康检查地址: http://localhost:8080/api/health");
        
        SpringApplication.run(ClaudeServerApplication.class, args);
        
        System.out.println("✅ Claude Code Server 启动完成!");
        System.out.println("🌐 API文档地址: http://localhost:8080/docs");
        System.out.println("📊 监控地址: http://localhost:8080/actuator/health");
    }
}
