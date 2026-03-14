# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot REST API server that acts as a bridge between HTTP clients and the Claude Code CLI tool, providing programmatic access to AI-powered code generation capabilities.

**Key Technologies:**
- Java 17 with Spring Boot 3.2+
- Maven build system
- Apache Commons Exec for process management
- Integration with Claude Code CLI

## Build and Run Commands

### Building the Project
```bash
# Clean build with Maven
mvn clean package

# The output JAR will be: target/bootstrap.jar
```

### Running the Application
```bash
# Option 1: Using the generated JAR
java -jar target/bootstrap.jar

# Option 2: Using Spring Boot Maven plugin
mvn spring-boot:run

# Option 3: Using provided run script
./run/run-java.sh
```

### Running Tests
```bash
# Run all tests
mvn test

# Run with specific profile
mvn test -Ptest
```

### Development Commands
```bash
# Skip tests during build
mvn clean package -DskipTests

# Run with specific profile
mvn spring-boot:run -Dspring.profiles.active=dev

# Check dependency tree
mvn dependency:tree
```

## Architecture Overview

### Layered Architecture Pattern

The application follows a clean layered architecture:

1. **Controller Layer** (`controller/`) - REST endpoints that handle HTTP requests
   - `ProjectController`: Project CRUD operations and file access
   - `SessionController`: Session management for multi-turn conversations
   - `SystemController`: System health and monitoring

2. **Service Layer** (`service/`) - Business logic
   - `ClaudeExecutorService`: Executes Claude CLI commands with timeout/error handling
   - `ProjectService`: Manages project lifecycle, file operations, and metadata

3. **Configuration Layer** (`config/`)
   - `ClaudeProperties`: Type-safe configuration binding for Claude settings

4. **DTO Layer** (`dto/`) - Data transfer objects for API contracts

### Key Architectural Decisions

**Process Execution Model**: The application executes the Claude CLI as an external process using Apache Commons Exec. Each execution:
- Runs in its own working directory under `./generated`
- Has configurable timeout (default 600 seconds)
- Supports concurrent execution with limits (default max 5)
- Captures stdout/stderr for response handling

**Session Management**: Sessions enable multi-turn conversations with Claude:
- UUID-based session IDs
- Sessions can be bound to projects or work independently
- Support for `--session-id` (new) and `--resume` (continue) flags
- Context maintained across API calls within a session

**Project Storage**: All generated projects are stored in the filesystem:
```
./generated/
└── project-{id}/
    ├── request.json       # Request metadata
    ├── response.json      # Execution result
    ├── .history/         # Historical requests/responses
    └── [generated files] # Claude's output
```

## Critical Implementation Details

### Claude CLI Integration

The core integration happens in `ClaudeExecutorService.executeCommand()` (service/ClaudeExecutorService.java:95-225):
- Builds command with flags: `--dangerously-skip-permissions`, `--session-id`/`--resume`, `--append-system-prompt`
- Sets up environment variables for proxy configuration if enabled
- Uses ProcessBuilder with proper working directory setup
- Implements timeout handling via ExecuteWatchdog

### Security Considerations

Path traversal prevention is implemented in `ProjectService.getFileContent()` (service/ProjectService.java:291-306):
- Normalizes requested file paths
- Validates that normalized path stays within project directory
- Throws SecurityException for invalid access attempts

### API Request Flow

For the main code generation endpoint:
1. `ProjectController.generateProject()` receives HTTP request
2. `ProjectService.generateProject()` validates and prepares execution
3. `ClaudeExecutorService.executeCommand()` runs Claude CLI
4. Response is saved to `response.json` with metadata
5. Project files are scanned and returned in response

## Configuration

Main configuration is in `src/main/resources/application.yml`:

```yaml
claude:
  executable: claude              # CLI command path
  workspace-dir: ./generated     # Project storage directory
  timeout: 600                   # Command timeout in seconds
  max-concurrent-processes: 5    # Concurrent execution limit
  proxy:                         # Optional proxy configuration
    enabled: true
    http: http://127.0.0.1:7897
```

Profile-specific configs: `application-dev.yml`, `application-test.yml`, `application-pro.yml`

## Key API Endpoints

- `POST /api/projects/generate` - Generate new project from prompt
- `GET /api/projects` - List all projects
- `POST /api/sessions/execute/{sessionId}` - Execute command in session
- `GET /api/system/info` - System health information
- API documentation available at: `/swagger-ui.html`

## Dependencies and External Requirements

### Critical External Dependency
- **Claude Code CLI**: Must be installed and accessible via PATH as `claude` command
- The application will fail to execute if Claude CLI is not available

### Maven Dependencies
Parent POM: `com.goodluck:goodluck-framework:1.0.0-SNAPSHOT`

Key dependencies managed in `pom.xml`:
- Spring Boot starters (web, validation, actuator)
- Apache Commons Exec for process management
- Spring Cloud for microservices capabilities
- JGit for Git operations

## Common Development Tasks

### Adding a New API Endpoint
1. Create method in appropriate controller class
2. Add corresponding service method if business logic is needed
3. Create DTOs in `dto/` package for request/response
4. API will automatically appear in Swagger documentation

### Modifying Claude Execution Behavior
- Main logic in `ClaudeExecutorService.executeCommand()`
- Command building starts at line 138
- Environment setup at lines 164-176
- Timeout configuration at line 197

### Debugging Claude Executions
- Check logs for command construction and execution details
- Review saved `request.json` and `response.json` in project directories
- Use `.history/` subdirectory for tracking request evolution

## Error Handling

The application uses Spring's exception handling with:
- Global exception handlers via `@ControllerAdvice` (if present)
- Custom SecurityException for path traversal attempts
- Process execution errors captured and returned in API response
- Timeout exceptions handled with process termination

## Monitoring and Health

- Health endpoint: `/actuator/health`
- Prometheus metrics: `/actuator/prometheus`
- Custom system info: `/api/system/info`
- Active process count available via `ClaudeExecutorService.getActiveProcessCount()`