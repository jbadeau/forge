package com.forge.execution

import com.forge.core.ProjectGraph
import com.forge.core.WorkspaceConfiguration
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Factory for creating task executors based on workspace configuration
 */
class ExecutorFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(ExecutorFactory::class.java)
        
        /**
         * Create an executor based on workspace configuration
         */
        fun createExecutor(
            workspaceRoot: Path,
            projectGraph: ProjectGraph,
            workspaceConfig: WorkspaceConfiguration? = null
        ): TaskExecutor {
            // Check if explicit task executor configuration is provided
            val taskExecutorConfig = workspaceConfig?.taskExecutor
            
            return if (taskExecutorConfig != null) {
                logger.info("Using configured task executor: ${taskExecutorConfig.executor}")
                val executorPlugin = TaskExecutorPluginFactory.createTaskExecutor(taskExecutorConfig)
                PluggableTaskExecutor(executorPlugin, projectGraph, workspaceRoot)
            } else {
                // Fallback to legacy remote execution logic for backward compatibility
                val remoteConfig = workspaceConfig?.getRemoteExecutionConfig()
                
                // Check if gRPC classes are available
                val isGrpcAvailable = try {
                    Class.forName("io.grpc.ManagedChannel")
                    true
                } catch (e: ClassNotFoundException) {
                    logger.info("gRPC not available - using Local Task Executor")
                    false
                }
                
                if (remoteConfig != null && workspaceConfig.isRemoteExecutionEnabled() && isGrpcAvailable) {
                    logger.info("Using Remote Task Executor with configured endpoint: ${remoteConfig.endpoint}")
                    val executorPlugin = TaskExecutorPluginFactory.createTaskExecutor(
                        "com.forge.executor.RemoteTaskExecutor",
                        mapOf(
                            "defaultEndpoint" to remoteConfig.endpoint,
                            "defaultInstanceName" to remoteConfig.instanceName,
                            "useTls" to remoteConfig.useTls,
                            "maxConnections" to remoteConfig.maxConnections,
                            "defaultTimeoutSeconds" to remoteConfig.timeoutSeconds,
                            "platform" to remoteConfig.platform
                        )
                    )
                    PluggableTaskExecutor(executorPlugin, projectGraph, workspaceRoot)
                } else {
                    logger.info("Using Local Task Executor")
                    val executorPlugin = TaskExecutorPluginFactory.createTaskExecutor("com.forge.executor.LocalTaskExecutor")
                    PluggableTaskExecutor(executorPlugin, projectGraph, workspaceRoot)
                }
            }
        }
    }
}

/**
 * Interface that task executors must implement
 */
interface TaskExecutor {
    fun execute(executionPlan: com.forge.graph.TaskExecutionPlan, verbose: Boolean = false): ExecutionResults
}