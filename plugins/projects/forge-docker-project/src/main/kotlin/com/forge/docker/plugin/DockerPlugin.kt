package com.forge.docker.plugin

import com.forge.plugin.api.*
import com.forge.docker.devkit.*
import com.forge.docker.plugin.generators.DockerfileGenerator
import com.forge.docker.plugin.executors.DockerBuildExecutor
import com.forge.docker.plugin.executors.DockerRunExecutor
import com.forge.docker.plugin.executors.DockerPushExecutor
import com.forge.docker.plugin.initializers.DockerInitializer

/**
 * Main Docker plugin that provides comprehensive Docker support for Forge workspaces
 * Similar to Nx's Docker plugin with init, executors, and generators
 */
class DockerPlugin : ForgePlugin {
    
    override val metadata = PluginMetadata(
        id = "com.forge.plugin.docker",
        name = "Docker Plugin",
        version = "1.0.0",
        description = "Comprehensive Docker support with build, run, push executors and generators",
        author = "Forge Team",
        keywords = listOf("docker", "container", "build", "deployment", "devops"),
        tags = listOf("docker", "container", "deployment"),
        minimumForgeVersion = "1.0.0"
    )
    
    override fun getExecutors(): Map<String, ExecutorSchema> = mapOf(
        "docker-build" to ExecutorSchema(
            implementation = "com.forge.docker.plugin.executors.DockerBuildExecutor",
            schema = JsonSchema(
                type = "object",
                properties = mapOf(
                    "context" to JsonProperty(
                        type = "string",
                        description = "Build context directory",
                        default = "."
                    ),
                    "dockerfile" to JsonProperty(
                        type = "string",
                        description = "Path to Dockerfile",
                        default = "Dockerfile"
                    ),
                    "tag" to JsonProperty(
                        type = "string",
                        description = "Image tag"
                    ),
                    "buildArgs" to JsonProperty(
                        type = "object",
                        description = "Build arguments",
                        additionalProperties = mapOf("type" to "string")
                    ),
                    "target" to JsonProperty(
                        type = "string",
                        description = "Target stage for multi-stage builds"
                    ),
                    "platform" to JsonProperty(
                        type = "string",
                        description = "Target platform (e.g., linux/amd64)"
                    ),
                    "push" to JsonProperty(
                        type = "boolean",
                        description = "Push image after build",
                        default = false
                    )
                ),
                required = listOf("tag")
            ),
            description = "Build Docker images with advanced options"
        ),
        
        "docker-run" to ExecutorSchema(
            implementation = "com.forge.docker.plugin.executors.DockerRunExecutor",
            schema = JsonSchema(
                type = "object",
                properties = mapOf(
                    "image" to JsonProperty(
                        type = "string",
                        description = "Docker image to run"
                    ),
                    "ports" to JsonProperty(
                        type = "object",
                        description = "Port mappings (host:container)",
                        additionalProperties = mapOf("type" to "string")
                    ),
                    "volumes" to JsonProperty(
                        type = "object",
                        description = "Volume mappings (host:container)",
                        additionalProperties = mapOf("type" to "string")
                    ),
                    "environment" to JsonProperty(
                        type = "object",
                        description = "Environment variables",
                        additionalProperties = mapOf("type" to "string")
                    ),
                    "detached" to JsonProperty(
                        type = "boolean",
                        description = "Run container in detached mode",
                        default = false
                    ),
                    "removeOnExit" to JsonProperty(
                        type = "boolean",
                        description = "Remove container on exit",
                        default = true
                    ),
                    "command" to JsonProperty(
                        type = "string",
                        description = "Command to run in container"
                    )
                ),
                required = listOf("image")
            ),
            description = "Run Docker containers with flexible configuration"
        ),
        
        "docker-push" to ExecutorSchema(
            implementation = "com.forge.docker.plugin.executors.DockerPushExecutor",
            schema = JsonSchema(
                type = "object",
                properties = mapOf(
                    "image" to JsonProperty(
                        type = "string",
                        description = "Docker image to push"
                    ),
                    "registry" to JsonProperty(
                        type = "string",
                        description = "Docker registry URL"
                    ),
                    "repository" to JsonProperty(
                        type = "string",
                        description = "Repository name"
                    ),
                    "tag" to JsonProperty(
                        type = "string",
                        description = "Image tag",
                        default = "latest"
                    )
                ),
                required = listOf("image")
            ),
            description = "Push Docker images to registries"
        )
    )
    
    override fun getGenerators(): Map<String, GeneratorSchema> = mapOf(
        "dockerfile" to GeneratorSchema(
            implementation = "com.forge.docker.plugin.generators.DockerfileGenerator",
            schema = JsonSchema(
                type = "object",
                properties = mapOf(
                    "project" to JsonProperty(
                        type = "string",
                        description = "Target project name"
                    ),
                    "baseImage" to JsonProperty(
                        type = "string",
                        description = "Base Docker image",
                        enum = listOf("node:18-alpine", "python:3.11-slim", "openjdk:17-alpine", "nginx:alpine", "golang:1.21-alpine"),
                        default = "node:18-alpine"
                    ),
                    "port" to JsonProperty(
                        type = "integer",
                        description = "Application port",
                        default = 3000
                    ),
                    "buildStage" to JsonProperty(
                        type = "boolean",
                        description = "Use multi-stage build",
                        default = true
                    ),
                    "healthCheck" to JsonProperty(
                        type = "boolean",
                        description = "Add health check",
                        default = true
                    )
                ),
                required = listOf("project")
            ),
            description = "Generate optimized Dockerfiles for various technologies",
            examples = listOf(
                GeneratorExample(
                    name = "Node.js Application",
                    description = "Create Dockerfile for Node.js application",
                    options = mapOf(
                        "project" to "my-web-app",
                        "baseImage" to "node:18-alpine",
                        "port" to 3000,
                        "buildStage" to true
                    )
                )
            )
        )
    )
    
    override fun getInitializers(): Map<String, InitializerSchema> = mapOf(
        "docker" to InitializerSchema(
            implementation = "com.forge.docker.plugin.initializers.DockerInitializer",
            schema = JsonSchema(
                type = "object",
                properties = mapOf(
                    "addPlugin" to JsonProperty(
                        type = "boolean",
                        description = "Add Docker plugin to workspace configuration",
                        default = true
                    ),
                    "createDockerIgnore" to JsonProperty(
                        type = "boolean",
                        description = "Create .dockerignore file",
                        default = true
                    ),
                    "buildTarget" to JsonProperty(
                        type = "string",
                        description = "Name for build target",
                        default = "docker-build"
                    ),
                    "runTarget" to JsonProperty(
                        type = "string",
                        description = "Name for run target",
                        default = "docker-run"
                    ),
                    "pushTarget" to JsonProperty(
                        type = "string",
                        description = "Name for push target",
                        default = "docker-push"
                    ),
                    "skipFormat" to JsonProperty(
                        type = "boolean",
                        description = "Skip formatting files",
                        default = false
                    )
                )
            ),
            description = "Initialize Docker support in workspace",
            presets = listOf(
                InitializerPreset(
                    name = "basic",
                    description = "Basic Docker setup",
                    options = mapOf(
                        "addPlugin" to true,
                        "createDockerIgnore" to true
                    )
                ),
                InitializerPreset(
                    name = "complete",
                    description = "Complete Docker setup with custom targets",
                    options = mapOf(
                        "addPlugin" to true,
                        "createDockerIgnore" to true,
                        "buildTarget" to "build-docker",
                        "runTarget" to "run-docker",
                        "pushTarget" to "push-docker"
                    )
                )
            )
        )
    )
    
    override fun getMigrators(): Map<String, MigratorSchema> = mapOf(
        "docker-to-buildkit" to MigratorSchema(
            implementation = "com.forge.docker.plugin.migrators.DockerBuildKitMigrator",
            description = "Migrate Dockerfiles to use BuildKit features",
            version = "latest"
        )
    )
    
    /**
     * Project inference similar to Nx's createNodesV2
     */
    override fun inferProjects(
        configFiles: List<String>,
        options: Map<String, Any>,
        context: CreateNodesContext
    ): Map<String, ProjectConfiguration> {
        val dockerOptions = DockerPluginOptions(
            buildTarget = options["buildTarget"] as? String ?: "docker-build",
            runTarget = options["runTarget"] as? String ?: "docker-run",
            pushTarget = options["pushTarget"] as? String ?: "docker-push"
        )
        
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val dockerfilePath = java.nio.file.Path.of(configFile)
                val projectRoot = context.workspaceRoot.relativize(dockerfilePath.parent).toString()
                
                if (dockerfilePath.exists()) {
                    val dockerTargets = DockerProjectCreator.createDockerTargets(
                        projectRoot, 
                        dockerOptions, 
                        context
                    )
                    
                    val projectName = dockerfilePath.parent.fileName?.toString() ?: continue
                    val dockerfileInfo = DockerUtils.parseDockerfile(dockerfilePath)
                    val technologies = DockerUtils.detectTechnology(dockerfileInfo)
                    
                    projects[projectRoot] = ProjectConfiguration(
                        name = projectName,
                        root = projectRoot,
                        sourceRoot = projectRoot,
                        projectType = "application", // Docker projects are typically applications
                        tags = listOf("docker", "container") + technologies,
                        targets = dockerTargets.targets,
                        metadata = dockerTargets.metadata
                    )
                }
            } catch (e: Exception) {
                // Log error but continue processing other files
                println("Failed to process Docker file $configFile: ${e.message}")
            }
        }
        
        return projects
    }
    
    override fun getConfigFiles(): List<String> = listOf(
        "**/Dockerfile",
        "**/dockerfile",
        "**/Dockerfile.*"
    )
}