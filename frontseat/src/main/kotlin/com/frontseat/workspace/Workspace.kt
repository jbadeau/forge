package com.frontseat.workspace

import com.frontseat.project.ProjectGraph
import com.frontseat.project.ProjectGraphBuilder
import com.frontseat.project.ProjectGraphNode
import com.frontseat.project.DiscoveryPlugin
import com.frontseat.task.TaskGraph
import com.frontseat.task.TaskGraphBuilder
import com.frontseat.task.Task
import com.frontseat.task.TaskId
import com.frontseat.task.TaskExecutionPlan
import com.frontseat.plugin.PluginManager
import com.frontseat.project.nature.NatureRegistry
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Main workspace API entry point that provides unified access to projects and tasks.
 * 
 * This is the primary interface for interacting with a Forge workspace, similar to 
 * how Moon, Buck, and other build tools provide a workspace-level API.
 */
class Workspace private constructor(
    val root: Path,
    private val configuration: WorkspaceConfiguration,
    private val pluginManager: PluginManager,
    private val natureRegistry: NatureRegistry = NatureRegistry()
) {
    
    /**
     * Get the project graph - builds fresh each time
     */
    fun getProjectGraph(): ProjectGraph {
        val discoveryPlugins = pluginManager.loadPlugins(root)
            .filterIsInstance<DiscoveryPlugin>()
        val builder = ProjectGraphBuilder(root, discoveryPlugins, true, natureRegistry)
        return builder.buildProjectGraph()
    }
    
    /**
     * Get the task graph - builds fresh each time
     */
    fun getTaskGraph(): TaskGraph {
        val projectGraph = getProjectGraph()
        val builder = TaskGraphBuilder(projectGraph)
        // For now, build with "build" as default target
        // TODO: Make this configurable or build all targets
        return builder.buildTaskGraph("build")
    }
    
    // === Project API ===
    
    /**
     * Get all projects in the workspace
     */
    fun getProjects(): Collection<ProjectGraphNode> = getProjectGraph().getAllProjects()
    
    /**
     * Get a specific project by name
     */
    fun getProject(name: String): ProjectGraphNode? = getProjectGraph().getProject(name)
    
    /**
     * Get projects filtered by tag
     */
    fun getProjectsByTag(tag: String): List<ProjectGraphNode> = getProjectGraph().getProjectsByTag(tag)
    
    /**
     * Get projects filtered by type
     */
    fun getProjectsByType(type: String): List<ProjectGraphNode> = getProjectGraph().getProjectsByType(type)
    
    /**
     * Check if a project exists
     */
    fun hasProject(name: String): Boolean = getProjectGraph().hasProject(name)
    
    /**
     * Get project names in topological order (respecting dependencies)
     */
    fun getProjectsTopologicalOrder(): List<List<String>> = getProjectGraph().topologicalSort()
    
    // === Task API ===
    
    /**
     * Get all tasks in the workspace
     */
    fun getTasks(): Collection<Task> = getTaskGraph().getAllTasks()
    
    /**
     * Get a specific task by ID
     */
    fun getTask(taskId: String): Task? = getTaskGraph().getTask(taskId)
    
    /**
     * Get a specific task by TaskId object
     */
    fun getTask(taskId: TaskId): Task? = getTaskGraph().getTask(taskId)
    
    /**
     * Get all tasks for a specific project
     */
    fun getTasksByProject(projectName: String): List<Task> = getTaskGraph().getTasksByProject(projectName)
    
    /**
     * Get all tasks for a specific target name (across all projects)
     */
    fun getTasksByTarget(targetName: String): List<Task> = getTaskGraph().getTasksByTarget(targetName)
    
    /**
     * Get tasks that have no dependencies (root tasks)
     */
    fun getRootTasks(): List<Task> = getTaskGraph().getRootTasks()
    
    /**
     * Get tasks that no other tasks depend on (leaf tasks)
     */
    fun getLeafTasks(): List<Task> = getTaskGraph().getLeafTasks()
    
    /**
     * Check if a task exists
     */
    fun hasTask(taskId: String): Boolean = getTaskGraph().hasTask(taskId)
    
    /**
     * Get task dependencies
     */
    fun getTaskDependencies(taskId: String): List<String> = getTaskGraph().getDependencies(taskId)
    
    /**
     * Get tasks that depend on the given task
     */
    fun getTaskDependents(taskId: String): List<String> = getTaskGraph().getDependents(taskId)
    
    /**
     * Get tasks in topological order for execution
     */
    fun getTasksTopologicalOrder(): List<Task> = getTaskGraph().getTopologicalOrder()
    
    /**
     * Get an execution plan for all tasks
     */
    fun getTaskExecutionPlan(): TaskExecutionPlan = getTaskGraph().getExecutionPlan()
    
    /**
     * Get execution plan for specific tasks
     */
    fun getTaskExecutionPlan(taskIds: Set<String>): TaskExecutionPlan {
        val tasks = taskIds.mapNotNull { getTask(it) }.toSet()
        return getTaskGraph().getExecutionPlan(tasks)
    }
    
    // === Plugin/Nature Registration ===
    
    /**
     * Get the nature registry
     */
    fun getNatureRegistry(): NatureRegistry = natureRegistry
    
    // === Workspace Info ===
    
    // root property is already exposed via constructor parameter
    
    /**
     * Get workspace configuration
     */
    fun getConfiguration(): WorkspaceConfiguration = configuration
    
    /**
     * Get plugin manager
     */
    fun getPluginManager(): PluginManager = pluginManager
    
    // No caching, so no refresh method needed
    
    /**
     * Get workspace statistics
     */
    fun getStats(): WorkspaceStats {
        val projectGraph = getProjectGraph()
        val taskGraph = getTaskGraph()
        
        return WorkspaceStats(
            projectCount = projectGraph.getAllProjects().size,
            taskCount = taskGraph.getAllTasks().size,
            projectTypes = projectGraph.getAllProjects()
                .groupBy { it.data.projectType }
                .mapValues { it.value.size },
            targetTypes = taskGraph.getAllTasks()
                .groupBy { it.target }
                .mapValues { it.value.size }
        )
    }
    
    companion object {
        /**
         * Discover and create a workspace from the current directory or specified path
         */
        @JvmStatic
        @JvmOverloads
        fun discover(startPath: Path = Paths.get("").toAbsolutePath()): Workspace {
            val workspaceRoot = findWorkspaceRoot(startPath)
                ?: throw IllegalStateException("Could not find workspace root (no frontseat.json found)")
            
            return create(workspaceRoot)
        }
        
        /**
         * Create a workspace from a specific root directory
         */
        @JvmStatic
        fun create(workspaceRoot: Path): Workspace {
            require(workspaceRoot.isDirectory()) { "Workspace root must be a directory: $workspaceRoot" }
            
            val configPath = workspaceRoot.resolve(".forge").resolve("workspace.yml")
                .takeIf { it.exists() } 
                ?: workspaceRoot.resolve("frontseat.json")
                    .takeIf { it.exists() }
                ?: throw IllegalStateException("No workspace configuration found at $workspaceRoot")
            
            val configuration = if (configPath.toString().endsWith(".yml")) {
                // TODO: Implement YAML loading
                WorkspaceConfiguration()
            } else {
                WorkspaceConfiguration.load(configPath)
            }
            
            val pluginManager = PluginManager()
            
            return Workspace(workspaceRoot, configuration, pluginManager)
        }
        
        /**
         * Find workspace root by looking for frontseat.json or .forge directory
         */
        private fun findWorkspaceRoot(startPath: Path): Path? {
            var current = startPath
            
            while (true) {
                // Look for frontseat.json or .frontseat/workspace.yml
                if (current.resolve("frontseat.json").exists() || 
                    current.resolve(".forge").resolve("workspace.yml").exists()) {
                    return current
                }
                
                val parent = current.parent ?: break
                current = parent
            }
            
            return null
        }
    }
}

/**
 * Statistics about the workspace
 */
data class WorkspaceStats(
    val projectCount: Int,
    val taskCount: Int,
    val projectTypes: Map<String, Int>,
    val targetTypes: Map<String, Int>
)