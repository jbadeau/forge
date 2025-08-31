package com.forge.inference.plugins

import com.forge.core.ProjectConfiguration
import com.forge.core.TargetConfiguration
import com.forge.inference.CreateNodesContext
import com.forge.inference.CreateNodesResult
import com.forge.inference.InferencePlugin
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

data class DockerPluginOptions(
    val buildTargetName: String = "docker-build",
    val runTargetName: String = "docker-run",
    val pushTargetName: String = "docker-push"
)

/**
 * Plugin that infers Docker-related targets from Dockerfile presence
 * Creates container build, run, and push targets
 */
class DockerPlugin : InferencePlugin<DockerPluginOptions> {
    
    override val name: String = "@forge/docker"
    override val createNodesPattern: String = "**/Dockerfile"
    
    private val logger = LoggerFactory.getLogger(DockerPlugin::class.java)
    
    override val defaultOptions = DockerPluginOptions()
    
    override val createNodes = { configFiles: List<String>, options: DockerPluginOptions?, context: CreateNodesContext ->
        val opts = options ?: defaultOptions
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val dockerfilePath = Path.of(configFile)
                if (dockerfilePath.exists()) {
                    val project = inferProjectFromDockerfile(dockerfilePath, opts, context)
                    if (project != null) {
                        // Only add if we don't already have a project in this directory
                        if (!projects.containsKey(project.name)) {
                            projects[project.name] = project
                            logger.debug("Inferred Docker project '${project.name}' from $configFile")
                        } else {
                            // Merge Docker targets into existing project
                            val existing = projects[project.name]!!
                            val mergedTargets = existing.targets + project.targets
                            val mergedTags = (existing.tags + project.tags).distinct()
                            projects[project.name] = existing.copy(
                                targets = mergedTargets,
                                tags = mergedTags
                            )
                            logger.debug("Added Docker targets to existing project '${project.name}'")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process Dockerfile at $configFile: ${e.message}")
            }
        }
        
        CreateNodesResult(projects = projects)
    }
    
    private fun inferProjectFromDockerfile(
        dockerfilePath: Path,
        options: DockerPluginOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val projectRoot = context.workspaceRoot.relativize(dockerfilePath.parent).toString()
        val dockerfileContent = dockerfilePath.readText()
        
        // Infer project name from directory or explicit name
        val projectName = dockerfilePath.parent.name
        
        val tags = extractTags(dockerfileContent)
        val targets = inferTargets(dockerfileContent, options, projectRoot, projectName)
        
        return ProjectConfiguration(
            name = projectName,
            root = projectRoot,
            sourceRoot = "$projectRoot/src",
            projectType = "application", // Docker implies deployable application
            tags = tags,
            targets = targets
        )
    }
    
    private fun extractTags(dockerfileContent: String): List<String> {
        val tags = mutableListOf("docker", "containerized")
        val contentLower = dockerfileContent.lowercase()
        
        // Detect base images and add relevant tags
        when {
            contentLower.contains("from node:") -> tags.add("node")
            contentLower.contains("from openjdk:") || contentLower.contains("from eclipse-temurin:") -> tags.add("java")
            contentLower.contains("from python:") -> tags.add("python")
            contentLower.contains("from nginx:") -> tags.add("nginx")
            contentLower.contains("from alpine:") -> tags.add("alpine")
            contentLower.contains("from ubuntu:") -> tags.add("ubuntu")
        }
        
        // Detect common patterns
        when {
            contentLower.contains("spring") -> tags.add("spring")
            contentLower.contains("npm install") -> tags.add("npm")
            contentLower.contains("mvn") -> tags.add("maven")
            contentLower.contains("gradle") -> tags.add("gradle")
        }
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        dockerfileContent: String,
        options: DockerPluginOptions,
        projectRoot: String,
        projectName: String
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Docker build target
        targets[options.buildTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("docker build -t $projectName:latest ."),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "{projectRoot}/Dockerfile",
                "{projectRoot}/.dockerignore"
            ),
            outputs = listOf(), // Docker images aren't file outputs
            cache = false // Docker has its own layer caching
        )
        
        // Docker run target 
        targets[options.runTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("docker run --rm $projectName:latest"),
                "cwd" to projectRoot
            ),
            dependsOn = listOf(options.buildTargetName),
            cache = false
        )
        
        // Docker push target (if registry is configured)
        targets[options.pushTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("docker push $projectName:latest"),
                "cwd" to projectRoot
            ),
            dependsOn = listOf(options.buildTargetName),
            cache = false
        )
        
        return targets
    }
}