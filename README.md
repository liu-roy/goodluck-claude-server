# 🍃 Claude Code Server (Spring Boot版)

基于Spring Boot的Claude AI代码生成服务器，提供RESTful API接口，支持代码生成、项目管理等功能。

## ✨ 特性

- 🚀 **Spring Boot框架** - 企业级Java应用框架
- 📚 **自动API文档** - Swagger UI集成，自动生成交互式API文档
- 🔧 **代码生成** - 集成Claude AI进行智能代码生成
- 📁 **项目管理** - 完整的项目生命周期管理
- 📄 **文件操作** - 文件浏览、下载、内容查看
- 🛡️ **安全控制** - 路径安全验证，防止目录遍历攻击
- 📊 **健康监控** - Spring Boot Actuator集成
- 🎯 **类型安全** - 完整的参数验证和异常处理

## 🛠️ 技术栈

- **Java 17+** - 现代Java版本
- **Spring Boot 3.2.0** - 最新的Spring Boot框架
- **SpringDoc OpenAPI** - API文档自动生成
- **Lombok** - 减少样板代码
- **Jackson** - JSON序列化/反序列化
- **Apache Commons** - 实用工具库

## 📋 前置要求

1. **Java 17或更高版本**
2. **Maven 3.6+**
3. **Claude Code** - 需要安装claude-code命令行工具
4. **网络代理**（如果需要）- 用于访问Claude API

## 🚀 快速开始

### 1. 克隆项目

```bash
# 如果这是独立项目
git clone <repository-url>
cd claude-server-spring

# 或者直接在已有的claude-server目录下
cd claude-server-spring
```

### 2. 配置环境

编辑 `src/main/resources/application.yml` 文件：

```yaml
claude:
  # Claude可执行文件路径（确保在PATH中）
  executable: claude
  
  # 工作空间目录
  workspace-dir: ./generated
  
  # 代理配置（如果需要）
  proxy:
    enabled: true
    http: http://127.0.0.1:7897
    https: http://127.0.0.1:7897
    socks: socks5://127.0.0.1:7897
```

### 3. 启动服务器

#### 方式一：使用启动脚本（推荐）

```bash
./start.sh
```

#### 方式二：使用Maven

```bash
# 编译并运行
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/claude-server-1.0.0.jar
```

### 4. 访问服务

启动成功后，可以访问以下地址：

- **API文档**: http://localhost:8080/docs
- **健康检查**: http://localhost:8080/api/health
- **应用监控**: http://localhost:8080/actuator/health

## 📖 API文档

启动服务器后，访问 http://localhost:8080/docs 查看完整的API文档。

### 主要API端点

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/version` | 版本信息 |
| POST | `/api/projects/generate` | 生成代码项目 |
| GET | `/api/projects` | 获取所有项目 |
| GET | `/api/projects/{id}` | 获取项目详情 |
| DELETE | `/api/projects/{id}` | 删除项目 |
| GET | `/api/projects/{id}/files/{path}` | 获取文件内容 |
| GET | `/api/projects/{id}/download/{path}` | 下载文件 |

### 代码生成示例

```bash
# 生成Java Hello World程序
curl -X POST http://localhost:8080/api/projects/generate \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "创建一个Java的Hello World程序",
    "projectName": "java-hello-world"
  }'
```

## 🔧 配置说明

### 应用配置 (`application.yml`)

```yaml
# Spring Boot基础配置
spring:
  application:
    name: claude-server
server:
  port: 8080

# Claude配置
claude:
  executable: claude                    # Claude可执行文件
  workspace-dir: ./generated           # 工作空间目录
  timeout: 120                         # 命令超时时间（秒）
  max-concurrent-processes: 5          # 最大并发进程数
  
  # 代理配置
  proxy:
    enabled: false                     # 是否启用代理
    http: http://127.0.0.1:7897       # HTTP代理
    https: http://127.0.0.1:7897      # HTTPS代理
    socks: socks5://127.0.0.1:7897    # SOCKS代理

# API文档配置
springdoc:
  api-docs:
    path: /api-docs                    # API文档JSON地址
  swagger-ui:
    path: /docs                        # Swagger UI地址
    try-it-out-enabled: true          # 启用在线测试
```

## 📁 项目结构

```
claude-server-spring/
├── src/main/java/com/claude/server/
│   ├── ClaudeServerApplication.java      # 主应用类
│   ├── config/                          # 配置类
│   │   ├── ClaudeProperties.java        # Claude配置属性
│   │   └── OpenApiConfig.java           # API文档配置
│   ├── controller/                      # REST控制器
│   │   ├── HealthController.java        # 健康检查控制器
│   │   └── ProjectController.java       # 项目管理控制器
│   ├── service/                         # 业务服务
│   │   ├── ClaudeExecutorService.java   # Claude执行服务
│   │   └── ProjectService.java          # 项目管理服务
│   ├── dto/                            # 数据传输对象
│   │   ├── ApiResponse.java            # API响应包装器
│   │   ├── CodeGenerationRequest.java  # 代码生成请求
│   │   └── ProjectInfo.java            # 项目信息
│   └── exception/                      # 异常处理
│       └── GlobalExceptionHandler.java # 全局异常处理器
├── src/main/resources/
│   └── application.yml                 # 应用配置文件
├── pom.xml                            # Maven配置
├── start.sh                           # 启动脚本
└── README.md                          # 项目文档
```

## 🧪 测试

### 1. 健康检查测试

```bash
curl http://localhost:8080/api/health
```

### 2. 代码生成测试

```bash
# 生成Python程序
curl -X POST http://localhost:8080/api/projects/generate \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "创建一个Python待办事项管理器",
    "projectName": "python-todo"
  }'
```

### 3. 项目管理测试

```bash
# 获取所有项目
curl http://localhost:8080/api/projects

# 获取项目详情
curl http://localhost:8080/api/projects/{projectId}

# 获取文件内容
curl http://localhost:8080/api/projects/{projectId}/files/{fileName}
```

## 🔍 故障排除

### 常见问题

1. **Claude命令找不到**
   ```
   确保claude-code已正确安装并在PATH中可用
   ```

2. **网络连接问题**
   ```
   检查代理配置是否正确
   ```

3. **端口被占用**
   ```
   修改application.yml中的server.port配置
   ```

4. **Java版本问题**
   ```
   确保使用Java 17或更高版本
   ```

### 日志查看

日志文件位置：`logs/claude-server.log`

```bash
# 查看实时日志
tail -f logs/claude-server.log

# 查看错误日志
grep ERROR logs/claude-server.log
```

## 🤝 贡献

欢迎提交问题和改进建议！

## 📄 许可证

MIT License

## 📞 支持

如有问题，请访问API文档或查看日志文件获取详细信息。
