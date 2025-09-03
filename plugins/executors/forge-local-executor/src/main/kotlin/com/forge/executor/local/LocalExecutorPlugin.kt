package com.forge.executor.local

import com.forge.actions.ActionNode
import com.forge.actions.ActionType
import com.forge.plugin.api.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Local executor plugin that runs tasks on the local machine
 */
class LocalExecutorPlugin : ExecutorPlugin {
    override val metadata = ExecutorPluginMetadata(
        id = "forge.executor.local",
        name = "Local Executor",
        version = "1.0.0",
        description = "Executes tasks on the local machine",
        supportedPlatforms = setOf(Platform.LINUX, Platform.MACOS, Platform.WINDOWS),
        author = "Forge Team",
        tags = listOf("local", "execution")
    )
    
    private val logger = LoggerFactory.getLogger(LocalExecutorPlugin::class.java)
    private val resourceHandles = ConcurrentHashMap<String, LocalResourceHandle>()
    private var config = LocalExecutorConfig()
    private var isShutdown = false
    
    override fun initialize(config: Map<String, Any>) {
        this.config = LocalExecutorConfig.from(config)
        logger.info("Local executor initialized with max parallelism: ${this.config.maxParallelism}")
    }
    
    override fun canExecute(action: ActionNode): Boolean {
        return when (action.type) {
            ActionType.RUN_TASK -> true
            else -> false
        }
    }
    
    override suspend fun execute(
        action: ActionNode,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        logger.debug("Executing action: ${action.id} (${action.type})")
        
        val command = action.inputs["command"] as? String
            ?: return@withContext ExecutionResult.failure(1, "No command specified")
            
        val workingDir = action.inputs["cwd"] as? String ?: context.workspaceRoot
        val env = (action.inputs["env"] as? Map<String, String>) ?: emptyMap()
        val timeout = (action.inputs["timeout"] as? Number)?.toLong() ?: 300L
        
        try {
            executeNormalTask(command, workingDir, env, timeout, io)
        } catch (e: Exception) {
            logger.error("Error executing action: ${action.id}", e)
            ExecutionResult.failure(1, e.message ?: "Unknown error")
        }
    }
    
    private suspend fun executeNormalTask(
        command: String,
        workingDir: String,
        env: Map<String, String>,
        timeout: Long,
        io: ExecutionIO
    ): ExecutionResult {
        val process = createProcess(command, workingDir, env)
        
        // Handle process I/O
        val outputJob = CoroutineScope(Dispatchers.IO).launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    io.stdout(line + "\n")
                }
            }
        }
        
        val errorJob = CoroutineScope(Dispatchers.IO).launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    io.stderr(line + "\n")
                }
            }
        }
        
        // Wait for completion with timeout
        val completed = withTimeoutOrNull(timeout * 1000) {
            process.waitFor()
        }
        
        if (completed == null) {
            // Timeout occurred
            process.destroyForcibly()
            outputJob.cancel()
            errorJob.cancel()
            return ExecutionResult.failure(124, "Command timed out after ${timeout}s")
        }
        
        // Wait for I/O to complete
        outputJob.join()
        errorJob.join()
        
        return ExecutionResult(
            exitCode = process.exitValue(),
            outputs = mapOf(
                "executionTime" to System.currentTimeMillis()
            )
        )
    }
    
    private fun createProcess(
        command: String,
        workingDir: String,
        env: Map<String, String>
    ): Process {
        val processBuilder = ProcessBuilder()
            .directory(File(workingDir))
            .command(parseCommand(command))
        
        // Set environment
        val processEnv = processBuilder.environment()
        env.forEach { (key, value) ->
            processEnv[key] = value
        }
        
        return processBuilder.start()
    }
    
    private fun parseCommand(command: String): List<String> {
        return when {
            System.getProperty("os.name").lowercase().contains("windows") -> {
                listOf("cmd", "/c", command)
            }
            else -> {
                listOf("/bin/sh", "-c", command)
            }
        }
    }
    
    override fun acquireResources(requirements: ResourceRequirements): ResourceHandle? {
        if (resourceHandles.size >= config.maxParallelism) {
            return null // No resources available
        }
        
        val handle = LocalResourceHandle(
            id = "local-${System.currentTimeMillis()}-${resourceHandles.size}",
            resources = requirements
        )
        
        resourceHandles[handle.id] = handle
        return handle
    }
    
    override fun releaseResources(handle: ResourceHandle) {
        resourceHandles.remove(handle.id)
    }
    
    override suspend fun healthCheck(): HealthStatus {
        return if (isShutdown) {
            HealthStatus.UNHEALTHY
        } else {
            HealthStatus.HEALTHY
        }
    }
}

/**
 * Configuration for local executor
 */
data class LocalExecutorConfig(
    val maxParallelism: Int = Runtime.getRuntime().availableProcessors(),
    val defaultTimeout: Long = 300L,
    val preserveEnv: Boolean = true
) {
    companion object {
        fun from(config: Map<String, Any>): LocalExecutorConfig {
            return LocalExecutorConfig(
                maxParallelism = (config["maxParallelism"] as? Number)?.toInt()
                    ?: Runtime.getRuntime().availableProcessors(),
                defaultTimeout = (config["defaultTimeout"] as? Number)?.toLong() ?: 300L,
                preserveEnv = (config["preserveEnv"] as? Boolean) ?: true
            )
        }
    }
}

/**
 * Local resource handle
 */
private class LocalResourceHandle(
    override val id: String,
    override val resources: ResourceRequirements
) : ResourceHandle {
    override fun release() {
        // Nothing to release for local execution
    }
}