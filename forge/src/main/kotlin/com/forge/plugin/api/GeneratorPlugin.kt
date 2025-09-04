package com.forge.plugin.api

import java.nio.file.Path

/**
 * Interface for generator implementations (similar to Nx generators)
 * Generators are used to create or modify code in a workspace
 */
interface Generator {
    /**
     * Execute the generator with the given options
     */
    suspend fun generate(
        workspace: WorkspaceContext,
        options: Map<String, Any>
    ): GeneratorResult
    
    /**
     * Validate generator options before execution
     */
    fun validate(options: Map<String, Any>): ValidationResult = ValidationResult.valid()
    
    /**
     * Get prompts for interactive mode
     */
    fun getPrompts(): List<GeneratorPrompt> = emptyList()
    
    /**
     * Preview changes without executing (dry-run mode)
     */
    suspend fun preview(
        workspace: WorkspaceContext,
        options: Map<String, Any>
    ): GeneratorPreview
}

/**
 * Context provided to generators
 */
data class WorkspaceContext(
    val root: Path,
    val tree: FileTree,
    val logger: GeneratorLogger,
    val packageManager: PackageManager = PackageManager.NPM,
    val gitInitialized: Boolean = false,
    val projects: Map<String, ProjectInfo> = emptyMap(),
    val configuration: Map<String, Any> = emptyMap()
)

/**
 * File tree interface for generators to manipulate workspace files
 */
interface FileTree {
    /**
     * Read a file's content
     */
    fun read(path: String): String?
    
    /**
     * Write content to a file
     */
    fun write(path: String, content: String)
    
    /**
     * Delete a file or directory
     */
    fun delete(path: String)
    
    /**
     * Check if file exists
     */
    fun exists(path: String): Boolean
    
    /**
     * List files in directory
     */
    fun children(path: String): List<String>
    
    /**
     * Copy file or directory
     */
    fun copy(from: String, to: String)
    
    /**
     * Move/rename file or directory
     */
    fun move(from: String, to: String)
    
    /**
     * Create directory
     */
    fun mkdir(path: String)
    
    /**
     * Get file metadata
     */
    fun stat(path: String): FileMetadata?
}

data class FileMetadata(
    val isFile: Boolean,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * Logger interface for generators
 */
interface GeneratorLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun fatal(message: String)
}

/**
 * Package manager types
 */
enum class PackageManager {
    NPM,
    YARN,
    PNPM,
    MAVEN,
    GRADLE,
    CARGO
}

/**
 * Project information
 */
data class ProjectInfo(
    val name: String,
    val root: String,
    val type: String,
    val tags: List<String> = emptyList(),
    val targets: Map<String, Any> = emptyMap()
)

/**
 * Result of generator execution
 */
data class GeneratorResult(
    val success: Boolean,
    val files: List<GeneratedFile> = emptyList(),
    val messages: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

data class GeneratedFile(
    val path: String,
    val action: FileAction,
    val content: String? = null
)

enum class FileAction {
    CREATE,
    MODIFY,
    DELETE,
    RENAME
}

/**
 * Preview of generator changes (dry-run)
 */
data class GeneratorPreview(
    val files: List<PreviewFile> = emptyList(),
    val summary: String = ""
)

data class PreviewFile(
    val path: String,
    val action: FileAction,
    val diff: String? = null  // For modifications, show diff
)

/**
 * Interactive prompts for generators
 */
data class GeneratorPrompt(
    val name: String,
    val type: PromptType,
    val message: String,
    val default: Any? = null,
    val choices: List<PromptChoice>? = null,
    val validate: ((Any) -> String?)? = null,
    val condition: ((Map<String, Any>) -> Boolean)? = null
)

enum class PromptType {
    INPUT,          // Text input
    CONFIRM,        // Yes/no confirmation
    SELECT,         // Single selection from list
    MULTISELECT,    // Multiple selections from list
    PASSWORD        // Hidden text input
}

data class PromptChoice(
    val name: String,
    val value: Any,
    val description: String? = null
)