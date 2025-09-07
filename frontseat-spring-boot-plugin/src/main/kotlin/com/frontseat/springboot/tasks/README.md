# Spring Boot Tasks

## Available Tasks

### 1. **start** - Development Server
Starts Spring Boot application in development mode using `spring-boot:run` Maven goal.

**Default Options:**
- `profiles`: "dev" - Active Spring profiles
- `port`: 8080 - Server port
- `debug`: false - Enable debug mode
- `debugPort`: 5005 - Debug port when debug is enabled

**Example project.json:**
```json
{
  "targets": {
    "start": {
      "executor": "start",
      "options": {
        "profiles": "dev,local",
        "port": 3000,
        "debug": true,
        "jvmArgs": ["-Xmx2g"]
      }
    }
  }
}
```

### 2. **build-image** - Container Image
Builds a container image using Cloud Native Buildpacks.

**Default Options:**
- `imageName`: project name - Name of the image
- `imageTag`: "latest" - Image tag
- `builder`: "paketobuildpacks/builder:base" - Buildpack builder
- `publish`: false - Whether to publish to registry

**Example project.json:**
```json
{
  "targets": {
    "build-image": {
      "executor": "build-image",
      "options": {
        "imageName": "my-app",
        "imageTag": "1.0.0",
        "publish": true,
        "registry": "docker.io/myorg"
      }
    }
  }
}
```

## How It Works

1. **Task Discovery**: When SpringBootNature detects a Spring Boot project, it creates these tasks with default options
2. **User Configuration**: Users can override any option in their project.json
3. **Runtime Execution**: Tasks merge user options with defaults and build the appropriate commands
4. **Build System Integration**: Both tasks require Maven nature and spring-boot-maven-plugin

## Customization

All tasks support standard NX executor properties:
- `inputs`: Files that affect task execution
- `outputs`: Files produced by the task
- `cache`: Whether results can be cached
- `dependsOn`: Other tasks that must run first

## Requirements

- **start**: Requires Maven nature and spring-boot-maven-plugin
- **build-image**: Requires Maven nature, spring-boot-maven-plugin, and Docker