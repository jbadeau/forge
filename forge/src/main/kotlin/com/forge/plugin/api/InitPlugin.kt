package com.forge.plugin.api

import java.nio.file.Path

/**
 * Interface for workspace initialization implementations (similar to Nx create-nx-workspace presets)
 * Initializers set up new workspaces with predefined structures and configurations
 */
interface Initializer {
    /**
     * Initialize a new workspace with the given template and options
     */
    suspend fun initialize(
        targetPath: Path,
        options: Map<String, Any>
    ): InitResult
    
    /**
     * Validate initialization options
     */
    fun validate(options: Map<String, Any>): ValidationResult = ValidationResult.valid()
    
    /**
     * Get available presets for this init template
     */
    fun getPresets(): List<InitPresetInfo> = emptyList()
    
    /**
     * Get prompts for interactive initialization
     */
    fun getPrompts(): List<GeneratorPrompt> = emptyList()
    
    /**
     * Preview what would be created without actually creating it
     */
    suspend fun preview(
        targetPath: Path,
        options: Map<String, Any>
    ): InitPreview
    
    /**
     * Check if target directory is suitable for initialization
     */
    fun canInitialize(targetPath: Path): ValidationResult
}

/**
 * Result of workspace initialization
 */
data class InitResult(
    val success: Boolean,
    val workspaceName: String,
    val workspaceRoot: Path,
    val files: List<InitFile> = emptyList(),
    val projects: List<String> = emptyList(),    // Created projects
    val plugins: List<String> = emptyList(),     // Installed plugins
    val nextSteps: List<String> = emptyList(),
    val messages: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * File created during initialization
 */
data class InitFile(
    val path: String,
    val content: String? = null,
    val template: String? = null,    // Template used to generate this file
    val executable: Boolean = false
)

/**
 * Preview of initialization
 */
data class InitPreview(
    val files: List<PreviewFile> = emptyList(),
    val structure: String = "",      // ASCII tree representation
    val summary: String = "",
    val commands: List<String> = emptyList() // Commands that would be run
)

/**
 * Information about available presets
 */
data class InitPresetInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val options: Map<String, Any> = emptyMap(),
    val hidden: Boolean = false
)

/**
 * Context for initialization templates
 */
data class InitContext(
    val templateRoot: Path,          // Root of template files
    val targetRoot: Path,            // Target workspace root
    val options: Map<String, Any>,   // User-provided options
    val tree: FileTree,              // File system operations
    val logger: GeneratorLogger,
    val packageManager: PackageManager = PackageManager.NPM
)

/**
 * Template engine for processing template files
 */
interface TemplateEngine {
    /**
     * Process template file with given variables
     */
    fun processTemplate(
        templateContent: String,
        variables: Map<String, Any>
    ): String
    
    /**
     * Process filename template (for dynamic filenames)
     */
    fun processFileName(
        fileName: String,
        variables: Map<String, Any>
    ): String
    
    /**
     * Get all variables used in template
     */
    fun extractVariables(templateContent: String): Set<String>
}

/**
 * Base implementation for initializers
 */
abstract class BaseInitializer : Initializer {
    protected val templateEngine: TemplateEngine = DefaultTemplateEngine()
    
    override fun canInitialize(targetPath: Path): ValidationResult {
        return when {
            !targetPath.toFile().exists() -> ValidationResult.valid()
            targetPath.toFile().isFile() -> ValidationResult.invalid("Target path is a file, not a directory")
            targetPath.toFile().listFiles()?.isNotEmpty() == true -> 
                ValidationResult.invalid("Target directory is not empty")
            else -> ValidationResult.valid()
        }
    }
    
    override suspend fun preview(targetPath: Path, options: Map<String, Any>): InitPreview {
        // Default implementation - subclasses should override for better previews
        return InitPreview(
            summary = "Would initialize workspace at ${targetPath.toAbsolutePath()}"
        )
    }
    
    /**
     * Copy template files to target directory
     */
    protected suspend fun copyTemplateFiles(
        context: InitContext,
        templateSubdir: String = ""
    ): List<InitFile> {
        val files = mutableListOf<InitFile>()
        val templateDir = if (templateSubdir.isEmpty()) {
            context.templateRoot
        } else {
            context.templateRoot.resolve(templateSubdir)
        }
        
        // Implementation would recursively copy and process template files
        TODO("Implementation needed")
    }
    
    /**
     * Process template variables
     */
    protected fun createTemplateVariables(
        options: Map<String, Any>,
        workspaceName: String
    ): Map<String, Any> {
        val variables = mutableMapOf<String, Any>()
        variables.putAll(options)
        variables["workspaceName"] = workspaceName
        variables["WorkspaceName"] = workspaceName.split("-", "_").joinToString("") { 
            it.replaceFirstChar { c -> c.uppercase() } 
        }
        variables["workspace_name"] = workspaceName.replace("-", "_")
        variables["WORKSPACE_NAME"] = workspaceName.replace("-", "_").uppercase()
        return variables
    }
}

/**
 * Default template engine implementation
 */
private class DefaultTemplateEngine : TemplateEngine {
    override fun processTemplate(templateContent: String, variables: Map<String, Any>): String {
        var result = templateContent
        variables.forEach { (key, value) ->
            // Simple variable substitution: <%= variable %>
            result = result.replace("<%=$key%>", value.toString())
            // Handlebars-style: {{variable}}
            result = result.replace("{{$key}}", value.toString())
            // Shell-style: ${variable}
            result = result.replace("\${$key}", value.toString())
        }
        return result
    }
    
    override fun processFileName(fileName: String, variables: Map<String, Any>): String {
        return processTemplate(fileName, variables)
    }
    
    override fun extractVariables(templateContent: String): Set<String> {
        val variables = mutableSetOf<String>()
        
        // Extract <%=variable%> style
        Regex("<%=([^%]+)%>").findAll(templateContent).forEach { match ->
            variables.add(match.groupValues[1].trim())
        }
        
        // Extract {{variable}} style
        Regex("\\{\\{([^}]+)\\}\\}").findAll(templateContent).forEach { match ->
            variables.add(match.groupValues[1].trim())
        }
        
        // Extract ${variable} style
        Regex("\\$\\{([^}]+)\\}").findAll(templateContent).forEach { match ->
            variables.add(match.groupValues[1].trim())
        }
        
        return variables
    }
}