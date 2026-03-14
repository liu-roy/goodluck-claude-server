package com.goodluck.ai.claude.service.annotation;

import java.lang.annotation.*;

/**
 * Git自动提交注解
 * 在方法执行成功后，自动将生成的代码提交到Git仓库
 *
 * 使用方式:
 * @AutoGitCommit(
 *     messageFromParam = "prompt",           // 从方法参数的prompt字段获取commit message
 *     defaultMessage = "AI generated code",  // 默认commit message
 *     projectNameFromParam = "projectName"   // 从方法参数获取项目名
 * )
 *
 * @author Claude AI
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoGitCommit {

    /**
     * 从方法参数中获取commit message的字段名
     * 例如: "prompt" 表示从参数的 getPrompt() 方法获取
     * 默认为 "prompt"
     */
    String messageFromParam() default "prompt";

    /**
     * 默认的commit message
     * 当无法从参数获取message时使用
     */
    String defaultMessage() default "AI generated code by Claude";

    /**
     * 从方法参数中获取项目名的字段名
     * 例如: "projectName" 表示从参数的 getProjectName() 方法获取
     * 如果为空，则从返回值的 getName() 方法获取
     */
    String projectNameFromParam() default "projectName";

    /**
     * 是否在项目名为空时跳过提交
     * 默认为 true
     */
    boolean skipIfProjectNameEmpty() default true;

    /**
     * 是否在方法执行失败时跳过提交
     * 默认为 true（只在成功时提交）
     */
    boolean skipOnFailure() default true;

    /**
     * commit message的最大长度
     * 超过此长度将被截断
     */
    int maxMessageLength() default 100;

    /**
     * 是否启用自动推送到远程仓库
     * 默认为 false（只提交到本地）
     */
    boolean autoPush() default false;
}
