package com.forge.execution.remote

import com.forge.core.ProjectGraph
import com.forge.execution.ExecutionResults
import com.forge.execution.TaskExecutorPlugin
import com.forge.graph.Task
import com.forge.graph.TaskResult
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Remote task executor plugin that uses Remote Execution API (BuildBarn compatible)
 */
class RemoteTaskExecutorPlugin : TaskExecutorPlugin {
    
    private val logger = LoggerFactory.getLogger(RemoteTaskExecutorPlugin::class.java)
    private var remoteExecutor: RemoteExecutionExecutor? = null
    
    override fun getExecutorId(): String = "com.forge.executor.RemoteTaskExecutor"
    
    override fun validateOptions(options: Map<String, Any>): Boolean {
        // Validate required options for remote execution
        val endpoint = options["defaultEndpoint"] as? String
        if (endpoint.isNullOrBlank()) {
            logger.error("Remote task executor requires 'defaultEndpoint' option")
            return false
        }
        return true
    }
    
    override fun initialize(options: Map<String, Any>) {
        val config = RemoteExecutionConfig(
            endpoint = options["defaultEndpoint"] as? String ?: "127.0.0.1:8980",
            instanceName = options["defaultInstanceName"] as? String ?: "",
            useTls = options["useTls"] as? Boolean ?: false,
            maxConnections = options["maxConnections"] as? Int ?: 100,
            timeoutSeconds = (options["defaultTimeoutSeconds"] as? Number)?.toLong() ?: 300L,
            platform = (options["platform"] as? Map<String, String>) ?: emptyMap()
        )
        
        logger.info("Initializing Remote Task Executor with endpoint: ${config.endpoint}")
        // remoteExecutor will be created per-execution to ensure proper workspace/project context
    }
    
    override fun executeTask(
        task: Task,
        projectGraph: ProjectGraph,
        workspaceRoot: Path,
        verbose: Boolean
    ): TaskResult {
        // For single task execution, create a simple execution plan and use the bulk execute method
        val singleTaskPlan = com.forge.graph.TaskExecutionPlan(listOf(listOf(task)))
        val results = execute(singleTaskPlan, projectGraph, workspaceRoot, verbose)
        return results.results[task.id] ?: throw RuntimeException("Task result not found: ${task.id}")
    }
    
    override fun execute(
        executionPlan: com.forge.graph.TaskExecutionPlan,
        projectGraph: ProjectGraph,
        workspaceRoot: Path,
        verbose: Boolean
    ): ExecutionResults {
        val executor = getOrCreateExecutor(projectGraph, workspaceRoot)
        return executor.execute(executionPlan, verbose, skipCache = false)
    }
    
    private fun getOrCreateExecutor(projectGraph: ProjectGraph, workspaceRoot: Path): RemoteExecutionExecutor {
        return remoteExecutor ?: run {
            // Default configuration if not initialized
            val defaultConfig = RemoteExecutionConfig(
                endpoint = "127.0.0.1:8980",
                instanceName = "",
                useTls = false,
                maxConnections = 100,
                timeoutSeconds = 300L,
                platform = emptyMap()
            )
            
            val executor = RemoteExecutionExecutor(workspaceRoot, projectGraph, defaultConfig)
            remoteExecutor = executor
            executor
        }
    }
    
    override fun close() {
        remoteExecutor?.close()
        remoteExecutor = null
    }
}