# Maven Tasks

## How Tasks Work

Tasks in Frontseat follow the NX executor pattern. Each task is a function that:
1. Accepts a project path and user options
2. Returns a `CommandTask` with inputs, outputs, and options
3. Can be configured via project.json

## User Configuration

Users can override task options in their `project.json`:

```json
{
  "name": "my-app",
  "targets": {
    "build": {
      "executor": "compile",  // Task name
      "options": {
        "phase": "compile",
        "skipTests": true,
        "args": ["-T", "4"]  // Use 4 threads
      },
      "inputs": ["pom.xml", "src/**"],
      "outputs": ["target/classes/**"],
      "cache": true
    }
  }
}
```

## Task Implementation

Each task:
1. Defines default options
2. Merges user options with defaults
3. Builds the command based on final options
4. Returns a CommandTask with the configuration

Example:
```kotlin
fun createMavenCompileTask(
    projectPath: Path,
    userOptions: Map<String, Any> = emptyMap()
): CommandTask {
    // Default options
    val defaultOptions = mapOf(
        "phase" to "compile",
        "skipTests" to false
    )
    
    // Merge user options
    val finalOptions = defaultOptions + userOptions
    
    // Build command based on options
    // ...
}
```

## How User Options Flow

1. **project.json** defines targets with options
2. **ProjectGraphBuilder** reads project.json
3. **TaskGraphBuilder** creates tasks, passing TargetConfiguration options
4. **Nature** calls task factory functions with options
5. **Task functions** merge defaults with user options

This allows users to customize task behavior without modifying code!