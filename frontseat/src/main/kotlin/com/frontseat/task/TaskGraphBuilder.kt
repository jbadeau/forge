package com.frontseat.task

import com.frontseat.project.ProjectGraph
import com.frontseat.plugin.api.ProjectConfiguration
import com.frontseat.plugin.api.TargetConfiguration
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.*

class TaskGraphBuilder(
    private val projectGraph: ProjectGraph
) {
    private val logger = LoggerFactory.getLogger(TaskGraphBuilder::class.java)
    
    fun buildTaskGraph(
        targetName: String,
        projectNames: Set<String> = projectGraph.nodes.keys
    ): TaskGraph {
        logger.info("Building task graph for target '$targetName' with ${projectNames.size} projects")
        
        val tasks = mutableMapOf<String, Task>()
        val dependencies = mutableMapOf<String, MutableList<String>>()
        
        // Recursively create tasks for all projects that have the target and their dependencies
        projectNames.forEach { projectName ->
            val project = projectGraph.getProject(projectName)
            if (project != null && project.data.hasTarget(targetName)) {
                createTaskWithDependencies(projectName, targetName, project.data, tasks, dependencies)
            }
        }
        
        // Resolve dependencies for each task
        tasks.values.forEach { task ->
            resolveDependencies(task, tasks, dependencies)
        }
        
        // Find root tasks (no dependencies)
        val roots = dependencies.entries
            .filter { it.value.isEmpty() }
            .map { it.key }
        
        logger.info("Built task graph with ${tasks.size} tasks and ${roots.size} root tasks")
        
        val graph = TaskGraph()
        
        // Add all tasks to the graph
        tasks.values.forEach { task ->
            graph.addTask(task)
        }
        
        return graph
    }
    
    private fun createTaskWithDependencies(
        projectName: String,
        targetName: String,
        projectConfig: ProjectConfiguration,
        tasks: MutableMap<String, Task>,
        dependencies: MutableMap<String, MutableList<String>>
    ) {
        val taskId = "$projectName:$targetName"
        
        // Skip if task already exists
        if (tasks.containsKey(taskId)) {
            return
        }
        
        // Check if project has this target
        if (!projectConfig.hasTarget(targetName)) {
            return
        }
        
        val target = projectConfig.getTarget(targetName)!!
        
        // Create the main task
        tasks[taskId] = Task(
            id = TaskId(taskId),
            project = projectName,
            target = targetName,
            configuration = target,
            dependencies = emptySet() // Will be populated later
        )
        dependencies[taskId] = mutableListOf()
        
        // Recursively create dependency tasks within the same project
        target.dependsOn.forEach { depTargetName ->
            if (projectConfig.hasTarget(depTargetName)) {
                createTaskWithDependencies(projectName, depTargetName, projectConfig, tasks, dependencies)
            }
        }
    }
    
    private fun resolveDependencies(
        task: Task,
        allTasks: Map<String, Task>,
        dependencies: MutableMap<String, MutableList<String>>
    ) {
        val dependsOn = task.configuration.dependsOn
        
        dependsOn.forEach { depString ->
            val resolvedDeps = resolveDependencyString(depString, task, allTasks)
            dependencies[task.id.value]?.addAll(resolvedDeps)
        }
    }
    
    private fun resolveDependencyString(
        depString: String,
        currentTask: Task,
        allTasks: Map<String, Task>
    ): List<String> {
        return when {
            // ^target - depends on same target in all dependencies of current project
            depString.startsWith("^") -> {
                val targetName = depString.substring(1)
                resolveProjectDependencies(currentTask.project, targetName, allTasks)
            }
            
            // self:target - depends on specific target in same project
            depString.contains(":") -> {
                val parts = depString.split(":")
                if (parts.size == 2) {
                    val (projectName, targetName) = parts
                    val resolvedProject = if (projectName == "self") currentTask.project else projectName
                    val taskId = "$resolvedProject:$targetName"
                    if (allTasks.containsKey(taskId)) listOf(taskId) else emptyList()
                } else {
                    emptyList()
                }
            }
            
            // target - depends on same target in same project
            else -> {
                val taskId = "${currentTask.project}:$depString"
                if (allTasks.containsKey(taskId)) listOf(taskId) else emptyList()
            }
        }
    }
    
    private fun resolveProjectDependencies(
        projectName: String,
        targetName: String,
        allTasks: Map<String, Task>
    ): List<String> {
        val projectDeps = projectGraph.getDependencies(projectName)
        return projectDeps.mapNotNull { dep ->
            val taskId = "${dep.target}:$targetName"
            if (allTasks.containsKey(taskId)) taskId else null
        }
    }
    
    fun buildAffectedTaskGraph(
        targetName: String,
        affectedProjects: Set<String>
    ): TaskGraph {
        logger.info("Building affected task graph for ${affectedProjects.size} affected projects")
        
        // Get all projects that might be affected (including dependents)
        val allAffectedProjects = mutableSetOf<String>()
        affectedProjects.forEach { project ->
            allAffectedProjects.add(project)
            allAffectedProjects.addAll(projectGraph.getTransitiveDependents(project))
        }
        
        return buildTaskGraph(targetName, allAffectedProjects)
    }
    
    fun buildTaskGraphForProjects(
        targetName: String,
        specificProjects: List<String>
    ): TaskGraph {
        logger.info("Building task graph for specific projects: ${specificProjects.joinToString()}")
        
        // Include dependencies of specified projects to ensure proper ordering
        val allRequiredProjects = mutableSetOf<String>()
        specificProjects.forEach { project ->
            allRequiredProjects.add(project)
            allRequiredProjects.addAll(projectGraph.getTransitiveDependencies(project))
        }
        
        return buildTaskGraph(targetName, allRequiredProjects)
    }
    
    private fun generateTaskHash(
        taskId: String,
        target: TargetConfiguration,
        projectConfig: ProjectConfiguration
    ): String {
        val hasher = MessageDigest.getInstance("SHA-256")
        
        // Hash task configuration
        hasher.update(taskId.toByteArray())
        hasher.update(target.toString().toByteArray())
        
        // Hash project configuration that affects this task
        hasher.update(projectConfig.name.toByteArray())
        hasher.update(projectConfig.root.toByteArray())
        hasher.update(projectConfig.tags.joinToString().toByteArray())
        
        // Hash dependencies
        target.dependsOn.forEach { dep ->
            hasher.update(dep.toByteArray())
        }
        
        // Hash inputs and outputs
        target.inputs.forEach { input ->
            hasher.update(input.toByteArray())
        }
        target.outputs.forEach { output ->
            hasher.update(output.toByteArray())
        }
        
        return Base64.getEncoder().encodeToString(hasher.digest())
    }
}

data class TaskGraphBuildOptions(
    val targetName: String,
    val projectFilter: ProjectFilter = ProjectFilter.All,
    val skipCache: Boolean = false,
    val parallel: Boolean = true,
    val maxParallel: Int = Runtime.getRuntime().availableProcessors()
)

sealed class ProjectFilter {
    object All : ProjectFilter()
    data class Specific(val projects: List<String>) : ProjectFilter()
    data class Affected(val base: String = "main") : ProjectFilter()
    data class WithTag(val tag: String) : ProjectFilter()
    data class OfType(val type: String) : ProjectFilter()
}