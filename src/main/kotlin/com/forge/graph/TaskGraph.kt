package com.forge.graph

import com.forge.core.TargetConfiguration
import java.time.Instant

data class TaskGraph(
    val tasks: Map<String, Task>,
    val dependencies: Map<String, List<String>>,
    val roots: List<String>,
    val continuousDependencies: Map<String, List<String>> = emptyMap()
) {
    fun getTask(taskId: String): Task? = tasks[taskId]
    
    fun getDependencies(taskId: String): List<String> = dependencies[taskId] ?: emptyList()
    
    fun getDependents(taskId: String): List<String> {
        return dependencies.entries
            .filter { (_, deps) -> deps.contains(taskId) }
            .map { it.key }
    }
    
    fun getAllTasks(): List<Task> = tasks.values.toList()
    
    fun getTasksByProject(projectName: String): List<Task> = 
        tasks.values.filter { it.projectName == projectName }
    
    fun getTasksByTarget(targetName: String): List<Task> = 
        tasks.values.filter { it.targetName == targetName }
    
    fun getRootTasks(): List<Task> = roots.mapNotNull { tasks[it] }
    
    fun hasTask(taskId: String): Boolean = tasks.containsKey(taskId)
    
    fun isEmpty(): Boolean = tasks.isEmpty()
    
    fun size(): Int = tasks.size
    
    fun topologicalSort(): List<List<String>> {
        val executionLayers = mutableListOf<List<String>>()
        val inDegree = dependencies.mapValues { it.value.size }.toMutableMap()
        val completed = mutableSetOf<String>()
        
        while (completed.size < tasks.size) {
            val ready = inDegree.entries
                .filter { it.value == 0 && !completed.contains(it.key) }
                .map { it.key }
            
            if (ready.isEmpty()) {
                throw IllegalStateException("Circular dependency detected in task graph")
            }
            
            executionLayers.add(ready)
            completed.addAll(ready)
            
            ready.forEach { taskId ->
                dependencies.forEach { (dependentId, deps) ->
                    if (deps.contains(taskId)) {
                        inDegree[dependentId] = inDegree[dependentId]!! - 1
                    }
                }
            }
        }
        
        return executionLayers
    }
    
    fun getExecutionPlan(): TaskExecutionPlan {
        val layers = topologicalSort()
        val layerTasks = layers.map { layer -> 
            layer.mapNotNull { taskId -> tasks[taskId] } 
        }
        return TaskExecutionPlan(layerTasks)
    }
}

data class Task(
    val id: String,
    val projectName: String,
    val targetName: String,
    val target: TargetConfiguration,
    val hash: String? = null,
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
    
    fun isCacheable(): Boolean = target.isCacheable()
    
    fun canRunInParallel(): Boolean = target.canRunInParallel()
    
    override fun toString(): String = id
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CACHED
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