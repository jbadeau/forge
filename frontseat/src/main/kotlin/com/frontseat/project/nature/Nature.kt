package com.frontseat.project.nature

import com.frontseat.task.CommandTask
import java.nio.file.Path
import kotlin.reflect.full.findAnnotation

/**
 * A project nature represents a specific technology, framework, or capability
 * that can be applied to a project. Natures are composable and contribute
 * targets to the appropriate lifecycle phases.
 */
interface Nature {
    /**
     * Unique identifier for this nature
     * Gets value from @NatureInfo annotation if present, otherwise must override
     */
    val id: String
        get() = this::class.findAnnotation<NatureInfo>()?.id
            ?: error("Nature ${this::class.simpleName} must have @NatureInfo annotation or override id")
    
    /**
     * Human-readable name for this nature
     * Defaults to id if not overridden
     */
    val name: String
        get() = id
    
    /**
     * Version of this nature
     */
    val version: String
        get() = "1.0.0"
    
    /**
     * Description of what this nature provides
     */
    val description: String
        get() = ""
    
    /**
     * Layer number for this nature - lower layers are applied first
     * Gets value from @NatureInfo annotation if present, otherwise must override
     * Standard layers:
     * 0 = Build Systems (build tools and package managers)
     * 1 = Languages (programming language detection and tooling)
     * 2 = Runtimes/Platforms (runtime environment and platform tooling)
     * 3 = Frameworks (web frameworks, application frameworks, etc.)
     * 4 = Testing (test frameworks and testing tools)
     * 5 = Infrastructure (containerization, orchestration, IaC, etc.)
     * 6 = Quality/Analysis (linting, formatting, static analysis, etc.)
     * 7 = Deployment/Distribution (publishing, containerization, etc.)
     */
    val layer: Int
        get() = this::class.findAnnotation<NatureInfo>()?.layer
            ?: error("Nature ${this::class.simpleName} must have @NatureInfo annotation or override layer")
    
    
    /**
     * Check if this nature is applicable to the given project path and context
     */
    fun isApplicable(projectPath: Path, context: NatureContext? = null): Boolean
    
    /**
     * Create tasks for this nature, bound to appropriate lifecycle phases
     */
    fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask>
    
    /**
     * Create project dependencies for this nature
     */
    fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency>
}


/**
 * Project dependency discovered by a nature
 */
data class ProjectDependency(
    val source: String,           // source project name/path
    val target: String,           // target project name/path  
    val type: NatureDependencyType,     // type of dependency
    val scope: DependencyScope = DependencyScope.BUILD // when this dependency applies
)

/**
 * Type of project dependency for natures
 */
enum class NatureDependencyType {
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
     * Check if a task with the given name exists
     */
    fun hasTask(taskName: String): Boolean
    
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

/**
 * Default implementation of NatureContext
 */
class NatureContextImpl(
    private val projectPath: Path,
    private val appliedNatures: Set<String>,
    private val availableTasks: MutableSet<String> = mutableSetOf()
) : NatureContext {
    
    override fun getAppliedNatures(): Set<String> = appliedNatures
    
    override fun hasNature(natureId: String): Boolean = appliedNatures.contains(natureId)
    
    override fun hasTask(taskName: String): Boolean = availableTasks.contains(taskName)
    
    override fun getProjectPath(): Path = projectPath
    
    override fun getAllProjects(): List<ProjectInfo> {
        // TODO: Implement workspace-aware project discovery
        return emptyList()
    }
    
    override fun findProjects(predicate: (ProjectInfo) -> Boolean): List<ProjectInfo> {
        // TODO: Implement workspace-aware project search
        return emptyList()
    }
}

/**
 * Standard nature layers - lower numbers are applied first
 */
object NatureLayers {
    const val BUILD_SYSTEMS = 0     // build tools and package managers
    const val LANGUAGES = 1         // programming language detection and tooling
    const val RUNTIMES = 2          // runtime environment and platform tooling
    const val FRAMEWORKS = 3        // web frameworks, application frameworks, etc.
    const val TESTING = 4           // test frameworks and testing tools
    const val INFRASTRUCTURE = 5    // containerization, orchestration, IaC, etc.
    const val QUALITY = 6           // linting, formatting, static analysis, etc.
    const val DEPLOYMENT = 7        // publishing, containerization, etc.
}