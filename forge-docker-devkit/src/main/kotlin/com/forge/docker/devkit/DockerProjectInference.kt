package com.forge.docker.devkit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.plugin.api.ProjectConfiguration
import com.forge.plugin.api.TargetConfiguration
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.api.DependencyType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Options for Docker project inference
 */
data class DockerInferenceOptions(
    val buildTargetName: String = "docker-build",
    val pushTargetName: String = "docker-push",
    val runTargetName: String = "docker-run"
)

/**
 * Core Docker project inference logic
 */
class DockerProjectInference {
    
    private val logger = LoggerFactory.getLogger(DockerProjectInference::class.java)
    private val objectMapper = ObjectMapper()
    
    fun createNodes(
        configFiles: List<String>,
        options: DockerInferenceOptions,
        context: CreateNodesContext
    ): Map<String, ProjectConfiguration> {
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val dockerfilePath = Path.of(configFile)
                if (dockerfilePath.exists()) {
                    val project = inferProjectFromDockerfile(dockerfilePath, options, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process Dockerfile at $configFile: ${e.message}")
            }
        }
        
        return projects
    }
    
    fun createDependencies(
        options: DockerInferenceOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Docker projects typically don't have internal dependencies within the workspace
        // They depend on base images and external resources, not other workspace projects
        // However, we could analyze docker-compose.yml files for service dependencies
        
        context.projects.values.forEach { project ->
            val projectDir = context.workspaceRoot.resolve(project.root)
            
            // Check for docker-compose files that might indicate service dependencies
            listOf("docker-compose.yml", "docker-compose.yaml").forEach { composeFile ->
                val composePath = projectDir.resolve(composeFile)
                if (composePath.exists()) {
                    try {
                        val composeDependencies = parseDockerComposeDependencies(
                            composePath, 
                            project.name, 
                            context.projects
                        )
                        dependencies.addAll(composeDependencies)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse dependencies from $composePath: ${e.message}")
                    }
                }
            }
        }
        
        return dependencies
    }
    
    private fun inferProjectFromDockerfile(
        dockerfilePath: Path,
        options: DockerInferenceOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val dockerfileContent = dockerfilePath.readText()
        val projectRoot = context.workspaceRoot.relativize(dockerfilePath.parent).toString()
        
        // Extract project name from directory name
        val projectName = dockerfilePath.parent.fileName?.toString() ?: return null
        
        val projectType = inferProjectType(dockerfilePath.parent, dockerfileContent)
        val tags = extractTags(dockerfilePath.parent, dockerfileContent)
        val targets = inferTargets(options, projectRoot, projectName)
        
        return ProjectConfiguration(
            name = "${projectName}-docker",
            root = projectRoot,
            sourceRoot = projectRoot,
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun inferProjectType(projectDir: Path, dockerfileContent: String): String {
        // Most Docker projects are applications since they create deployable containers
        return "application"
    }
    
    private fun extractTags(projectDir: Path, dockerfileContent: String): List<String> {
        val tags = mutableListOf<String>()
        
        tags.add("docker")
        tags.add("container")
        
        // Analyze Dockerfile content for technology tags
        val content = dockerfileContent.lowercase()
        
        when {
            content.contains("from node") || content.contains("npm") || content.contains("yarn") -> {
                tags.add("nodejs")
                tags.add("javascript")
            }
            content.contains("from python") || content.contains("pip") || content.contains("requirements.txt") -> {
                tags.add("python")
            }
            content.contains("from java") || content.contains("from openjdk") || content.contains("maven") || content.contains("gradle") -> {
                tags.add("java")
            }
            content.contains("from golang") || content.contains("from go") || content.contains("go build") -> {
                tags.add("go")
                tags.add("golang")
            }
            content.contains("from nginx") -> {
                tags.add("nginx")
                tags.add("web")
            }
            content.contains("from redis") -> {
                tags.add("redis")
                tags.add("database")
            }
            content.contains("from postgres") || content.contains("from mysql") -> {
                tags.add("database")
            }
        }
        
        // Check for docker-compose
        if (projectDir.resolve("docker-compose.yml").exists() || 
            projectDir.resolve("docker-compose.yaml").exists()) {
            tags.add("docker-compose")
            tags.add("multi-service")
        }
        
        // Check for health checks or multi-stage builds
        if (content.contains("healthcheck") || content.contains("as builder")) {
            tags.add("production-ready")
        }
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        options: DockerInferenceOptions,
        projectRoot: String,
        imageName: String
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Docker build target
        targets[options.buildTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("docker build -t $imageName ."),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/Dockerfile",
                "{projectRoot}/**/*",
                "!{projectRoot}/node_modules",
                "!{projectRoot}/target",
                "!{projectRoot}/build"
            ),
            outputs = listOf(),
            cache = false // Docker builds shouldn't be cached by Forge
        )
        
        // Docker push target
        targets[options.pushTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("docker push $imageName"),
                "cwd" to projectRoot
            ),
            inputs = listOf(),
            outputs = listOf(),
            cache = false,
            dependsOn = listOf(options.buildTargetName)
        )
        
        // Docker run target
        targets[options.runTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("docker run --rm -p 8080:8080 $imageName"),
                "cwd" to projectRoot
            ),
            inputs = listOf(),
            outputs = listOf(),
            cache = false,
            dependsOn = listOf(options.buildTargetName)
        )
        
        return targets
    }
    
    private fun parseDockerComposeDependencies(
        composePath: Path,
        sourceProjectName: String,
        projects: Map<String, ProjectConfiguration>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val composeContent = composePath.readText()
            
            // Simple parsing for service dependencies in docker-compose
            // This is a basic implementation - could be enhanced with proper YAML parsing
            val servicePattern = Regex("""depends_on:\s*\n((?:\s*-\s*.+\n)*)""")
            val dependencyPattern = Regex("""\s*-\s*(.+)""")
            
            servicePattern.findAll(composeContent).forEach { match ->
                val dependsOnBlock = match.groupValues[1]
                dependencyPattern.findAll(dependsOnBlock).forEach { depMatch ->
                    val serviceName = depMatch.groupValues[1].trim()
                    
                    // Check if this service corresponds to another project in our workspace
                    val targetProject = projects.keys.find { projectName ->
                        projectName.contains(serviceName) || serviceName.contains(projectName.removeSuffix("-docker"))
                    }
                    
                    if (targetProject != null && targetProject != sourceProjectName) {
                        dependencies.add(
                            RawProjectGraphDependency(
                                source = sourceProjectName,
                                target = targetProject,
                                type = DependencyType.STATIC,
                                sourceFile = composePath.toString()
                            )
                        )
                        logger.debug("Found docker-compose dependency: $sourceProjectName -> $targetProject")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing docker-compose dependencies from $composePath: ${e.message}")
        }
        
        return dependencies
    }
}