package com.frontseat.plugin.api

import java.nio.file.Path

/**
 * Interface for migration implementations (similar to Nx migrations)
 * Migrators are used to upgrade workspace configurations and code
 */
interface Migrator {
    /**
     * Execute the migration
     */
    suspend fun migrate(
        workspace: WorkspaceContext,
        options: Map<String, Any> = emptyMap()
    ): MigrationResult
    
    /**
     * Check if this migration applies to the current workspace
     */
    fun isApplicable(workspace: WorkspaceContext): Boolean
    
    /**
     * Check if this migration has already been applied
     */
    fun isAlreadyApplied(workspace: WorkspaceContext): Boolean
    
    /**
     * Preview migration changes without executing
     */
    suspend fun preview(workspace: WorkspaceContext): MigrationPreview
    
    /**
     * Validate migration can be safely applied
     */
    fun validate(workspace: WorkspaceContext): ValidationResult
    
    /**
     * Rollback migration if possible
     */
    suspend fun rollback(
        workspace: WorkspaceContext,
        migrationResult: MigrationResult
    ): MigrationResult?
}

/**
 * Result of migration execution
 */
data class MigrationResult(
    val success: Boolean,
    val version: String? = null,              // Version this migration updated to
    val changes: List<MigrationChange> = emptyList(),
    val messages: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val rollbackData: Map<String, Any>? = null, // Data needed for rollback
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Preview of migration changes
 */
data class MigrationPreview(
    val changes: List<MigrationChange> = emptyList(),
    val summary: String = "",
    val warnings: List<String> = emptyList(),
    val risks: List<String> = emptyList()
)

/**
 * Individual change made by migration
 */
data class MigrationChange(
    val file: String,
    val action: MigrationAction,
    val description: String,
    val before: String? = null,     // Content before change
    val after: String? = null,      // Content after change
    val diff: String? = null        // Diff representation
)

enum class MigrationAction {
    CREATE_FILE,
    MODIFY_FILE,
    DELETE_FILE,
    RENAME_FILE,
    UPDATE_DEPENDENCY,
    ADD_DEPENDENCY,
    REMOVE_DEPENDENCY,
    UPDATE_CONFIG,
    ADD_SCRIPT,
    REMOVE_SCRIPT,
    UPDATE_SCHEMA,
    CUSTOM
}

/**
 * Common migration utilities interface
 */
interface MigrationUtils {
    /**
     * Update package.json dependencies
     */
    suspend fun updatePackageDependencies(
        tree: FileTree,
        dependencies: Map<String, String>? = null,
        devDependencies: Map<String, String>? = null,
        peerDependencies: Map<String, String>? = null
    ): Boolean
    
    /**
     * Update package.json scripts
     */
    suspend fun updatePackageScripts(
        tree: FileTree,
        scripts: Map<String, String>
    ): Boolean
    
    /**
     * Update frontseat.json configuration
     */
    suspend fun updateFrontseatConfig(
        tree: FileTree,
        updates: Map<String, Any>
    ): Boolean
    
    /**
     * Install packages using workspace package manager
     */
    suspend fun installPackages(
        workspace: WorkspaceContext,
        packages: List<String> = emptyList()
    ): Boolean
    
    /**
     * Run shell command in workspace
     */
    suspend fun runCommand(
        workspace: WorkspaceContext,
        command: String,
        args: List<String> = emptyList(),
        cwd: String? = null
    ): CommandResult
    
    /**
     * Create backup of file before modification
     */
    fun createBackup(tree: FileTree, filePath: String): String
    
    /**
     * Restore file from backup
     */
    fun restoreFromBackup(tree: FileTree, backupPath: String, originalPath: String): Boolean
    
    /**
     * Parse semantic version
     */
    fun parseVersion(version: String): SemanticVersion?
    
    /**
     * Compare versions
     */
    fun compareVersions(version1: String, version2: String): Int
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val success: Boolean = exitCode == 0
)

/**
 * Semantic version representation
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
    val build: String? = null
) : Comparable<SemanticVersion> {
    
    override fun toString(): String {
        val version = "$major.$minor.$patch"
        return when {
            prerelease != null && build != null -> "$version-$prerelease+$build"
            prerelease != null -> "$version-$prerelease"
            build != null -> "$version+$build"
            else -> version
        }
    }
    
    override fun compareTo(other: SemanticVersion): Int {
        return when {
            major != other.major -> major.compareTo(other.major)
            minor != other.minor -> minor.compareTo(other.minor)
            patch != other.patch -> patch.compareTo(other.patch)
            prerelease == null && other.prerelease != null -> 1
            prerelease != null && other.prerelease == null -> -1
            prerelease != null && other.prerelease != null -> prerelease.compareTo(other.prerelease)
            else -> 0
        }
    }
}

/**
 * Base implementation providing common migration functionality
 */
abstract class BaseMigrator : Migrator {
    protected val utils: MigrationUtils = MigrationUtilsImpl()
    
    override fun isAlreadyApplied(workspace: WorkspaceContext): Boolean {
        // Default implementation - subclasses should override if they can detect previous application
        return false
    }
    
    override fun validate(workspace: WorkspaceContext): ValidationResult {
        // Default implementation - basic validation
        return if (isApplicable(workspace)) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid("Migration is not applicable to this workspace")
        }
    }
    
    override suspend fun rollback(
        workspace: WorkspaceContext,
        migrationResult: MigrationResult
    ): MigrationResult? {
        // Default implementation - no rollback support
        return null
    }
}

/**
 * Default implementation of MigrationUtils
 */
private class MigrationUtilsImpl : MigrationUtils {
    // Implementation would go here
    override suspend fun updatePackageDependencies(
        tree: FileTree,
        dependencies: Map<String, String>?,
        devDependencies: Map<String, String>?,
        peerDependencies: Map<String, String>?
    ): Boolean {
        TODO("Implementation needed")
    }
    
    override suspend fun updatePackageScripts(tree: FileTree, scripts: Map<String, String>): Boolean {
        TODO("Implementation needed")
    }
    
    override suspend fun updateFrontseatConfig(tree: FileTree, updates: Map<String, Any>): Boolean {
        TODO("Implementation needed")
    }
    
    override suspend fun installPackages(workspace: WorkspaceContext, packages: List<String>): Boolean {
        TODO("Implementation needed")
    }
    
    override suspend fun runCommand(
        workspace: WorkspaceContext,
        command: String,
        args: List<String>,
        cwd: String?
    ): CommandResult {
        TODO("Implementation needed")
    }
    
    override fun createBackup(tree: FileTree, filePath: String): String {
        TODO("Implementation needed")
    }
    
    override fun restoreFromBackup(tree: FileTree, backupPath: String, originalPath: String): Boolean {
        TODO("Implementation needed")
    }
    
    override fun parseVersion(version: String): SemanticVersion? {
        TODO("Implementation needed")
    }
    
    override fun compareVersions(version1: String, version2: String): Int {
        TODO("Implementation needed")
    }
}