package com.forge.docker.devkit

import com.forge.plugin.api.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Options for creating Docker projects and targets
 */
data class DockerPluginOptions(
    val buildTarget: String = "docker-build",
    val runTarget: String = "docker-run",
    val pushTarget: String = "docker-push"
)

/**
 * Creates Docker project configurations and targets similar to Nx's createDockerTargets
 */
object DockerProjectCreator {
    
    private val logger = LoggerFactory.getLogger(DockerProjectCreator::class.java)
    
    /**
     * Create Docker targets for a project, similar to Nx's createDockerTargets function
     */
    fun createDockerTargets(
        projectRoot: String,
        options: DockerPluginOptions,
        context: CreateNodesContext
    ): DockerTargets {
        val imageRef = DockerUtils.generateImageRef(projectRoot)
        val dockerfilePath = Path.of(context.workspaceRoot.toString(), projectRoot, "Dockerfile")
        
        val targets = mutableMapOf<String, TargetConfiguration>()
        val namedInputs = getNamedInputs(projectRoot, context)
        
        // Parse Dockerfile for additional context
        val dockerfileInfo = if (dockerfilePath.exists()) {
            DockerUtils.parseDockerfile(dockerfilePath)
        } else {
            DockerfileInfo()
        }
        
        val technologies = DockerUtils.detectTechnology(dockerfileInfo)
        
        // Build target
        targets[options.buildTarget] = createBuildTarget(imageRef, projectRoot, namedInputs, technologies)
        
        // Run target
        targets[options.runTarget] = createRunTarget(imageRef, projectRoot, options.buildTarget, namedInputs, dockerfileInfo)
        
        // Push target (similar to Nx's nx-release-publish)
        targets[options.pushTarget] = createPushTarget(imageRef, projectRoot, options.buildTarget)
        
        val metadata = ProjectMetadata(
            targetGroups = mapOf(
                "Docker" to listOf(
                    options.buildTarget,
                    options.runTarget,
                    options.pushTarget
                )
            ),
            technologies = listOf("docker") + technologies
        )
        
        return DockerTargets(targets, metadata)
    }
    
    private fun createBuildTarget(
        imageRef: String,
        projectRoot: String,
        namedInputs: Map<String, List<String>>,
        technologies: List<String>
    ): TargetConfiguration {
        val buildCommand = DockerUtils.generateBuildCommand(imageRef)
        
        val inputs = if ("production" in namedInputs) {
            listOf("production", "^production")
        } else {
            listOf("default", "^default")
        } + listOf(
            "{projectRoot}/Dockerfile",
            "{projectRoot}/**/*",
            "!{projectRoot}/node_modules/**/*",
            "!{projectRoot}/target/**/*",
            "!{projectRoot}/build/**/*",
            "!{projectRoot}/.git/**/*"
        )
        
        return TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "command" to buildCommand.joinToString(" "),
                "cwd" to projectRoot
            ),
            inputs = inputs,
            outputs = listOf(),
            cache = false // Docker builds use their own layer caching
        )
    }
    
    private fun createRunTarget(
        imageRef: String,
        projectRoot: String,
        buildTarget: String,
        namedInputs: Map<String, List<String>>,
        dockerfileInfo: DockerfileInfo
    ): TargetConfiguration {
        val technologies = DockerUtils.detectTechnology(dockerfileInfo)
        val defaultPorts = DockerUtils.getDefaultPorts(technologies)
        
        // Generate run command with intelligent defaults
        val runCommand = DockerUtils.generateRunCommand(
            imageRef = imageRef,
            ports = defaultPorts,
            removeOnExit = true
        )
        
        val inputs = if ("production" in namedInputs) {
            listOf("production", "^production")
        } else {
            listOf("default", "^default")
        }
        
        return TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "command" to runCommand.joinToString(" "),
                "cwd" to projectRoot
            ),
            inputs = inputs,
            outputs = listOf(),
            cache = false,
            dependsOn = listOf(buildTarget)
        )
    }
    
    private fun createPushTarget(
        imageRef: String,
        projectRoot: String,
        buildTarget: String
    ): TargetConfiguration {
        return TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "command" to "docker push $imageRef",
                "cwd" to projectRoot
            ),
            inputs = listOf(),
            outputs = listOf(),
            cache = false,
            dependsOn = listOf(buildTarget)
        )
    }
    
    /**
     * Get named inputs for the project context
     * This is a simplified version of Nx's getNamedInputs
     */
    private fun getNamedInputs(projectRoot: String, context: CreateNodesContext): Map<String, List<String>> {
        // This would typically read from nx.json or similar configuration
        // For now, provide reasonable defaults
        return mapOf(
            "default" to listOf(
                "{projectRoot}/**/*",
                "!{projectRoot}/node_modules/**/*",
                "!{projectRoot}/target/**/*",
                "!{projectRoot}/build/**/*"
            ),
            "production" to listOf(
                "{projectRoot}/src/**/*",
                "{projectRoot}/Dockerfile",
                "{projectRoot}/package.json",
                "{projectRoot}/pom.xml",
                "{projectRoot}/build.gradle*"
            )
        )
    }
}

/**
 * Result of creating Docker targets, similar to Nx's DockerTargets type
 */
data class DockerTargets(
    val targets: Map<String, TargetConfiguration>,
    val metadata: ProjectMetadata
)

/**
 * Docker-specific project metadata
 */
data class ProjectMetadata(
    val targetGroups: Map<String, List<String>> = emptyMap(),
    val technologies: List<String> = emptyList()
)

/**
 * Metadata for individual targets
 */
data class TargetMetadata(
    val technologies: List<String> = emptyList(),
    val description: String = "",
    val help: TargetHelp? = null
)

/**
 * Help information for targets
 */
data class TargetHelp(
    val command: String = "",
    val example: Map<String, Any> = emptyMap()
)