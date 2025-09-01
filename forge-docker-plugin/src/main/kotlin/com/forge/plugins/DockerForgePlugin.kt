package com.forge.plugins

import com.forge.docker.devkit.DockerProjectInference
import com.forge.docker.devkit.DockerInferenceOptions
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateNodesResult
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.ForgePlugin
import com.forge.plugin.PluginMetadata
import com.forge.plugin.ValidationResult

/**
 * Options for Docker plugin
 */
data class DockerPluginOptions(
    val buildTargetName: String = "docker-build",
    val pushTargetName: String = "docker-push",
    val runTargetName: String = "docker-run"
)

/**
 * ForgePlugin implementation for Docker projects
 */
class DockerForgePlugin : ForgePlugin {
    
    private val inference = DockerProjectInference()
    
    override val metadata = PluginMetadata(
        id = "com.forge.docker",
        name = "Docker Plugin",
        version = "1.0.0",
        description = "Support for Docker containerization",
        createNodesPattern = "**/Dockerfile",
        supportedFiles = listOf("Dockerfile", "docker-compose.yml", "docker-compose.yaml"),
        author = "Forge Team",
        homepage = "https://github.com/forge/plugin-docker",
        tags = listOf("docker", "container", "deployment")
    )
    
    override val defaultOptions = DockerPluginOptions()
    
    override fun createNodes(
        configFiles: List<String>, 
        options: Any?, 
        context: CreateNodesContext
    ): CreateNodesResult {
        val opts = parseOptions(options)
        val inferenceOptions = DockerInferenceOptions(
            buildTargetName = opts.buildTargetName,
            pushTargetName = opts.pushTargetName,
            runTargetName = opts.runTargetName
        )
        
        val projects = inference.createNodes(configFiles, inferenceOptions, context)
        return CreateNodesResult(projects = projects)
    }
    
    override fun createDependencies(
        options: Any?, 
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val opts = parseOptions(options)
        val inferenceOptions = DockerInferenceOptions(
            buildTargetName = opts.buildTargetName,
            pushTargetName = opts.pushTargetName,
            runTargetName = opts.runTargetName
        )
        
        return inference.createDependencies(inferenceOptions, context)
    }
    
    override fun validateOptions(options: Any?): ValidationResult {
        return try {
            parseOptions(options)
            ValidationResult.valid()
        } catch (e: Exception) {
            ValidationResult.invalid("Invalid options: ${e.message}")
        }
    }
    
    private fun parseOptions(options: Any?): DockerPluginOptions {
        return when (options) {
            null -> defaultOptions
            is DockerPluginOptions -> options
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = options as Map<String, Any>
                DockerPluginOptions(
                    buildTargetName = map["buildTargetName"] as? String ?: defaultOptions.buildTargetName,
                    pushTargetName = map["pushTargetName"] as? String ?: defaultOptions.pushTargetName,
                    runTargetName = map["runTargetName"] as? String ?: defaultOptions.runTargetName
                )
            }
            else -> throw IllegalArgumentException("Invalid options type: ${options::class}")
        }
    }
    
}