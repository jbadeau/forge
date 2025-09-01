package com.forge.maven.plugin

import com.forge.maven.devkit.MavenProjectInference
import com.forge.maven.devkit.MavenInferenceOptions
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateNodesResult
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.ForgePlugin
import com.forge.plugin.PluginMetadata
import com.forge.plugin.ValidationResult

/**
 * Options for Maven plugin
 */
data class MavenPluginOptions(
    val buildTargetName: String = "compile",
    val testTargetName: String = "test",
    val packageTargetName: String = "package"
)

/**
 * ForgePlugin implementation for Maven projects
 */
class MavenForgePlugin : ForgePlugin {
    
    private val inference = MavenProjectInference()
    
    override val metadata = PluginMetadata(
        id = "com.forge.maven",
        name = "Maven Plugin",
        version = "1.0.0",
        description = "Support for Maven/Java projects",
        createNodesPattern = "**/pom.xml",
        supportedFiles = listOf("pom.xml"),
        author = "Forge Team",
        homepage = "https://github.com/forge/plugin-maven",
        tags = listOf("maven", "java", "kotlin", "scala")
    )
    
    override val defaultOptions = MavenPluginOptions()
    
    override fun createNodes(
        configFiles: List<String>, 
        options: Any?, 
        context: CreateNodesContext
    ): CreateNodesResult {
        val opts = parseOptions(options)
        val inferenceOptions = MavenInferenceOptions(
            buildTargetName = opts.buildTargetName,
            testTargetName = opts.testTargetName,
            packageTargetName = opts.packageTargetName
        )
        
        val projects = inference.createNodes(configFiles, inferenceOptions, context)
        return CreateNodesResult(projects = projects)
    }
    
    override fun createDependencies(
        options: Any?, 
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val opts = parseOptions(options)
        val inferenceOptions = MavenInferenceOptions(
            buildTargetName = opts.buildTargetName,
            testTargetName = opts.testTargetName,
            packageTargetName = opts.packageTargetName
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
    
    private fun parseOptions(options: Any?): MavenPluginOptions {
        return when (options) {
            null -> defaultOptions
            is MavenPluginOptions -> options
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = options as Map<String, Any>
                MavenPluginOptions(
                    buildTargetName = map["buildTargetName"] as? String ?: defaultOptions.buildTargetName,
                    testTargetName = map["testTargetName"] as? String ?: defaultOptions.testTargetName,
                    packageTargetName = map["packageTargetName"] as? String ?: defaultOptions.packageTargetName
                )
            }
            else -> throw IllegalArgumentException("Invalid options type: ${options::class}")
        }
    }
}