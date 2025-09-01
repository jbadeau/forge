package com.forge.plugin

import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateNodesResult
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency

/**
 * Metadata describing a Forge plugin
 */
data class PluginMetadata(
    val id: String,                         // "com.forge.js"
    val name: String,                       // "JavaScript Plugin"
    val version: String,                    // "1.2.0"
    val description: String,                // "Plugin for JavaScript/TypeScript projects"
    val createNodesPattern: String,         // "**/package.json"
    val supportedFiles: List<String>,       // ["package.json", "tsconfig.json"]
    val author: String = "",                // "Forge Team"
    val homepage: String = "",              // "https://github.com/forge/plugin-js"
    val tags: List<String> = emptyList()    // ["javascript", "typescript", "npm"]
)

/**
 * Main interface for Forge plugins
 */
interface ForgePlugin {
    /**
     * Plugin metadata
     */
    val metadata: PluginMetadata
    
    /**
     * Default options for this plugin
     */
    val defaultOptions: Any?
        get() = null
    
    /**
     * Create project nodes from configuration files
     */
    fun createNodes(
        configFiles: List<String>, 
        options: Any?, 
        context: CreateNodesContext
    ): CreateNodesResult
    
    /**
     * Create dependencies between projects
     */
    fun createDependencies(
        options: Any?, 
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> = emptyList()
    
    /**
     * Initialize the plugin (called once when loaded)
     */
    fun initialize() {}
    
    /**
     * Cleanup the plugin (called when unloaded)
     */
    fun cleanup() {}
    
    /**
     * Validate plugin configuration
     */
    fun validateOptions(options: Any?): ValidationResult = ValidationResult.valid()
}

/**
 * Result of plugin option validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun valid() = ValidationResult(true)
        fun invalid(vararg errors: String) = ValidationResult(false, errors.toList())
    }
}