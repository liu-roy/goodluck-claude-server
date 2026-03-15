---
name: maven-compile
description: 执行 Maven 编译. Use when the user asks for 编译、mvn compile、构建、build.
---

# Maven 编译

## 解释
- **入参**: `projectName`（项目名）, `branch`（可选，编译前切换分支）
- **行为**: 在项目目录执行 `mvn clean install`，跳过 Checkstyle/PMD/SpotBugs，超时 5 分钟

## 何时使用

- 用户要求**编译**项目、执行 **mvn compile** / **mvn install**、构建

## 执行步骤

1. **工作目录**：项目根目录 = `workspaceDir / projectName`，其中默认 `workspaceDir = /home/haierjava/cloneDir`（可按实际部署修改）；`projectName` 由调用方/用户提供。
2. **可选切换分支**：若用户提供 `branch` 且非空，先在项目根目录执行 `git checkout <branch>`，失败则直接报错不执行 Maven。
3. **执行命令**（在项目根目录）：
   ```bash
   mvn clean install -Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true
   ```
4. **超时**：5 分钟。
5. **成功**：退出码 0，返回成功。
6. **失败**：从 Maven 输出中提取错误摘要（如 `[ERROR]` 及关键行），不要整段日志刷屏，返回「编译失败: <摘要>」。

## 约定

- 仅做编译，不跑单测、不跑静态分析。
- 单测、静态分析由其他独立 skill 负责。
