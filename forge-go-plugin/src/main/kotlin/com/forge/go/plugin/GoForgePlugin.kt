package com.forge.go.plugin

import com.forge.go.devkit.GoProjectInference
import com.forge.go.devkit.GoInferenceOptions
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateNodesResult
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.ForgePlugin
import com.forge.plugin.PluginMetadata
import com.forge.plugin.ValidationResult

/**
 * Options for Go plugin
 */
data class GoPluginOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test",
    val lintTargetName: String = "lint"
)

/**
 * ForgePlugin implementation for Go projects
 */
class GoForgePlugin : ForgePlugin {
    
    private val inference = GoProjectInference()
    
    override val metadata = PluginMetadata(
        id = "com.forge.go",
        name = "Go Plugin",
        version = "1.0.0",
        description = "Support for Go projects",
        createNodesPattern = "**/go.mod",
        supportedFiles = listOf("go.mod", "go.sum"),
        author = "Forge Team",
        homepage = "https://github.com/forge/plugin-go",
        tags = listOf("go", "golang")
    )
    
    override val defaultOptions = GoPluginOptions()
    
    override fun createNodes(
        configFiles: List<String>, 
        options: Any?, 
        context: CreateNodesContext
    ): CreateNodesResult {
        val opts = parseOptions(options)
        val inferenceOptions = GoInferenceOptions(
            buildTargetName = opts.buildTargetName,
            testTargetName = opts.testTargetName,
            lintTargetName = opts.lintTargetName
        )
        
        val projects = inference.createNodes(configFiles, inferenceOptions, context)
        return CreateNodesResult(projects = projects)
    }
    
    override fun createDependencies(
        options: Any?, 
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val opts = parseOptions(options)
        val inferenceOptions = GoInferenceOptions(
            buildTargetName = opts.buildTargetName,
            testTargetName = opts.testTargetName,
            lintTargetName = opts.lintTargetName
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
    
    private fun parseOptions(options: Any?): GoPluginOptions {
        return when (options) {
            null -> defaultOptions
            is GoPluginOptions -> options
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = options as Map<String, Any>
                GoPluginOptions(
                    buildTargetName = map["buildTargetName"] as? String ?: defaultOptions.buildTargetName,
                    testTargetName = map["testTargetName"] as? String ?: defaultOptions.testTargetName,
                    lintTargetName = map["lintTargetName"] as? String ?: defaultOptions.lintTargetName
                )
            }
            else -> throw IllegalArgumentException("Invalid options type: ${options::class}")
        }
    }
}