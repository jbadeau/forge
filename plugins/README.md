# Forge Plugins

This directory contains all Forge plugins organized by their purpose and functionality.

## Directory Structure

```
plugins/
├── projects/     # Project Discovery Plugins
├── actions/      # Action Enhancement Plugins  
└── executors/    # Task Execution Plugins
```

### 📁 `/projects` - Project Discovery Plugins

**Purpose**: Discover and configure projects from configuration files

**Examples**:
- `forge-maven-plugin` - Discovers Maven projects from `pom.xml`
- `forge-js-plugin` - Discovers JavaScript/TypeScript projects from `package.json`
- `forge-go-plugin` - Discovers Go projects from `go.mod`
- `forge-docker-plugin` - Discovers Docker projects from `Dockerfile`

**Lifecycle**: Run once during workspace discovery

### 📁 `/actions` - Action Enhancement Plugins

**Purpose**: Add cross-cutting concerns to task execution (caching, tracing, security, etc.)

**Examples**:
- `forge-cache-plugin` - Adds caching actions to tasks
- `forge-tracing-plugin` - Adds distributed tracing spans
- `forge-security-plugin` - Adds security scanning actions

**Lifecycle**: Run during action graph construction (per execution)

### 📁 `/executors` - Task Execution Plugins

**Purpose**: Execute tasks in different runtime environments

**Examples**:
- `forge-local-executor` - Executes tasks on the local machine
- `forge-remote-executor` - Executes tasks on remote build servers
- `forge-docker-executor` - Executes tasks in Docker containers

**Lifecycle**: Run during task execution (per action)

## Plugin Development

Each directory contains plugins that implement their respective interfaces:

- **Project Plugins**: Implement `com.forge.plugin.ProjectPlugin`
- **Action Plugins**: Implement `com.forge.plugin.api.ActionPlugin`
- **Executor Plugins**: Implement `com.forge.plugin.api.ExecutorPlugin`

## Plugin Loading

Plugins are discovered and loaded through:

1. **ServiceLoader** - Built-in plugins via `META-INF/services/`
2. **Directory Scanning** - JARs in plugin directories
3. **Configuration** - Explicit plugin configuration in `.forge/plugins.yml`

For detailed documentation, see `/docs/PLUGIN_ARCHITECTURE.md`.