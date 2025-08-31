# Forge Demo Workspace

This is a comprehensive example workspace demonstrating the capabilities of the Forge build tool - a Kotlin-based build system inspired by Nx. This directory contains only the demo workspace itself, not a Maven module.

## Project Structure

```
forge-demo/
├── apps/
│   ├── mobile-app/          # React Native mobile application
│   └── web-app/             # React web application with Docker support
├── libs/
│   ├── go-utils/            # Go utility library
│   ├── shared-lib/          # Java shared library
│   ├── ui-components/       # React UI component library
│   └── utils/               # JavaScript utility library
├── services/
│   ├── api-gateway/         # Go API gateway service with Docker
│   ├── auth-service/        # Java authentication service with Docker
│   └── user-service/        # Java user management service
└── tools/
    └── deployment-cli/      # Node.js deployment CLI tool
```

## Target Configuration

All projects use the `forge:run-commands` executor for consistent command execution:

### Standard Plugin-Inferred Targets

**JavaScript/TypeScript Projects:**
```json
{
  "build": {
    "executor": "forge:run-commands",
    "options": {
      "commands": ["npm run build"],
      "cwd": "apps/web-app"
    }
  }
}
```

**Go Projects:**
```json
{
  "build": {
    "executor": "forge:run-commands", 
    "options": {
      "commands": ["go build ./..."],
      "cwd": "libs/go-utils"
    }
  }
}
```

**Maven Projects:**
```json
{
  "compile": {
    "executor": "forge:run-commands",
    "options": {
      "commands": ["mvn compile"],
      "cwd": "services/auth-service"
    }
  }
}
```

**Docker Projects:**
```json
{
  "docker-build": {
    "executor": "forge:run-commands",
    "options": {
      "commands": ["docker build -t web-app:latest ."],
      "cwd": "apps/web-app"
    }
  }
}
```

### Advanced Executor Features

**Multi-Step Sequential Commands**:
```json
{
  "custom-build": {
    "executor": "forge:run-commands",
    "options": {
      "commands": [
        "npm run clean",
        "npm run build", 
        "npm run test",
        "npm run package"
      ],
      "parallel": false
    }
  }
}
```

**Commands with Environment Variables**:
```json
{
  "deploy": {
    "executor": "forge:run-commands",
    "options": {
      "commands": [
        "echo \"Deploying $PROJECT_NAME to $ENVIRONMENT\"",
        "docker build -t $PROJECT_NAME:$VERSION .",
        "docker push $REGISTRY/$PROJECT_NAME:$VERSION"
      ],
      "env": {
        "PROJECT_NAME": "web-app",
        "ENVIRONMENT": "production",
        "VERSION": "1.0.0",
        "REGISTRY": "my-registry.com"
      }
    }
  }
}
```

**Parallel Command Execution**:
```json
{
  "test-all": {
    "executor": "forge:run-commands",
    "options": {
      "commands": [
        "npm run test:unit",
        "npm run test:integration", 
        "npm run test:e2e"
      ],
      "parallel": true
    }
  }
}
```

## Available Commands

### Project Discovery
```bash
# List all projects
forge show projects

# Show specific project details  
forge show project web-app

# JSON output for tooling integration
forge show projects --json
```

### Task Execution
```bash
# Run single project task
forge run web-app build

# Run task across multiple projects
forge run-many --target=test --tags=frontend

# Dry run to see execution plan
forge run web-app build --dry-run

# Verbose output
forge run web-app build --verbose
```

### Dependency Visualization  
```bash
# Show project dependency graph
forge graph

# JSON format for analysis
forge graph --json
```

## Plugin Configuration

The workspace is configured with plugins in `forge.json`:

```json
{
  "plugins": [
    {
      "plugin": "@forge/js",
      "options": {
        "buildTargetName": "build",
        "testTargetName": "test", 
        "lintTargetName": "lint"
      }
    },
    {
      "plugin": "@forge/maven",
      "options": {
        "buildTargetName": "compile",
        "testTargetName": "test",
        "packageTargetName": "package"
      }
    },
    {
      "plugin": "@forge/go", 
      "options": {
        "buildTargetName": "build",
        "testTargetName": "test"
      }
    },
    {
      "plugin": "@forge/docker",
      "options": {
        "buildTargetName": "docker-build",
        "runTargetName": "docker-run", 
        "pushTargetName": "docker-push"
      }
    }
  ]
}
```

## Executor Features

The `forge:run-commands` executor supports:

- **Sequential command execution** (default)
- **Parallel command execution** (`"parallel": true`)
- **Working directory specification** (`"cwd": "path/to/directory"`)
- **Environment variable injection** (`"env": {...}`)
- **Command chaining with proper error handling**
- **Real-time output streaming in verbose mode**

## Dependencies

The demo workspace includes realistic dependency relationships:

- **web-app** → ui-components → utils
- **mobile-app** → ui-components → utils  
- **user-service** → shared-lib, auth-service
- **auth-service** → shared-lib
- **api-gateway** → go-utils
- **deployment-cli** → utils

These dependencies are automatically inferred by the plugins and used for task execution ordering.

## Testing the Demo

```bash
# Navigate to the demo workspace
cd forge-demo

# Build the native CLI (from project root)
mise run build-native

# Show all discovered projects
../forge-cli/target/forge show projects

# Test JavaScript build pipeline with dependencies
../forge-cli/target/forge run web-app build --dry-run

# Test Maven build
../forge-cli/target/forge run auth-service compile --dry-run

# Test Go build
../forge-cli/target/forge run go-utils build --dry-run
```

This demo workspace provides a comprehensive example of using Forge with multiple languages, frameworks, and deployment targets in a realistic monorepo structure.