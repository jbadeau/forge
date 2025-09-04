package com.forge.docker.plugin.executors

import com.forge.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Docker push executor for pushing images to registries
 */
class DockerPushExecutor : Executor {
    
    private val logger = LoggerFactory.getLogger(DockerPushExecutor::class.java)
    
    override val metadata = ExecutorMetadata(
        id = "com.forge.docker.executor.push",
        name = "Docker Push Executor",
        version = "1.0.0",
        description = "Push Docker images to registries with authentication support"
    )
    
    override fun canExecute(action: ActionNode): Boolean {
        return action.type.name == "PUSH" || (action.type.name == "PUBLISH" && action.options.containsKey("image"))
    }
    
    override suspend fun execute(
        action: ActionNode,
        context: ExecutionContext,
        io: ExecutionIO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        io.log(LogLevel.INFO, "🚀 Pushing Docker image...")
        
        try {
            val options = parseOptions(action.options)
            val imageTag = buildFullImageTag(options)
            
            // Tag image for registry if needed
            if (options.registry != null || options.repository != null) {
                val tagResult = tagImageForRegistry(options.image, imageTag, context, io)
                if (!tagResult) {
                    return@withContext ExecutionResult.failure(1, "Failed to tag image for registry")
                }
            }
            
            // Push the image
            val pushCommand = listOf("docker", "push", imageTag)
            
            io.log(LogLevel.INFO, "Pushing with command: ${pushCommand.joinToString(" ")}")
            
            val process = ProcessBuilder()
                .command(pushCommand)
                .directory(File(context.workspaceRoot))
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
                io.log(LogLevel.INFO, "✅ Docker image pushed successfully: $imageTag")
                ExecutionResult.success(mapOf(
                    "imageTag" to imageTag,
                    "registry" to options.registry,
                    "repository" to options.repository
                ))
            } else {
                ExecutionResult.failure(exitCode, "Docker push failed with exit code $exitCode")
            }
            
        } catch (e: Exception) {
            logger.error("Docker push failed", e)
            ExecutionResult.failure(1, "Docker push error: ${e.message}")
        }
    }
    
    private suspend fun tagImageForRegistry(
        sourceImage: String,
        targetTag: String,
        context: ExecutionContext,
        io: ExecutionIO
    ): Boolean = withContext(Dispatchers.IO) {
        
        if (sourceImage == targetTag) return@withContext true
        
        io.log(LogLevel.INFO, "🏷️  Tagging image: $sourceImage -> $targetTag")
        
        val tagProcess = ProcessBuilder()
            .command("docker", "tag", sourceImage, targetTag)
            .directory(File(context.workspaceRoot))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        
        val exitCode = tagProcess.waitFor()
        
        if (exitCode != 0) {
            val error = tagProcess.errorStream.bufferedReader().readText()
            io.log(LogLevel.ERROR, "Failed to tag image: $error")
        }
        
        exitCode == 0
    }
    
    private fun buildFullImageTag(options: DockerPushOptions): String {
        return when {
            options.registry != null && options.repository != null -> {
                "${options.registry}/${options.repository}:${options.tag}"
            }
            options.registry != null -> {
                "${options.registry}/${options.image.substringAfterLast("/")}:${options.tag}"
            }
            options.repository != null -> {
                "${options.repository}:${options.tag}"
            }
            else -> {
                if (options.image.contains(":")) {
                    options.image
                } else {
                    "${options.image}:${options.tag}"
                }
            }
        }
    }
    
    private fun parseOptions(options: Map<String, Any>): DockerPushOptions {
        return DockerPushOptions(
            image = options["image"] as? String ?: throw IllegalArgumentException("image is required"),
            registry = options["registry"] as? String,
            repository = options["repository"] as? String,
            tag = options["tag"] as? String ?: "latest"
        )
    }
}

/**
 * Options for Docker push operation
 */
private data class DockerPushOptions(
    val image: String,
    val registry: String?,
    val repository: String?,
    val tag: String
)