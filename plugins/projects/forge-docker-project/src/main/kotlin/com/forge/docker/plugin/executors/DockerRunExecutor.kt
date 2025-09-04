package com.forge.docker.plugin.executors

import com.forge.plugin.api.*
import com.forge.docker.devkit.DockerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Docker run executor for running containers with flexible configuration
 */
class DockerRunExecutor : Executor {
    
    private val logger = LoggerFactory.getLogger(DockerRunExecutor::class.java)
    
    override val metadata = ExecutorMetadata(
        id = "com.forge.docker.executor.run",
        name = "Docker Run Executor",
        version = "1.0.0",
        description = "Run Docker containers with port mapping, volumes, and environment variables"
    )
    
    override fun canExecute(action: ActionNode): Boolean {
        return action.type.name == "RUN" && action.options.containsKey("image")
    }
    
    override suspend fun execute(
        action: ActionNode,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        io.log(LogLevel.INFO, "🏃 Running Docker container...")
        
        try {
            val options = parseOptions(action.options)
            
            // Generate run command with DockerUtils
            val runCommand = DockerUtils.generateRunCommand(
                imageRef = options.image,
                ports = options.ports,
                volumes = options.volumes,
                envVars = options.environment,
                detached = options.detached,
                removeOnExit = options.removeOnExit
            ).toMutableList()
            
            // Add custom command if specified
            options.command?.let { cmd ->
                runCommand.addAll(cmd.split(" "))
            }
            
            io.log(LogLevel.INFO, "Running with command: ${runCommand.joinToString(" ")}")
            
            // Execute run command
            val process = ProcessBuilder()
                .command(runCommand)
                .directory(File(context.workspaceRoot))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            
            if (options.detached) {
                // For detached mode, just check that container started
                Thread.sleep(2000) // Give container time to start
                
                val containerId = process.inputStream.bufferedReader().readLine()
                io.log(LogLevel.INFO, "✅ Container started in detached mode: $containerId")
                
                ExecutionResult.success(mapOf(
                    "containerId" to (containerId ?: "unknown"),
                    "image" to options.image,
                    "detached" to true
                ))
            } else {
                // For attached mode, stream output until completion
                val outputThread = Thread {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            io.log(LogLevel.INFO, line)
                        }
                    }
                }
                
                val errorThread = Thread {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            io.log(LogLevel.ERROR, line)
                        }
                    }
                }
                
                outputThread.start()
                errorThread.start()
                
                val exitCode = process.waitFor()
                outputThread.join()
                errorThread.join()
                
                if (exitCode == 0) {
                    io.log(LogLevel.INFO, "✅ Container completed successfully")
                    ExecutionResult.success(mapOf(
                        "image" to options.image,
                        "exitCode" to exitCode
                    ))
                } else {
                    ExecutionResult.failure(exitCode, "Container exited with code $exitCode")
                }
            }
            
        } catch (e: Exception) {
            logger.error("Docker run failed", e)
            ExecutionResult.failure(1, "Docker run error: ${e.message}")
        }
    }
    
    private fun parseOptions(options: Map<String, Any>): DockerRunOptions {
        return DockerRunOptions(
            image = options["image"] as? String ?: throw IllegalArgumentException("image is required"),
            ports = (options["ports"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
            volumes = (options["volumes"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
            environment = (options["environment"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
            detached = options["detached"] as? Boolean ?: false,
            removeOnExit = options["removeOnExit"] as? Boolean ?: true,
            command = options["command"] as? String
        )
    }
}

/**
 * Options for Docker run operation
 */
private data class DockerRunOptions(
    val image: String,
    val ports: Map<String, String>,
    val volumes: Map<String, String>,
    val environment: Map<String, String>,
    val detached: Boolean,
    val removeOnExit: Boolean,
    val command: String?
)