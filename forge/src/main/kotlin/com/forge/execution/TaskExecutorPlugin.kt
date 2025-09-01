package com.forge.execution

import com.forge.core.ProjectGraph
import com.forge.graph.Task
import com.forge.graph.TaskExecutionPlan
import com.forge.graph.TaskResult
import java.nio.file.Path

/**
 * Interface for pluggable task executors that handle task execution and caching
 */
interface TaskExecutorPlugin : AutoCloseable {
    
    /**
     * Execute a single task
     */
    fun executeTask(
        task: Task,
        projectGraph: ProjectGraph,
        workspaceRoot: Path,
        verbose: Boolean = false
    ): TaskResult
    
    /**
     * Execute a task execution plan
     */
    fun execute(
        executionPlan: TaskExecutionPlan,
        projectGraph: ProjectGraph,
        workspaceRoot: Path,
        verbose: Boolean = false
    ): ExecutionResults {
        val results = mutableMapOf<String, TaskResult>()
        val startTime = System.currentTimeMillis()
        
        for ((layerIndex, layer) in executionPlan.layers.withIndex()) {
            // Execute tasks in parallel within each layer
            val layerResults = layer.map { task ->
                executeTask(task, projectGraph, workspaceRoot, verbose)
            }
            
            // Add results to map
            layerResults.forEach { result ->
                results[result.task.id] = result
            }
            
            // Check if any task in this layer failed
            val failedTasks = layerResults.filter { !it.isSuccess }
            if (failedTasks.isNotEmpty()) {
                // Stop execution on failure
                break
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        val successCount = results.values.count { it.isSuccess }
        val failureCount = results.values.count { !it.isSuccess }
        
        return ExecutionResults(
            results = results,
            totalDuration = duration,
            successCount = successCount,
            failureCount = failureCount
        )
    }
    
    /**
     * Get the executor ID for configuration
     */
    fun getExecutorId(): String
    
    /**
     * Validate executor options
     */
    fun validateOptions(options: Map<String, Any>): Boolean = true
    
    /**
     * Initialize the executor with options
     */
    fun initialize(options: Map<String, Any>) {}
    
    override fun close() {
        // Default empty implementation
    }
}

/**
 * Configuration for task executors
 */
data class TaskExecutorConfig(
    val executor: String,
    val options: Map<String, Any> = emptyMap()
)

/**
 * Result of executing a shell process
 */
data class ProcessResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

/**
 * Results of executing multiple tasks
 */
data class ExecutionResults(
    val results: Map<String, com.forge.graph.TaskResult>,
    val totalDuration: Long,
    val successCount: Int,
    val failureCount: Int
) {
    val success: Boolean get() = failureCount == 0
}