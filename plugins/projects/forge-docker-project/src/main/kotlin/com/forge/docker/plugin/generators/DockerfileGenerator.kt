package com.forge.docker.plugin.generators

import com.forge.plugin.api.*
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Generator for creating optimized Dockerfiles for various technologies
 */
class DockerfileGenerator : Generator {
    
    override suspend fun generate(
        workspace: WorkspaceContext,
        options: Map<String, Any>
    ): GeneratorResult {
        
        val parsedOptions = parseOptions(options)
        
        workspace.logger.info("🐳 Generating Dockerfile for project: ${parsedOptions.project}")
        
        try {
            val projectPath = workspace.root.resolve(parsedOptions.project)
            if (!projectPath.exists()) {
                return GeneratorResult(
                    success = false,
                    messages = listOf("Project directory does not exist: ${parsedOptions.project}")
                )
            }
            
            // Detect technology stack if not explicitly provided
            val technology = detectTechnology(projectPath, parsedOptions.baseImage)
            
            // Generate Dockerfile content
            val dockerfileContent = generateDockerfileContent(
                parsedOptions,
                technology,
                projectPath
            )
            
            // Write Dockerfile
            val dockerfilePath = projectPath.resolve("Dockerfile")
            val files = mutableListOf<GeneratedFile>()
            
            files.add(GeneratedFile(
                path = dockerfilePath.toString(),
                action = if (dockerfilePath.exists()) FileAction.MODIFY else FileAction.CREATE,
                content = dockerfileContent
            ))
            
            // Generate .dockerignore if requested
            if (parsedOptions.createDockerIgnore) {
                val dockerIgnoreContent = generateDockerIgnoreContent(technology)
                val dockerIgnorePath = projectPath.resolve(".dockerignore")
                
                files.add(GeneratedFile(
                    path = dockerIgnorePath.toString(),
                    action = if (dockerIgnorePath.exists()) FileAction.MODIFY else FileAction.CREATE,
                    content = dockerIgnoreContent
                ))
            }
            
            workspace.fileTree.writeFiles(files)
            
            val messages = listOf(
                "Generated Dockerfile for $technology application",
                "Base image: ${parsedOptions.baseImage}",
                "Port: ${parsedOptions.port}",
                if (parsedOptions.buildStage) "Multi-stage build enabled" else null,
                if (parsedOptions.healthCheck) "Health check included" else null
            ).filterNotNull()
            
            val nextSteps = listOf(
                "Build the image: docker build -t ${parsedOptions.project} .",
                "Run the container: docker run -p ${parsedOptions.port}:${parsedOptions.port} ${parsedOptions.project}",
                "Add any additional dependencies to the Dockerfile as needed"
            )
            
            return GeneratorResult(
                success = true,
                files = files,
                messages = messages,
                nextSteps = nextSteps
            )
            
        } catch (e: Exception) {
            return GeneratorResult(
                success = false,
                messages = listOf("Failed to generate Dockerfile: ${e.message}")
            )
        }
    }
    
    private fun detectTechnology(projectPath: Path, baseImage: String): String {
        return when {
            baseImage.contains("node") -> "nodejs"
            baseImage.contains("python") -> "python"
            baseImage.contains("java") || baseImage.contains("openjdk") -> "java"
            baseImage.contains("golang") || baseImage.contains("go") -> "go"
            baseImage.contains("nginx") -> "nginx"
            projectPath.resolve("package.json").exists() -> "nodejs"
            projectPath.resolve("requirements.txt").exists() || projectPath.resolve("pyproject.toml").exists() -> "python"
            projectPath.resolve("pom.xml").exists() || projectPath.resolve("build.gradle").exists() -> "java"
            projectPath.resolve("go.mod").exists() -> "go"
            projectPath.resolve("Cargo.toml").exists() -> "rust"
            else -> "generic"
        }
    }
    
    private fun generateDockerfileContent(
        options: DockerfileOptions,
        technology: String,
        projectPath: Path
    ): String {
        return when (technology) {
            "nodejs" -> generateNodeDockerfile(options, projectPath)
            "python" -> generatePythonDockerfile(options, projectPath)
            "java" -> generateJavaDockerfile(options, projectPath)
            "go" -> generateGoDockerfile(options, projectPath)
            else -> generateGenericDockerfile(options)
        }
    }
    
    private fun generateNodeDockerfile(options: DockerfileOptions, projectPath: Path): String {
        val hasYarn = projectPath.resolve("yarn.lock").exists()
        val packageManager = if (hasYarn) "yarn" else "npm"
        
        return if (options.buildStage) {
            """
            # Multi-stage build for Node.js application
            FROM ${options.baseImage} AS builder
            
            WORKDIR /app
            
            # Copy package files
            COPY package*.json ./
            ${if (hasYarn) "COPY yarn.lock ./" else ""}
            
            # Install dependencies
            RUN $packageManager ci --only=production && $packageManager cache clean --force
            
            # Copy source code
            COPY . .
            
            # Build application
            RUN $packageManager run build 2>/dev/null || echo "No build script found"
            
            # Production stage
            FROM ${options.baseImage} AS production
            
            WORKDIR /app
            
            # Copy built application
            COPY --from=builder /app/dist ./dist 2>/dev/null || COPY --from=builder /app/build ./build 2>/dev/null || echo "No build output found"
            COPY --from=builder /app/node_modules ./node_modules
            COPY --from=builder /app/package*.json ./
            
            # Create non-root user
            RUN addgroup -g 1001 -S nodejs && adduser -S nextjs -u 1001
            
            USER nextjs
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["$packageManager", "start"]
            """.trimIndent()
        } else {
            """
            FROM ${options.baseImage}
            
            WORKDIR /app
            
            # Copy package files
            COPY package*.json ./
            ${if (hasYarn) "COPY yarn.lock ./" else ""}
            
            # Install dependencies
            RUN $packageManager ci --only=production
            
            # Copy source code
            COPY . .
            
            # Build if build script exists
            RUN $packageManager run build 2>/dev/null || echo "No build script found"
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["$packageManager", "start"]
            """.trimIndent()
        }
    }
    
    private fun generatePythonDockerfile(options: DockerfileOptions, projectPath: Path): String {
        val hasRequirementsTxt = projectPath.resolve("requirements.txt").exists()
        val hasPipfile = projectPath.resolve("Pipfile").exists()
        val hasPoetry = projectPath.resolve("pyproject.toml").exists()
        
        return if (options.buildStage) {
            """
            # Multi-stage build for Python application
            FROM ${options.baseImage} AS builder
            
            WORKDIR /app
            
            # Install build dependencies
            RUN pip install --upgrade pip
            
            # Copy dependency files
            ${when {
                hasPoetry -> """
                COPY pyproject.toml poetry.lock ./
                RUN pip install poetry && poetry config virtualenvs.create false && poetry install --no-dev
                """.trimIndent()
                hasPipfile -> """
                COPY Pipfile Pipfile.lock ./
                RUN pip install pipenv && pipenv install --system --deploy
                """.trimIndent()
                hasRequirementsTxt -> """
                COPY requirements.txt ./
                RUN pip install -r requirements.txt
                """.trimIndent()
                else -> "# No dependency file found"
            }}
            
            # Copy source code
            COPY . .
            
            # Production stage
            FROM ${options.baseImage} AS production
            
            WORKDIR /app
            
            # Copy installed packages and application
            COPY --from=builder /usr/local/lib/python*/site-packages /usr/local/lib/python*/site-packages
            COPY --from=builder /app .
            
            # Create non-root user
            RUN groupadd -r appuser && useradd -r -g appuser appuser
            RUN chown -R appuser:appuser /app
            USER appuser
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["python", "app.py"]
            """.trimIndent()
        } else {
            """
            FROM ${options.baseImage}
            
            WORKDIR /app
            
            # Install dependencies
            RUN pip install --upgrade pip
            
            ${when {
                hasPoetry -> """
                COPY pyproject.toml poetry.lock ./
                RUN pip install poetry && poetry config virtualenvs.create false && poetry install --no-dev
                """.trimIndent()
                hasPipfile -> """
                COPY Pipfile Pipfile.lock ./
                RUN pip install pipenv && pipenv install --system --deploy
                """.trimIndent()
                hasRequirementsTxt -> """
                COPY requirements.txt ./
                RUN pip install -r requirements.txt
                """.trimIndent()
                else -> "# No dependency file found"
            }}
            
            # Copy source code
            COPY . .
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["python", "app.py"]
            """.trimIndent()
        }
    }
    
    private fun generateJavaDockerfile(options: DockerfileOptions, projectPath: Path): String {
        val hasMaven = projectPath.resolve("pom.xml").exists()
        val hasGradle = projectPath.resolve("build.gradle").exists() || projectPath.resolve("build.gradle.kts").exists()
        
        return if (options.buildStage) {
            """
            # Multi-stage build for Java application
            FROM maven:3.8-openjdk-17 AS builder
            
            WORKDIR /app
            
            ${if (hasMaven) """
            # Copy Maven files
            COPY pom.xml ./
            COPY src ./src
            
            # Build application
            RUN mvn clean package -DskipTests
            """.trimIndent() else if (hasGradle) """
            # Copy Gradle files
            COPY build.gradle* gradlew ./
            COPY gradle gradle
            COPY src ./src
            
            # Build application
            RUN ./gradlew build -x test
            """.trimIndent() else """
            # Copy source code
            COPY . .
            """.trimIndent()}
            
            # Production stage
            FROM ${options.baseImage} AS production
            
            WORKDIR /app
            
            ${if (hasMaven) """
            # Copy built JAR
            COPY --from=builder /app/target/*.jar app.jar
            """.trimIndent() else if (hasGradle) """
            # Copy built JAR
            COPY --from=builder /app/build/libs/*.jar app.jar
            """.trimIndent() else """
            # Copy application
            COPY --from=builder /app .
            """.trimIndent()}
            
            # Create non-root user
            RUN groupadd -r appuser && useradd -r -g appuser appuser
            USER appuser
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/actuator/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["java", "-jar", "app.jar"]
            """.trimIndent()
        } else {
            """
            FROM ${options.baseImage}
            
            WORKDIR /app
            
            # Copy JAR file (assumes it's already built)
            COPY target/*.jar app.jar 2>/dev/null || COPY build/libs/*.jar app.jar 2>/dev/null || COPY *.jar app.jar
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/actuator/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["java", "-jar", "app.jar"]
            """.trimIndent()
        }
    }
    
    private fun generateGoDockerfile(options: DockerfileOptions, projectPath: Path): String {
        return if (options.buildStage) {
            """
            # Multi-stage build for Go application
            FROM golang:1.21-alpine AS builder
            
            WORKDIR /app
            
            # Copy go mod files
            COPY go.mod go.sum ./
            RUN go mod download
            
            # Copy source code
            COPY . .
            
            # Build application
            RUN CGO_ENABLED=0 GOOS=linux go build -o main .
            
            # Production stage
            FROM ${options.baseImage} AS production
            
            WORKDIR /app
            
            # Copy built binary
            COPY --from=builder /app/main .
            
            # Create non-root user
            RUN addgroup -g 1001 -S appuser && adduser -S appuser -u 1001 -G appuser
            USER appuser
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["./main"]
            """.trimIndent()
        } else {
            """
            FROM ${options.baseImage}
            
            WORKDIR /app
            
            # Copy pre-built binary (assumes it's already built)
            COPY main .
            
            EXPOSE ${options.port}
            
            ${if (options.healthCheck) """
            # Health check
            HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
                CMD curl -f http://localhost:${options.port}/health || exit 1
            """.trimIndent() else ""}
            
            CMD ["./main"]
            """.trimIndent()
        }
    }
    
    private fun generateGenericDockerfile(options: DockerfileOptions): String {
        return """
        FROM ${options.baseImage}
        
        WORKDIR /app
        
        # Copy application files
        COPY . .
        
        EXPOSE ${options.port}
        
        ${if (options.healthCheck) """
        # Health check
        HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
            CMD curl -f http://localhost:${options.port}/health || exit 1
        """.trimIndent() else ""}
        
        CMD ["echo", "Please configure your application startup command"]
        """.trimIndent()
    }
    
    private fun generateDockerIgnoreContent(technology: String): String {
        val commonIgnores = """
        # Git
        .git
        .gitignore
        README.md
        
        # IDE
        .vscode
        .idea
        *.swp
        *.swo
        *~
        
        # OS
        .DS_Store
        Thumbs.db
        
        # Logs
        logs
        *.log
        
        # Docker
        Dockerfile*
        docker-compose*
        .dockerignore
        """.trimIndent()
        
        val techSpecific = when (technology) {
            "nodejs" -> """
            
            # Node.js
            node_modules
            npm-debug.log*
            yarn-debug.log*
            yarn-error.log*
            .npm
            .yarn-integrity
            .env.local
            .env.development.local
            .env.test.local
            .env.production.local
            coverage
            .nyc_output
            """.trimIndent()
            
            "python" -> """
            
            # Python
            __pycache__
            *.py[cod]
            *$py.class
            *.so
            .Python
            env
            venv
            ENV
            env.bak
            venv.bak
            .pytest_cache
            .coverage
            htmlcov
            """.trimIndent()
            
            "java" -> """
            
            # Java
            target
            build
            *.class
            *.jar
            *.war
            *.ear
            *.logs
            .gradle
            .settings
            .project
            .classpath
            """.trimIndent()
            
            "go" -> """
            
            # Go
            *.exe
            *.exe~
            *.dll
            *.so
            *.dylib
            *.test
            *.out
            vendor
            """.trimIndent()
            
            else -> ""
        }
        
        return commonIgnores + techSpecific
    }
    
    private fun parseOptions(options: Map<String, Any>): DockerfileOptions {
        return DockerfileOptions(
            project = options["project"] as? String ?: throw IllegalArgumentException("project is required"),
            baseImage = options["baseImage"] as? String ?: "node:18-alpine",
            port = options["port"] as? Int ?: 3000,
            buildStage = options["buildStage"] as? Boolean ?: true,
            healthCheck = options["healthCheck"] as? Boolean ?: true,
            createDockerIgnore = options["createDockerIgnore"] as? Boolean ?: true
        )
    }
}

/**
 * Options for Dockerfile generation
 */
private data class DockerfileOptions(
    val project: String,
    val baseImage: String,
    val port: Int,
    val buildStage: Boolean,
    val healthCheck: Boolean,
    val createDockerIgnore: Boolean
)