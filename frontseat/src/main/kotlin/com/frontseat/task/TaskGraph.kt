package com.frontseat.task

import com.frontseat.project.ProjectGraph
import com.frontseat.project.TargetConfiguration
import java.time.Instant

/**
 * Task graph representing dependencies between tasks across all projects.
 * This is the second layer in Moon's architecture:
 * 1. ProjectGraph - project dependencies
 * 2. TaskGraph - task dependencies (this file)
 * 3. ActionGraph - fine-grained action dependencies
 * 
 * The TaskGraph is built from the ProjectGraph and represents
 * executable tasks and their relationships.
 */

data class TaskId(val value: String) {
    companion object {
        fun create(project: String, target: String): TaskId {
            return TaskId("$project:$target")
        }
    }
}

data class Task(
    val id: TaskId,
    val project: String,
    val target: String,
    val configuration: TargetConfiguration,
    val dependencies: Set<TaskId> = emptySet(),
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    val cache: Boolean = true,
    val metadata: Map<String, Any> = emptyMap(),
    // Execution state
    val startTime: Instant? = null,
    val endTime: Instant? = null
) {
    val status: TaskStatus = when {
        startTime == null -> TaskStatus.PENDING
        endTime == null -> TaskStatus.RUNNING
        else -> TaskStatus.COMPLETED
    }
    
    fun getDuration(): Long? {
        return if (startTime != null && endTime != null) {
            endTime.toEpochMilli() - startTime.toEpochMilli()
        } else null
    }
    
    fun isCompleted(): Boolean = status == TaskStatus.COMPLETED
    fun isRunning(): Boolean = status == TaskStatus.RUNNING
    fun isPending(): Boolean = status == TaskStatus.PENDING
    fun isCacheable(): Boolean = configuration.isCacheable()
    fun canRunInParallel(): Boolean = configuration.canRunInParallel()
    
    override fun toString(): String = id.value
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CACHED
}

class TaskGraph {
    private val tasks = mutableMapOf<TaskId, Task>()
    private val edges = mutableMapOf<TaskId, MutableSet<TaskId>>()
    
    fun addTask(task: Task) {
        tasks[task.id] = task
        edges.putIfAbsent(task.id, mutableSetOf())
        
        // Add edges from dependencies
        task.dependencies.forEach { depId ->
            edges.computeIfAbsent(depId) { mutableSetOf() }.add(task.id)
        }
    }
    
    fun getTask(id: TaskId): Task? = tasks[id]
    fun getTask(taskId: String): Task? = tasks[TaskId(taskId)]
    
    fun getAllTasks(): Collection<Task> = tasks.values
    
    fun getSuccessors(id: TaskId): Set<TaskId> = edges[id] ?: emptySet()
    
    fun getDependencies(id: TaskId): Set<TaskId> = tasks[id]?.dependencies ?: emptySet()
    fun getDependencies(taskId: String): List<String> = 
        getDependencies(TaskId(taskId)).map { it.value }
    
    fun getDependents(taskId: String): List<String> {
        return edges.entries
            .filter { (_, successors) -> successors.any { it.value == taskId } }
            .map { it.key.value }
    }
    
    fun getTasksByProject(projectName: String): List<Task> = 
        tasks.values.filter { it.project == projectName }
    
    fun getTasksByTarget(targetName: String): List<Task> = 
        tasks.values.filter { it.target == targetName }
    
    fun getRootTasks(): List<Task> {
        return tasks.values.filter { it.dependencies.isEmpty() }
    }
    
    fun getLeafTasks(): List<Task> {
        return tasks.values.filter { task ->
            edges[task.id]?.isEmpty() ?: true
        }
    }
    
    fun hasTask(taskId: String): Boolean = tasks.containsKey(TaskId(taskId))
    fun isEmpty(): Boolean = tasks.isEmpty()
    fun size(): Int = tasks.size
    
    /**
     * Get execution layers (tasks that can run in parallel)
     */
    fun getTopologicalOrder(): List<Task> {
        val result = mutableListOf<Task>()
        val visited = mutableSetOf<TaskId>()
        val visiting = mutableSetOf<TaskId>()
        
        fun visit(taskId: TaskId) {
            if (taskId in visited) return
            if (taskId in visiting) {
                throw IllegalStateException("Circular dependency detected at task: ${taskId.value}")
            }
            
            visiting.add(taskId)
            val task = tasks[taskId] ?: return
            task.dependencies.forEach { depId ->
                visit(depId)
            }
            visiting.remove(taskId)
            visited.add(taskId)
            result.add(task)
        }
        
        tasks.keys.forEach { taskId ->
            visit(taskId)
        }
        
        return result
    }
    
    /**
     * Get tasks organized in layers for parallel execution
     */
    fun getExecutionLayers(): List<List<Task>> {
        val layers = mutableListOf<List<Task>>()
        val executed = mutableSetOf<TaskId>()
        val remaining = tasks.keys.toMutableSet()
        
        while (remaining.isNotEmpty()) {
            val layer = remaining.filter { id ->
                val task = tasks[id]!!
                task.dependencies.all { it in executed }
            }.mapNotNull { tasks[it] }
            
            if (layer.isEmpty()) {
                throw IllegalStateException("Circular dependency detected in task graph")
            }
            
            layers.add(layer)
            layer.forEach { 
                executed.add(it.id)
                remaining.remove(it.id)
            }
        }
        
        return layers
    }
    
    fun getExecutionPlan(): TaskExecutionPlan {
        val layers = getExecutionLayers()
        return TaskExecutionPlan(layers)
    }
    
    /**
     * Get execution plan for a subset of tasks
     */
    fun getExecutionPlan(targetTasks: Set<Task>): TaskExecutionPlan {
        // Build subgraph containing only target tasks and their dependencies
        val requiredTaskIds = mutableSetOf<TaskId>()
        
        // Add target tasks and collect all their dependencies recursively
        fun collectDependencies(taskId: TaskId) {
            if (requiredTaskIds.contains(taskId)) return
            requiredTaskIds.add(taskId)
            
            val task = tasks[taskId] ?: return
            task.dependencies.forEach { depId ->
                collectDependencies(depId)
            }
        }
        
        targetTasks.forEach { task ->
            collectDependencies(task.id)
        }
        
        // Build layers from the required tasks
        val filteredLayers = mutableListOf<List<Task>>()
        val processed = mutableSetOf<TaskId>()
        
        while (processed.size < requiredTaskIds.size) {
            val ready = requiredTaskIds
                .filter { taskId ->
                    !processed.contains(taskId) && 
                    tasks[taskId]?.dependencies?.all { processed.contains(it) } == true
                }
                .mapNotNull { tasks[it] }
            
            if (ready.isEmpty()) {
                val remaining = requiredTaskIds - processed
                throw IllegalStateException("Circular dependency detected in task subgraph. Remaining tasks: $remaining")
            }
            
            filteredLayers.add(ready)
            processed.addAll(ready.map { it.id })
        }
        
        return TaskExecutionPlan(filteredLayers)
    }
    
    // Legacy compatibility methods
    fun topologicalSort(): List<List<String>> {
        return getExecutionLayers().map { layer ->
            layer.map { it.id.value }
        }
    }
    
    /**
     * Build task graph from project graph and selected targets
     */
    companion object {
        fun fromProjectGraph(
            projectGraph: ProjectGraph,
            targets: Map<String, Set<String>> // project -> set of targets
        ): TaskGraph {
            val taskGraph = TaskGraph()
            
            // Create tasks for each project:target combination
            targets.forEach { (projectName, targetNames) ->
                val project = projectGraph.getProject(projectName)
                    ?: throw IllegalArgumentException("Project not found: $projectName")
                
                targetNames.forEach { targetName ->
                    val targetConfig = project.data.targets[targetName]
                        ?: throw IllegalArgumentException("Target $targetName not found in project $projectName")
                    
                    val taskId = TaskId.create(projectName, targetName)
                    
                    // Collect dependencies from:
                    // 1. Task's explicit dependencies
                    // 2. Project dependencies (if target exists in dependent projects)
                    val taskDependencies = mutableSetOf<TaskId>()
                    
                    // Add explicit task dependencies
                    targetConfig.dependsOn?.forEach { depTarget ->
                        if (depTarget.contains(":")) {
                            // Cross-project dependency
                            val (depProject, depTargetName) = depTarget.split(":", limit = 2)
                            taskDependencies.add(TaskId.create(depProject, depTargetName))
                        } else {
                            // Same project dependency
                            taskDependencies.add(TaskId.create(projectName, depTarget))
                        }
                    }
                    
                    // Add implicit dependencies from project graph
                    projectGraph.getDependencies(projectName).forEach { projectDep ->
                        val depProject = projectDep.target
                        // If the same target exists in the dependent project, add it as a dependency
                        if (targets[depProject]?.contains(targetName) == true) {
                            taskDependencies.add(TaskId.create(depProject, targetName))
                        }
                    }
                    
                    val task = Task(
                        id = taskId,
                        project = projectName,
                        target = targetName,
                        configuration = targetConfig,
                        dependencies = taskDependencies,
                        inputs = targetConfig.inputs ?: emptyList(),
                        outputs = targetConfig.outputs ?: emptyList(),
                        cache = targetConfig.isCacheable(),
                        metadata = emptyMap()
                    )
                    
                    taskGraph.addTask(task)
                }
            }
            
            return taskGraph
        }
    }
}

data class TaskExecutionPlan(
    val layers: List<List<Task>>
) {
    val totalTasks: Int = layers.sumOf { it.size }
    
    val maxParallelism: Int = layers.maxOfOrNull { it.size } ?: 0
    
    fun getTasksAtLayer(layer: Int): List<Task> = 
        layers.getOrNull(layer) ?: emptyList()
    
    fun getLayerCount(): Int = layers.size
    
    fun getAllTasks(): List<Task> = layers.flatten()
    
    fun isEmpty(): Boolean = layers.isEmpty() || layers.all { it.isEmpty() }
}

data class TaskResult(
    val task: Task,
    val status: TaskStatus,
    val startTime: Instant,
    val endTime: Instant,
    val output: String = "",
    val error: String = "",
    val exitCode: Int = 0,
    val fromCache: Boolean = false
) {
    val duration: Long = endTime.toEpochMilli() - startTime.toEpochMilli()
    
    val isSuccess: Boolean = status == TaskStatus.COMPLETED || status == TaskStatus.CACHED
    
    val isFailure: Boolean = status == TaskStatus.FAILED
    
    fun wasSkipped(): Boolean = status == TaskStatus.SKIPPED
    
    fun wasCached(): Boolean = status == TaskStatus.CACHED || fromCache
}

data class ExecutionResults(
    val results: Map<String, TaskResult>,
    val totalDuration: Long,
    val successCount: Int,
    val failureCount: Int
) {
    val totalTasks: Int = results.size
    val isSuccess: Boolean = failureCount == 0
    val isPartialSuccess: Boolean = successCount > 0 && failureCount > 0
    val isCompleteFailure: Boolean = successCount == 0 && failureCount > 0
}