package com.frontseat.nature

import com.frontseat.annotation.Nature
import com.frontseat.command.CommandTask
import java.nio.file.Path
import kotlin.reflect.full.findAnnotation

/**
 * A project nature represents a specific technology, framework, or capability
 * that can be applied to a project. Natures are composable and contribute
 * targets to the appropriate lifecycle phases.
 */
interface ProjectNature {
    /**
     * Unique identifier for this nature
     * Gets value from @Nature annotation if present, otherwise must override
     */
    val id: String
        get() = this::class.findAnnotation<Nature>()?.id
            ?: error("Nature ${this::class.simpleName} must have @Nature annotation or override id")
    
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
     * Gets value from @Nature annotation if present, otherwise must override
     * Standard layers:
     * 0 = Build Systems (Maven, Gradle, Cargo, npm, etc.)
     * 1 = Languages (Java, Kotlin, JavaScript, TypeScript, Rust, Go, etc.)
     * 2 = Runtimes/Platforms (Node.js, JVM, .NET, etc.)
     * 3 = Frameworks (Spring Boot, React, Angular, Next.js, FastAPI, etc.)
     * 4 = Testing (JUnit, Jest, Cypress, Playwright, etc.)
     * 5 = Infrastructure (Docker, Kubernetes, Terraform, etc.)
     * 6 = Quality/Analysis (SonarQube, ESLint, Prettier, etc.)
     * 7 = Deployment/Distribution (Jib, Docker Build, NPM Publish, etc.)
     */
    val layer: Int
        get() = this::class.findAnnotation<Nature>()?.layer
            ?: error("Nature ${this::class.simpleName} must have @Nature annotation or override layer")
    
    
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
    const val BUILD_SYSTEMS = 0     // Maven, Gradle, Cargo, npm, etc.
    const val LANGUAGES = 1         // Java, Kotlin, JavaScript, TypeScript, Rust, Go, etc.
    const val RUNTIMES = 2          // Node.js, JVM, .NET, etc.
    const val FRAMEWORKS = 3        // Spring Boot, React, Angular, Next.js, FastAPI, etc.
    const val TESTING = 4           // JUnit, Jest, Cypress, Playwright, etc.
    const val INFRASTRUCTURE = 5    // Docker, Kubernetes, Terraform, etc.
    const val QUALITY = 6           // SonarQube, ESLint, Prettier, etc.
    const val DEPLOYMENT = 7        // Jib, Docker Build, NPM Publish, etc.
}