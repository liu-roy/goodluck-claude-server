package com.goodluck.ai.claude.service.service;

import com.google.common.base.Throwables;
import com.goodluck.ai.claude.service.config.ClaudeProperties;
import com.goodluck.ai.claude.service.constant.ErrorCode;
import com.goodluck.ai.claude.api.model.to.ClaudeGenerationCommand;
import com.goodluck.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claude 命令执行服务
 */
@Slf4j
@Service
public class ClaudeExecutorService {

    @Autowired
    private ClaudeProperties claudeProperties;

    /**
     * 活跃进程映射
     */
    private final Map<String, DefaultExecutor> activeExecutors = new ConcurrentHashMap<>();

    /**
     * 执行 Claude 命令（支持长时间运行和会话）
     *
     * @param prompt         用户提示
     * @param projectName    项目名
     * @param timeoutSeconds 超时时间（秒）
     * @param sessionId      会话ID（可选，用于支持多轮对话）
     * @param systemPrompt   系统提示（可选，用于设定对话上下文）
     * @param isNewSession   默认为 null
     */
    public CommandResult executeCommand(String prompt, String projectName,
                                        Integer timeoutSeconds, String sessionId, String systemPrompt, boolean isNewSession) {
        ClaudeGenerationCommand command = ClaudeGenerationCommand.builder()
                .prompt(prompt)
                .projectName(projectName)
                .sessionId(sessionId)
                .systemPrompt(systemPrompt)
                .newSession(isNewSession)
                .timeout(timeoutSeconds)
                .build();

        return executeCommand(command);
    }

    /**
     * 执行 Claude 命令（支持长时间运行和会话）
     */
    public CommandResult executeCommand(ClaudeGenerationCommand command) {
        String processId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        String prompt = command.getPrompt();
        String projectName = command.getProjectName();
        String sessionId = command.getSessionId();
        String systemPrompt = command.getSystemPrompt();
        boolean isNewSession = command.getNewSession();
        try {
            log.info("开始执行 Claude 命令，进程ID: {}, 项目名: {}, 会话ID: {}", processId, projectName, sessionId);

            // 设置工作目录为项目目录
            Path workingDir = getProjectWorkingDirectory(projectName);
            log.info("Claude工作目录: {}", workingDir);

            // 构建命令
            CommandLine commandLine = buildCommandLine(prompt, sessionId, systemPrompt, isNewSession);
            log.info("执行命令: {}", commandLine);

            // 创建 DefaultExecutor 实例
            DefaultExecutor executor = new DefaultExecutor();

            // 设置工作目录
            executor.setWorkingDirectory(workingDir.toFile());

            // 设置超时时间（默认10分钟）
            int timeout = command.getTimeout() != null ? command.getTimeout() : 600;
            // 转换为毫秒
            ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout * 1000L);
            executor.setWatchdog(watchdog);
            log.info("命令执行超时时间设置为: {}秒", timeout);

            // 创建输出和错误流处理器
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
            executor.setStreamHandler(streamHandler);

            // 设置环境变量
            Map<String, String> environment = new HashMap<>();
            // environment.putAll(setupProxyEnvironmentMap());
            environment.putAll(setupThirdModelEnvironmentMap());

            // 保存 executor 以便后续管理
            activeExecutors.put(processId, executor);

            // 执行命令
            int exitCode;
            // 使用异步执行以便更好地处理超时
            DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
            executor.execute(commandLine, environment, resultHandler);

            // 等待执行完成
            resultHandler.waitFor(timeout * 1000L);

            if (resultHandler.hasResult()) {
                exitCode = resultHandler.getExitValue();
                log.info("Claude 命令执行完成，进程ID: {}, 退出码: {}", processId, exitCode);
            } else {
                // 超时
                watchdog.destroyProcess();
                exitCode = -1;
                log.warn("Claude 命令执行超时，进程ID: {}, 已强制终止", processId);
                errorStream.write(String.format("命令执行超时（%d秒）", timeout).getBytes());
            }

            // 列出生成的文件
//            listGeneratedFiles(workingDir);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("命令执行时间: {}ms", executionTime);

            // 获取输出内容
            String output = outputStream.toString();
            String error = errorStream.toString();

            // 实时输出日志（如果需要）
            if (StringUtils.isNotBlank(output)) {
                log.info("Claude输出: \n{}", output);
            }
            if (StringUtils.isNotBlank(error)) {
                log.error("Claude错误: \n{}", error);
            }

            if (exitCode != 0) {
                if (exitCode == 1 && "Prompt is too long".equals(output)) {
                    throw new BusinessException(ErrorCode.CLAUDE_PROMPT_TOO_LONG, "提示词超长");
                } else if (exitCode == -1) {
                    throw new BusinessException(ErrorCode.CLAUDE_RUN_TIMEOUT, "运行时间超时");
                } else {
                    String errorMsg = error.trim();
                    if (StringUtils.isBlank(errorMsg)) {
                        errorMsg = output;
                    }
                    throw new BusinessException(ErrorCode.CLAUDE_UNKNOWN_ERROR, errorMsg);
                }

            } else {
                return CommandResult.builder()
                        .processId(processId)
                        .success(true)
                        .exitCode(exitCode)
                        .output(output)
                        .error(error)
                        .workingDirectory(workingDir.toString())
                        .sessionId(sessionId)
                        .executionTime(executionTime)
                        .build();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("执行 Claude 命令失败，执行时间{}, 进程ID: {}, 报错:{}", processId, executionTime, Throwables.getStackTraceAsString(e));
            throw new BusinessException(ErrorCode.CLAUDE_UNKNOWN_ERROR, e.getMessage());
        } finally {
            // 清理进程记录
            activeExecutors.remove(processId);
            // 关闭流
            try {
                outputStream.close();
                errorStream.close();
            } catch (IOException e) {
                log.warn("关闭流失败", e);
            }
        }
    }

    /**
     * 列出生成的文件
     */
    private void listGeneratedFiles(Path directory) {
        try (var paths = java.nio.file.Files.walk(directory, 2)) {
            log.info("项目目录 {} 中的文件：", directory);
            paths.filter(java.nio.file.Files::isRegularFile)
                    .forEach(path -> log.info("  - {}", directory.relativize(path)));
        } catch (IOException e) {
            log.error("无法列出生成的文件", e);
        }
    }

    /**
     * 构建 Claude 命令行（使用 Apache Commons Exec）
     */
    private CommandLine buildCommandLine(String prompt, String sessionId, String systemPrompt, boolean isNewSession) {
        // claude 可执行文件
        CommandLine commandLine = new CommandLine(claudeProperties.getExecutable());

        // 添加 --dangerously-skip-permissions 标志来跳过所有人工确认
        commandLine.addArgument("--dangerously-skip-permissions");

        // 根据是否为新项目决定使用哪种参数
        if (StringUtils.isNotBlank(sessionId)) {
            if (isNewSession) {
                // 新项目使用 --session-id 创建新会话
                commandLine.addArgument("--session-id");
                commandLine.addArgument(sessionId);
                log.info("使用 --session-id 创建新会话: {}", sessionId);
            } else {
                // 已有项目使用 --resume 继续会话
                commandLine.addArgument("--resume");
                commandLine.addArgument(sessionId);
                log.info("使用 --resume 参数继续会话: {}", sessionId);
            }
        }

        // 添加系统提示（如果有）- 用于设定对话上下文
        if (StringUtils.isNotBlank(systemPrompt)) {
            commandLine.addArgument("--append-system-prompt");
            // 使用 handleQuoting=false 避免引号处理问题
            commandLine.addArgument(systemPrompt, false);
            log.info("添加系统提示以设定上下文");
        }

        // 增强提示词，确保生成实际代码文件
        String enhancedPrompt = prompt;
        // 使用 handleQuoting=false 避免引号处理问题
        commandLine.addArgument(enhancedPrompt, false);

        return commandLine;
    }

    /**
     * 获取项目工作目录
     */
    private Path getProjectWorkingDirectory(String projectId) {
        Path workspaceDir = Paths.get(claudeProperties.getWorkspaceDir());
        return workspaceDir;
    }

    private Map<String, String> setupThirdModelEnvironmentMap() {
        Map<String, String> env = new HashMap<>();
        if (!claudeProperties.getEnableThirdModel()) {
            return env;
        }
        // 保留系统原有的环境变量
        Map<String, String> systemEnv = System.getenv();
        env.putAll(systemEnv);

        env.put("ANTHROPIC_MODEL", claudeProperties.getModel());
        env.put("ANTHROPIC_SMALL_FAST_MODEL", claudeProperties.getFastModel());

        if ("claude".equals(claudeProperties.getThirdModelType())) {
            env.put("ANTHROPIC_AUTH_TOKEN", claudeProperties.getApiKey());
        } else {
            env.put("ANTHROPIC_API_KEY", claudeProperties.getApiKey());
        }

        env.put("ANTHROPIC_BASE_URL", claudeProperties.getBaseUrl());
        return env;
    }

    /**
     * 设置环境变量Map（用于 Apache Commons Exec）
     */
    private Map<String, String> setupProxyEnvironmentMap() {
        Map<String, String> env = new HashMap<>();

        // 保留系统原有的环境变量
        Map<String, String> systemEnv = System.getenv();
        env.putAll(systemEnv);

        ClaudeProperties.ProxyConfig proxy = claudeProperties.getProxy();
        if (proxy == null) {
            proxy = new ClaudeProperties.ProxyConfig();
            proxy.setEnabled(true);
            proxy.setHttp("http://127.0.0.1:7897");
            proxy.setHttps("http://127.0.0.1:7897");
            proxy.setSocks("socks5://127.0.0.1:7897");
            log.info("代理配置为空，使用默认代理: 127.0.0.1:7897");
        }

        if (proxy.isEnabled()) {
            // 设置所有可能的代理环境变量格式
            if (StringUtils.isNotBlank(proxy.getHttp())) {
                env.put("http_proxy", proxy.getHttp());
                env.put("HTTP_PROXY", proxy.getHttp());
            }
            if (StringUtils.isNotBlank(proxy.getHttps())) {
                env.put("https_proxy", proxy.getHttps());
                env.put("HTTPS_PROXY", proxy.getHttps());
            }
            if (StringUtils.isNotBlank(proxy.getSocks())) {
                env.put("all_proxy", proxy.getSocks());
                env.put("ALL_PROXY", proxy.getSocks());
            }

            // 额外设置NO_PROXY以排除本地地址
            env.put("no_proxy", "localhost,127.0.0.1");
            env.put("NO_PROXY", "localhost,127.0.0.1");
        } else {
            log.warn("代理未启用，Claude命令可能无法正常执行");
        }

        return env;
    }

    /**
     * 获取 Claude 版本信息
     */
    public String getClaudeVersion() {
        try {
            CommandLine commandLine = new CommandLine(claudeProperties.getExecutable());
            commandLine.addArgument("--version");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

            DefaultExecutor executor = new DefaultExecutor();
            executor.setStreamHandler(streamHandler);
            executor.setExitValue(0);

            // 10秒超时
            ExecuteWatchdog watchdog = new ExecuteWatchdog(10000);
            executor.setWatchdog(watchdog);

            int exitCode = executor.execute(commandLine);

            if (exitCode == 0) {
                return outputStream.toString().trim();
            }

        } catch (Exception e) {
            log.warn("无法获取 Claude 版本信息", e);
        }

        return "Unknown";
    }

    /**
     * 终止指定进程
     */
    public boolean terminateProcess(String processId) {
        DefaultExecutor executor = activeExecutors.get(processId);
        if (executor != null) {
            try {
                ExecuteWatchdog watchdog = executor.getWatchdog();
                if (watchdog != null) {
                    watchdog.destroyProcess();
                }
                activeExecutors.remove(processId);
                log.info("成功终止进程: {}", processId);
                return true;
            } catch (Exception e) {
                log.error("终止进程失败: {}", processId, e);
            }
        }
        return false;
    }

    /**
     * 获取活跃进程数量
     */
    public int getActiveProcessCount() {
        // Apache Commons Exec 不提供直接检查进程是否存活的方法
        // 所以我们只返回映射的大小
        return activeExecutors.size();
    }

    /**
     * 命令执行结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CommandResult {
        private String processId;
        private boolean success;
        private int exitCode;
        private String output;
        private String error;
        private String workingDirectory;
        private String sessionId; // 会话ID（如果使用了会话）
        private Long executionTime; // 执行时间（毫秒）
    }
}
