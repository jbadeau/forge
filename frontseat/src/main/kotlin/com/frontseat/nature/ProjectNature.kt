package com.frontseat.nature

import com.frontseat.plugin.api.TargetConfiguration
import java.nio.file.Path

/**
 * A project nature represents a specific technology, framework, or capability
 * that can be applied to a project. Natures are composable and contribute
 * targets to the appropriate lifecycle phases.
 */
interface ProjectNature {
    /**
     * Unique identifier for this nature
     */
    val id: String
    
    /**
     * Human-readable name for this nature
     */
    val name: String
    
    /**
     * Version of this nature
     */
    val version: String
    
    /**
     * Description of what this nature provides
     */
    val description: String
    
    /**
     * Natures that this nature requires to be present
     */
    val dependencies: List<String>
    
    /**
     * Natures that this nature conflicts with (cannot be used together)
     */
    val conflicts: List<String>
    
    /**
     * Check if this nature is applicable to the given project path
     */
    fun isApplicable(projectPath: Path): Boolean
    
    /**
     * Create targets for this nature, bound to appropriate lifecycle phases
     */
    fun createTargets(projectPath: Path, context: NatureContext): Map<String, NatureTargetDefinition>
    
    /**
     * Create project dependencies for this nature
     */
    fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency>
}

/**
 * Target definition with lifecycle binding
 */
data class NatureTargetDefinition(
    val configuration: TargetConfiguration,
    val lifecycle: TargetLifecycle,
    val cacheable: Boolean = true // Build targets cacheable by default
)

/**
 * Project dependency discovered by a nature
 */
data class ProjectDependency(
    val source: String,           // source project name/path
    val target: String,           // target project name/path  
    val type: DependencyType,     // type of dependency
    val scope: DependencyScope = DependencyScope.BUILD // when this dependency applies
)

/**
 * Type of project dependency
 */
enum class DependencyType {
    COMPILE,        // compile-time dependency
    RUNTIME,        // runtime dependency
    TEST,           // test dependency
    PROVIDED,       // provided dependency
    PLUGIN,         // build plugin dependency
    PARENT          // parent project dependency
}

/**
 * Scope when dependency applies
 */
enum class DependencyScope {
    BUILD,          // during build lifecycle
    DEVELOPMENT,    // during development lifecycle  
    RELEASE,        // during release lifecycle
    ALL            // during all lifecycles
}

/**
 * Context provided to natures during target creation
 */
interface NatureContext {
    /**
     * Get all applied nature IDs
     */
    fun getAppliedNatures(): Set<String>
    
    /**
     * Check if a specific nature is applied
     */
    fun hasNature(natureId: String): Boolean
    
    /**
     * Check if a target with the given name exists
     */
    fun hasTarget(targetName: String): Boolean
    
    /**
     * Get project root path
     */
    fun getProjectPath(): Path
    
    /**
     * Get information about all projects in the workspace
     */
    fun getAllProjects(): List<ProjectInfo>
    
    /**
     * Find projects that match a pattern or criteria
     */
    fun findProjects(predicate: (ProjectInfo) -> Boolean): List<ProjectInfo>
}

/**
 * Information about a project in the workspace
 */
data class ProjectInfo(
    val name: String,
    val path: Path,
    val natures: Set<String>,
    val type: String? = null
)