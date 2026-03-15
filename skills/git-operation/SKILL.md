# Git Operations Skill

## 概述
本 skill 用于指导 Claude 执行 Git 操作，包括克隆、提交、推送等。

## Git 认证配置

当执行需要认证的 Git 操作（如 clone 私有仓库、push 等）时，需要从用户提示词中获取凭证信息：

### 获取凭证的方式

1. **从提示词获取**：用户应在提示词中提供 Git 用户名和密码/Token
   - 格式示例：`git用户名: xxx, git密码: xxx`
   - 或：`使用用户名 xxx 和 token xxx 进行git操作`

2. **环境变量**（如已配置）：
   - `GIT_USERNAME`
   - `GIT_PASSWORD` 或 `GIT_TOKEN`

## 支持的 Git 操作

### 1. 克隆仓库 (clone)
```bash
git clone <repository-url> [directory]
git clone -b <branch> <repository-url>
```

带认证克隆：
```bash
git clone https://<username>:<password>@github.com/user/repo.git
```

### 2. 添加文件 (add)
```bash
git add .                    # 添加所有更改
git add <file>               # 添加指定文件
git add -A                   # 添加所有（包括删除）
```

### 3. 提交更改 (commit)
```bash
git commit -m "commit message"
git commit -am "message"     # 添加并提交已跟踪的文件
```

### 4. 推送到远程 (push)
```bash
git push origin <branch>
git push -u origin <branch>  # 设置上游分支
git push --force             # 强制推送（谨慎使用）
```

### 5. 拉取更新 (pull)
```bash
git pull origin <branch>
git pull --rebase origin <branch>
```

### 6. 分支操作
```bash
git branch <branch-name>           # 创建分支
git checkout <branch-name>         # 切换分支
git checkout -b <branch-name>      # 创建并切换
git branch -d <branch-name>        # 删除分支
```

### 7. 查看状态
```bash
git status                   # 查看状态
git log --oneline -n 10      # 查看最近10条提交
git diff                     # 查看差异
```

### 8. 重置操作
```bash
git reset --soft <commit>    # 保留更改
git reset --mixed <commit>   # 默认，取消暂存
git reset --hard <commit>    # 丢弃所有更改
```

## 使用示例

### 示例1：克隆并推送更改
```
用户提示: 使用用户名 developer 和 token ghp_xxxx 克隆 https://github.com/user/repo.git，修改后推送

操作步骤:
1. git clone https://developer:ghp_xxxx@github.com/user/repo.git
2. cd repo
3. # 进行修改...
4. git add .
5. git commit -m "Update code"
6. git push origin main
```

### 示例2：创建新分支开发
```
用户提示: 在项目中创建 feature/new-api 分支并推送

操作步骤:
1. git checkout -b feature/new-api
2. # 进行开发...
3. git add .
4. git commit -m "Add new API feature"
5. git push -u origin feature/new-api
```

## 注意事项

1. **凭证安全**：不要在代码或日志中明文记录密码/Token
2. **分支保护**：推送到 main/master 前确认是否需要 PR
3. **冲突处理**：推送前先 pull 最新代码
4. **提交信息**：使用有意义的提交消息
