package com.frontseat.plugin.api

import com.frontseat.actions.ActionNode
import kotlinx.coroutines.flow.Flow

/**
 * Metadata describing an Executor plugin
 */
data class ExecutorPluginMetadata(
    val id: String,                         // "com.frontseat.executor.local"
    val name: String,                       // "Local Executor"
    val version: String,                    // "1.2.0"
    val description: String,                // "Executes tasks on the local machine"
    val supportedPlatforms: Set<Platform>,  // [Platform.LINUX, Platform.MACOS]
    val supportedActionTypes: Set<String> = emptySet(),
    val author: String = "",                // "Forge Team"
    val homepage: String = "",              // "https://github.com/frontseat/executor-local"
    val tags: List<String> = emptyList()    // ["local", "execution"]
)

/**
 * Interface for task execution implementations
 */
interface Executor {
    /**
     * Plugin metadata
     */
    val metadata: ExecutorPluginMetadata
    
    /**
     * Initialize the executor with configuration
     */
    fun initialize(config: Map<String, Any>) {}
    
    /**
     * Check if this executor can handle the given action
     */
    fun canExecute(action: ActionNode): Boolean
    
    /**
     * Execute an action
     */
    suspend fun execute(
        action: ActionNode,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult
    
    /**
     * Prepare execution environment (called before execute)
     */
    suspend fun prepare(
        action: ActionNode,
        context: ExecutionContext
    ): PrepareResult = PrepareResult.success()
    
    /**
     * Cleanup after execution (called after execute)
     */
    suspend fun cleanup(
        action: ActionNode,
        context: ExecutionContext,
        result: ExecutionResult
    ) {}
    
    /**
     * Resource management
     */
    fun acquireResources(requirements: ResourceRequirements): ResourceHandle?
    fun releaseResources(handle: ResourceHandle)
    
    /**
     * Health check
     */
    suspend fun healthCheck(): HealthStatus
    
    /**
     * Shutdown the executor
     */
    fun shutdown() {}
    
    /**
     * Get executor capabilities
     */
    fun getCapabilities(): ExecutorCapabilities = ExecutorCapabilities()
}

/**
 * Platform information
 */
enum class Platform {
    LINUX,
    MACOS,
    WINDOWS,
    ANY
}

/**
 * I/O handlers for execution
 */
interface ExecutionIO {
    /**
     * Write to stdout
     */
    suspend fun stdout(data: String)
    suspend fun stdout(data: ByteArray)
    
    /**
     * Write to stderr
     */
    suspend fun stderr(data: String)
    suspend fun stderr(data: ByteArray)
    
    /**
     * Read from stdin
     */
    suspend fun stdin(): Flow<ByteArray>
    
    /**
     * Report progress
     */
    suspend fun progress(percentage: Int, message: String? = null)
    
    /**
     * Log a message
     */
    suspend fun log(level: LogLevel, message: String)
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Result of execution
 */
data class ExecutionResult(
    val exitCode: Int,
    val outputs: Map<String, Any> = emptyMap(),
    val cached: Boolean = false,
    val duration: Long? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    val success: Boolean get() = exitCode == 0
    
    companion object {
        fun success(outputs: Map<String, Any> = emptyMap()) = 
            ExecutionResult(0, outputs)
        
        fun failure(exitCode: Int, message: String? = null) =
            ExecutionResult(
                exitCode, 
                if (message != null) mapOf("error" to message) else emptyMap()
            )
        
        fun cached(outputs: Map<String, Any> = emptyMap()) =
            ExecutionResult(0, outputs, cached = true)
    }
}

/**
 * Result of preparation phase
 */
data class PrepareResult(
    val success: Boolean,
    val message: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success() = PrepareResult(true)
        fun failure(message: String) = PrepareResult(false, message)
    }
}

/**
 * Handle to acquired resources
 */
interface ResourceHandle {
    val id: String
    val resources: ResourceRequirements
    fun release()
}

/**
 * Executor capabilities
 */
data class ExecutorCapabilities(
    val supportsParallel: Boolean = true,
    val supportsInteractive: Boolean = false,
    val supportsPersistent: Boolean = false,
    val supportsRemote: Boolean = false,
    val supportsCaching: Boolean = true,
    val supportsStreaming: Boolean = true,
    val maxParallelism: Int? = null,
    val supportedActionTypes: Set<String>? = null
)