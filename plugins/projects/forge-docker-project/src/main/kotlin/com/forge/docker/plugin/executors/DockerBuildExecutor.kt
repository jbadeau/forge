package com.forge.docker.plugin.executors

import com.forge.plugin.api.*
import com.forge.docker.devkit.DockerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Docker build executor that builds Docker images with advanced options
 */
class DockerBuildExecutor : Executor {
    
    private val logger = LoggerFactory.getLogger(DockerBuildExecutor::class.java)
    
    override val metadata = ExecutorMetadata(
        id = "com.forge.docker.executor.build",
        name = "Docker Build Executor",
        version = "1.0.0",
        description = "Build Docker images with support for multi-stage builds, build args, and platforms"
    )
    
    override fun canExecute(action: ActionNode): Boolean {
        return action.type.name == "BUILD" && action.options.containsKey("dockerfile")
    }
    
    override suspend fun execute(
        action: ActionNode,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        io.log(LogLevel.INFO, "🐳 Building Docker image...")
        
        try {
            val options = parseOptions(action.options)
            
            // Validate inputs
            validateOptions(options, context.workspaceRoot)
            
            // Generate build command
            val buildCommand = DockerUtils.generateBuildCommand(
                imageRef = options.tag,
                dockerfilePath = options.dockerfile,
                buildArgs = options.buildArgs,
                target = options.target,
                platform = options.platform
            )
            
            io.log(LogLevel.INFO, "Building with command: ${buildCommand.joinToString(" ")}")
            
            // Execute build
            val process = ProcessBuilder()
                .command(buildCommand)
                .directory(File(context.workspaceRoot, options.context))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            
            // Stream output
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
                io.log(LogLevel.INFO, "✅ Docker image built successfully: ${options.tag}")
                
                // Optionally push if requested
                if (options.push) {
                    return@withContext pushImage(options.tag, context, io)
                }
                
                ExecutionResult.success(mapOf(
                    "imageTag" to options.tag,
                    "dockerfile" to options.dockerfile,
                    "context" to options.context
                ))
            } else {
                ExecutionResult.failure(exitCode, "Docker build failed with exit code $exitCode")
            }
            
        } catch (e: Exception) {
            logger.error("Docker build failed", e)
            ExecutionResult.failure(1, "Docker build error: ${e.message}")
        }
    }
    
    private suspend fun pushImage(
        imageTag: String,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        io.log(LogLevel.INFO, "🚀 Pushing Docker image: $imageTag")
        
        val pushProcess = ProcessBuilder()
            .command("docker", "push", imageTag)
            .directory(File(context.workspaceRoot))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        
        val outputThread = Thread {
            pushProcess.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> io.log(LogLevel.INFO, line) }
            }
        }
        
        val errorThread = Thread {
            pushProcess.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> io.log(LogLevel.ERROR, line) }
            }
        }
        
        outputThread.start()
        errorThread.start()
        
        val exitCode = pushProcess.waitFor()
        outputThread.join()
        errorThread.join()
        
        if (exitCode == 0) {
            io.log(LogLevel.INFO, "✅ Docker image pushed successfully: $imageTag")
            ExecutionResult.success(mapOf(
                "imageTag" to imageTag,
                "pushed" to true
            ))
        } else {
            ExecutionResult.failure(exitCode, "Docker push failed with exit code $exitCode")
        }
    }
    
    private fun parseOptions(options: Map<String, Any>): DockerBuildOptions {
        return DockerBuildOptions(
            context = options["context"] as? String ?: ".",
            dockerfile = options["dockerfile"] as? String ?: "Dockerfile",
            tag = options["tag"] as? String ?: throw IllegalArgumentException("tag is required"),
            buildArgs = (options["buildArgs"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
            target = options["target"] as? String,
            platform = options["platform"] as? String,
            push = options["push"] as? Boolean ?: false
        )
    }
    
    private fun validateOptions(options: DockerBuildOptions, workspaceRoot: String) {
        val contextDir = File(workspaceRoot, options.context)
        if (!contextDir.exists() || !contextDir.isDirectory) {
            throw IllegalArgumentException("Build context directory does not exist: ${options.context}")
        }
        
        val dockerfilePath = File(contextDir, options.dockerfile)
        if (!dockerfilePath.exists()) {
            throw IllegalArgumentException("Dockerfile does not exist: ${dockerfilePath.path}")
        }
        
        // Validate tag format (basic validation)
        if (!options.tag.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]*"))) {
            throw IllegalArgumentException("Invalid Docker tag format: ${options.tag}")
        }
    }
}

/**
 * Options for Docker build operation
 */
private data class DockerBuildOptions(
    val context: String,
    val dockerfile: String,
    val tag: String,
    val buildArgs: Map<String, String>,
    val target: String?,
    val platform: String?,
    val push: Boolean
)