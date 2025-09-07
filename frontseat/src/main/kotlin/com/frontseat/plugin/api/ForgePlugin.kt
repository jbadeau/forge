package com.frontseat.plugin.api

import java.nio.file.Path

/**
 * Core Forge plugin interface that provides Nx-like capabilities:
 * - Executors: Custom task runners
 * - Generators: Code and project scaffolding
 * - Migrators: Workspace migration tools
 * - Init: Workspace initialization
 * - Inference: Project discovery and configuration
 */
interface FrontseatPlugin {
    /**
     * Plugin metadata
     */
    val metadata: PluginMetadata
    
    /**
     * Get executors provided by this plugin
     */
    fun getExecutors(): Map<String, ExecutorSchema> = emptyMap()
    
    /**
     * Get generators provided by this plugin
     */
    fun getGenerators(): Map<String, GeneratorSchema> = emptyMap()
    
    /**
     * Get migrators provided by this plugin
     */
    fun getMigrators(): Map<String, MigratorSchema> = emptyMap()
    
    /**
     * Get init templates provided by this plugin
     */
    fun getInitTemplates(): Map<String, InitSchema> = emptyMap()
    
    /**
     * Get project inference capability
     */
    fun getProjectInference(): ProjectInferenceSchema? = null
    
    /**
     * Initialize the plugin with configuration
     */
    fun initialize(config: PluginConfiguration) {}
    
    /**
     * Called when plugin is being shut down
     */
    fun shutdown() {}
    
    /**
     * Health check for the plugin
     */
    fun healthCheck(): HealthStatus = HealthStatus.HEALTHY
}

/**
 * Plugin metadata similar to Nx plugin metadata
 */
data class PluginMetadata(
    val id: String,                         // "com.frontseat.plugin.react"
    val name: String,                       // "React Plugin"
    val version: String,                    // "1.2.0"
    val description: String,                // "React development tools for Forge"
    val author: String = "",                // "Forge Team"
    val homepage: String = "",              // "https://github.com/frontseat/plugin-react"
    val repository: String = "",            // "https://github.com/frontseat/plugin-react"
    val license: String = "",               // "MIT"
    val keywords: List<String> = emptyList(), // ["react", "frontend", "javascript"]
    val tags: List<String> = emptyList(),   // ["web", "frontend"]
    val supportedPlatforms: Set<Platform> = setOf(Platform.ANY),
    val minimumForgeVersion: String? = null, // "1.0.0"
    val peerDependencies: Map<String, String> = emptyMap() // {"@frontseat/js": "^1.0.0"}
)

/**
 * Plugin configuration passed during initialization
 */
data class PluginConfiguration(
    val workspaceRoot: Path,
    val pluginConfig: Map<String, Any> = emptyMap(),
    val globalConfig: Map<String, Any> = emptyMap(),
    val environment: Map<String, String> = emptyMap(),
    val cacheDir: Path? = null,
    val isCI: Boolean = false
)

/**
 * Schema for executors (similar to Nx executors)
 */
data class ExecutorSchema(
    val implementation: String,             // Class name or function reference
    val schema: JsonSchema,                 // JSON schema for options validation
    val description: String = "",
    val examples: List<ExecutorExample> = emptyList()
)

data class ExecutorExample(
    val name: String,
    val description: String,
    val options: Map<String, Any>
)

/**
 * Schema for generators (similar to Nx generators)
 */
data class GeneratorSchema(
    val implementation: String,             // Class name or function reference
    val schema: JsonSchema,                 // JSON schema for options validation
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val examples: List<GeneratorExample> = emptyList(),
    val hidden: Boolean = false
)

data class GeneratorExample(
    val name: String,
    val description: String,
    val command: String
)

/**
 * Schema for migrators (similar to Nx migrations)
 */
data class MigratorSchema(
    val implementation: String,             // Class name or function reference
    val description: String = "",
    val version: String,                    // Target version this migration applies to
    val cli: String = "frontseat",              // CLI command that should run this migration
    val packageJson: PackageJsonMigration? = null,
    val forgeJson: ForgeJsonMigration? = null
)

data class PackageJsonMigration(
    val dependencies: Map<String, String>? = null,
    val devDependencies: Map<String, String>? = null,
    val scripts: Map<String, String>? = null
)

data class ForgeJsonMigration(
    val version: String? = null,
    val plugins: List<String>? = null
)

/**
 * Schema for init templates (similar to Nx create-nx-workspace presets)
 */
data class InitSchema(
    val implementation: String,             // Class name or function reference
    val schema: JsonSchema,                 // JSON schema for options validation
    val description: String = "",
    val hidden: Boolean = false,
    val presets: List<InitPreset> = emptyList()
)

data class InitPreset(
    val name: String,
    val description: String,
    val options: Map<String, Any>
)

/**
 * Schema for project inference capability
 */
data class ProjectInferenceSchema(
    val implementation: String,             // Class name implementing ProjectGraphInferrer
    val configFiles: List<String>,          // ["package.json", "tsconfig.json"]
    val optionsSchema: JsonSchema? = null   // Schema for inference options
)

/**
 * JSON Schema representation for validation
 */
data class JsonSchema(
    val type: String = "object",
    val properties: Map<String, JsonProperty> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean = false,
    val title: String? = null,
    val description: String? = null,
    val examples: List<Map<String, Any>> = emptyList()
)

data class JsonProperty(
    val type: String,                       // "string", "number", "boolean", "array", "object"
    val description: String? = null,
    val default: Any? = null,
    val enum: List<Any>? = null,
    val items: JsonProperty? = null,        // For arrays
    val properties: Map<String, JsonProperty>? = null, // For objects
    val pattern: String? = null,            // For string validation
    val minimum: Number? = null,            // For number validation
    val maximum: Number? = null,            // For number validation
    val examples: List<Any> = emptyList()
)