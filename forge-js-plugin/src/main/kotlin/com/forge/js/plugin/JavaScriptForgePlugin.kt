package com.forge.js.plugin

import com.forge.js.devkit.JavaScriptProjectInference
import com.forge.js.devkit.JavaScriptInferenceOptions
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateNodesResult
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.ForgePlugin
import com.forge.plugin.PluginMetadata
import com.forge.plugin.ValidationResult

/**
 * Options for JavaScript plugin
 */
data class JavaScriptPluginOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test", 
    val lintTargetName: String = "lint",
    val serveTargetName: String = "serve"
)

/**
 * New ForgePlugin implementation for JavaScript/TypeScript/Node.js projects
 */
class JavaScriptForgePlugin : ForgePlugin {
    
    private val inference = JavaScriptProjectInference()
    
    override val metadata = PluginMetadata(
        id = "com.forge.js",
        name = "JavaScript Plugin",
        version = "1.0.0",
        description = "Support for JavaScript, TypeScript, and Node.js projects",
        createNodesPattern = "**/package.json",
        supportedFiles = listOf("package.json", "tsconfig.json", "jest.config.js", "vite.config.ts"),
        author = "Forge Team",
        homepage = "https://github.com/forge/plugin-js",
        tags = listOf("javascript", "typescript", "nodejs", "npm", "yarn", "pnpm")
    )
    
    override val defaultOptions = JavaScriptPluginOptions()
    
    override fun createNodes(
        configFiles: List<String>, 
        options: Any?, 
        context: CreateNodesContext
    ): CreateNodesResult {
        val opts = parseOptions(options)
        val inferenceOptions = JavaScriptInferenceOptions(
            buildTargetName = opts.buildTargetName,
            testTargetName = opts.testTargetName,
            lintTargetName = opts.lintTargetName,
            serveTargetName = opts.serveTargetName
        )
        
        val projects = inference.createNodes(configFiles, inferenceOptions, context)
        return CreateNodesResult(projects = projects)
    }
    
    override fun createDependencies(
        options: Any?, 
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val opts = parseOptions(options)
        val inferenceOptions = JavaScriptInferenceOptions(
            buildTargetName = opts.buildTargetName,
            testTargetName = opts.testTargetName,
            lintTargetName = opts.lintTargetName,
            serveTargetName = opts.serveTargetName
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
    
    private fun parseOptions(options: Any?): JavaScriptPluginOptions {
        return when (options) {
            null -> defaultOptions
            is JavaScriptPluginOptions -> options
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = options as Map<String, Any>
                JavaScriptPluginOptions(
                    buildTargetName = map["buildTargetName"] as? String ?: defaultOptions.buildTargetName,
                    testTargetName = map["testTargetName"] as? String ?: defaultOptions.testTargetName,
                    lintTargetName = map["lintTargetName"] as? String ?: defaultOptions.lintTargetName,
                    serveTargetName = map["serveTargetName"] as? String ?: defaultOptions.serveTargetName
                )
            }
            else -> throw IllegalArgumentException("Invalid options type: ${options::class}")
        }
    }
    
}