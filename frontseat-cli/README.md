# Frontseat CLI

Command line interface for the Frontseat build tool - an Nx-inspired build system for Kotlin.

## Features

- **Project Discovery**: Automatically discover projects in your workspace
- **Task Execution**: Run tasks on individual projects or multiple projects
- **Dependency Graph**: Visualize project dependencies and task relationships
- **Multiple Project Types**: Support for JavaScript, Maven, Go, and Docker projects
- **JSON Output**: Machine-readable output for integration with other tools

## Building

### Standard JAR

```bash
mvn clean package
java -jar target/frontseat.jar --help
```

### Native Executable (Requires GraalVM)

```bash
./build-native.sh
```

The native executable provides faster startup times and lower memory usage.

#### GraalVM Setup

To build native executables, you need GraalVM:

1. **Install GraalVM:**
   - Download from [GraalVM releases](https://github.com/graalvm/graalvm-ce-builds/releases)
   - Or use SDKMAN: `sdk install java 21.0.1-graalce`
   - Or use mise: `mise install java graalvm-21`

2. **Install native-image component:**
   ```bash
   gu install native-image
   ```

3. **Set JAVA_HOME to GraalVM installation**

## Usage

### Show Projects
```bash
forge show projects [--json]
```

### Run Task on Single Project
```bash
forge run <project-name> <target> [--dry-run]
```

### Run Task on Multiple Projects
```bash
forge run-many --target=<target> [--tags=<tags>] [--dry-run]
```

### Show Dependency Graph
```bash
forge graph [--json]
```

## Commands

- `show projects` - List all discovered projects
- `run <project> <target>` - Execute a target on a specific project  
- `run-many --target=<target>` - Execute a target on multiple projects
- `graph` - Display the project dependency graph

All commands support `--json` flag for machine-readable output and `--dry-run` for preview mode.

## Project Structure

The CLI automatically discovers projects by scanning for:
- `package.json` files (JavaScript/TypeScript projects)
- `pom.xml` files (Maven projects)  
- `go.mod` files (Go projects)
- `Dockerfile` files (Docker projects)

## Development

Built with:
- **Kotlin** - Primary language
- **Clikt** - Command line interface framework
- **Maven** - Build system
- **GraalVM Native Image** - Native compilation support