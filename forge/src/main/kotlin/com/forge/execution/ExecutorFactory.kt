package com.forge.execution

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.core.ProjectGraph
import com.forge.core.WorkspaceConfiguration
import com.forge.execution.remote.RemoteExecutionConfig
import com.forge.execution.remote.RemoteExecutionExecutor
import com.forge.graph.TaskExecutionPlan
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

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
            // Check if explicit workspace config is provided and has remote execution enabled
            val remoteConfig = workspaceConfig?.getRemoteExecutionConfig()
            
            // Check if gRPC classes are available (they won't be in native build without gRPC)
            val isGrpcAvailable = try {
                Class.forName("io.grpc.ManagedChannel")
                true
            } catch (e: ClassNotFoundException) {
                logger.info("gRPC not available - Remote Execution disabled")
                false
            }
            
            return if (remoteConfig != null && workspaceConfig.isRemoteExecutionEnabled() && isGrpcAvailable) {
                logger.info("Using Remote Execution with configured endpoint: ${remoteConfig.endpoint}")
                UnifiedTaskExecutor(
                    RemoteExecutionExecutor(workspaceRoot, projectGraph, remoteConfig)
                )
            } else if (remoteConfig != null && isGrpcAvailable) {
                logger.info("Using RemoteExecutionExecutor in local mode (endpoint: ${remoteConfig.endpoint})")
                // Use RemoteExecutionExecutor even for "local" mode to maintain unified caching
                UnifiedTaskExecutor(
                    RemoteExecutionExecutor(workspaceRoot, projectGraph, remoteConfig)
                )
            } else {
                logger.info("Using Local Execution with default Remote Execution fallback")
                // Default fallback configuration for testing
                val defaultConfig = RemoteExecutionConfig(
                    endpoint = "localhost:8080",
                    instanceName = "",
                    useTls = false,
                    maxConnections = 10,
                    timeoutSeconds = 300,
                    platform = emptyMap()
                )
                UnifiedTaskExecutor(
                    RemoteExecutionExecutor(workspaceRoot, projectGraph, defaultConfig)
                )
            }
        }
    }
}

/**
 * Interface that both old TaskExecutor and new RemoteExecutionExecutor can implement
 */
interface TaskExecutor {
    fun execute(executionPlan: TaskExecutionPlan, verbose: Boolean = false): ExecutionResults
}

/**
 * Adapter that wraps RemoteExecutionExecutor to implement the TaskExecutor interface
 */
class UnifiedTaskExecutor(
    private val remoteExecutor: RemoteExecutionExecutor
) : TaskExecutor, AutoCloseable {
    
    override fun execute(executionPlan: TaskExecutionPlan, verbose: Boolean): ExecutionResults {
        // RemoteExecutionExecutor.execute has an additional skipCache parameter
        return remoteExecutor.execute(executionPlan, verbose, skipCache = false)
    }
    
    override fun close() {
        remoteExecutor.close()
    }
}