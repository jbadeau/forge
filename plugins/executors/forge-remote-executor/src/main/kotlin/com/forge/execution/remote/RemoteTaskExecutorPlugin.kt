package com.forge.execution.remote

import com.forge.actions.ActionNode
import com.forge.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Remote task executor plugin that uses Remote Execution API (BuildBarn compatible)
 */
class RemoteTaskExecutorPlugin : ExecutorPlugin {
    override val metadata = ExecutorPluginMetadata(
        id = "forge.executor.remote",
        name = "Remote Executor",
        version = "1.0.0",
        description = "Executes tasks using Remote Execution API (BuildBarn compatible)",
        supportedPlatforms = setOf(Platform.LINUX, Platform.MACOS),
        author = "Forge Team",
        tags = listOf("remote", "execution", "buildbarn")
    )
    
    private val logger = LoggerFactory.getLogger(RemoteTaskExecutorPlugin::class.java)
    private var config = RemoteExecutionConfig()
    
    override fun initialize(config: Map<String, Any>) {
        this.config = RemoteExecutionConfig.from(config)
        logger.info("Remote executor initialized with endpoint: ${this.config.endpoint}")
    }
    
    override fun canExecute(action: ActionNode): Boolean {
        return when (action.type.name) {
            "RUN_TASK" -> true
            else -> false
        }
    }
    
    override suspend fun execute(
        action: ActionNode,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val command = action.inputs["command"] as? String
                ?: return@withContext ExecutionResult.failure(1, "No command specified")
            
            val workingDir = action.inputs["cwd"] as? String ?: context.workspaceRoot
            val env = when (val e = action.inputs["env"]) {
                is Map<*, *> -> e.entries.associate { (k, v) -> k.toString() to v.toString() }
                else -> emptyMap<String, String>()
            }
            
            // Execute via remote execution
            val result = executeRemotely(command, workingDir, env, io)
            
            ExecutionResult(
                exitCode = result.exitCode,
                outputs = result.outputs,
                duration = result.duration
            )
        } catch (e: Exception) {
            logger.error("Error executing remote action: ${action.id}", e)
            ExecutionResult.failure(1, e.message ?: "Unknown remote execution error")
        }
    }
    
    private suspend fun executeRemotely(
        command: String,
        workingDir: String,
        env: Map<String, String>,
        io: ExecutionIO
    ): RemoteExecutionResult {
        // Placeholder for actual remote execution implementation
        // This would integrate with BuildBarn or similar remote execution service
        io.log(LogLevel.INFO, "Executing remotely: $command")
        
        return RemoteExecutionResult(
            exitCode = 0,
            outputs = mapOf("remote" to true),
            duration = 1000L
        )
    }
    
    override suspend fun healthCheck(): HealthStatus {
        return try {
            // Check connection to remote executor
            HealthStatus.HEALTHY
        } catch (e: Exception) {
            logger.error("Remote executor health check failed", e)
            HealthStatus.UNHEALTHY
        }
    }
    
    override fun acquireResources(requirements: ResourceRequirements): ResourceHandle? {
        // Remote executor doesn't manage local resources
        return null
    }
    
    override fun releaseResources(handle: ResourceHandle) {
        // No-op for remote executor
    }
    
    override fun getCapabilities(): ExecutorCapabilities {
        return ExecutorCapabilities(
            supportsParallel = true,
            supportsInteractive = false,
            supportsPersistent = false,
            supportsRemote = true,
            supportsCaching = true,
            supportsStreaming = true
        )
    }
}

/**
 * Configuration for remote execution
 */
data class RemoteExecutionConfig(
    val endpoint: String = "127.0.0.1:8980",
    val instanceName: String = "",
    val useTls: Boolean = false,
    val maxConnections: Int = 100,
    val timeoutSeconds: Long = 300L,
    val platform: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(config: Map<String, Any>): RemoteExecutionConfig {
            return RemoteExecutionConfig(
                endpoint = (config["defaultEndpoint"] as? String) ?: "127.0.0.1:8980",
                instanceName = (config["defaultInstanceName"] as? String) ?: "",
                useTls = (config["useTls"] as? Boolean) ?: false,
                maxConnections = (config["maxConnections"] as? Number)?.toInt() ?: 100,
                timeoutSeconds = (config["defaultTimeoutSeconds"] as? Number)?.toLong() ?: 300L,
                platform = when (val p = config["platform"]) {
                    is Map<*, *> -> p.entries.associate { (k, v) -> k.toString() to v.toString() }
                    else -> emptyMap<String, String>()
                }
            )
        }
    }
}

/**
 * Result of remote execution
 */
data class RemoteExecutionResult(
    val exitCode: Int,
    val outputs: Map<String, Any> = emptyMap(),
    val duration: Long? = null
)