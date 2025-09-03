package com.forge.execution.remote

import build.bazel.remote.execution.v2.*
import com.forge.plugin.api.TargetConfiguration
import com.forge.task.Task

import com.google.protobuf.Duration as ProtoDuration
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Builder for converting Forge tasks to Remote Execution API objects
 */
class RemoteExecutionBuilder(
    private val workspaceRoot: Path,
    private val instanceName: String = ""
) {
    private val logger = LoggerFactory.getLogger(RemoteExecutionBuilder::class.java)
    
    /**
     * Build a Remote Execution Action from a Forge task
     */
    fun buildAction(task: Task, projectRoot: String): Action {
        val command = buildCommand(task, projectRoot)
        val commandDigest = computeDigest(command.toByteArray())
        
        val inputRoot = buildInputRoot(task, projectRoot)
        val inputRootDigest = computeDigest(inputRoot.toByteArray())
        
        return Action.newBuilder()
            .setCommandDigest(commandDigest)
            .setInputRootDigest(inputRootDigest)
            .setTimeout(ProtoDuration.newBuilder().setSeconds(300).build()) // 5 minutes default
            .setDoNotCache(!task.configuration.isCacheable())
            .build()
    }
    
    /**
     * Build a Command from a Forge task
     */
    fun buildCommand(task: Task, projectRoot: String): Command {
        val commandBuilder = Command.newBuilder()
        
        // Extract commands from target configuration
        val commands = extractCommands(task.configuration)
        if (commands.isNotEmpty()) {
            // For shell commands, we use sh -c or cmd /c
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            if (isWindows) {
                commandBuilder.addArguments("cmd")
                commandBuilder.addArguments("/c")
                commandBuilder.addArguments(commands.joinToString(" && "))
            } else {
                commandBuilder.addArguments("sh")
                commandBuilder.addArguments("-c")
                commandBuilder.addArguments(commands.joinToString(" && "))
            }
        }
        
        // Set working directory
        val workingDir = resolveWorkingDirectory(task.configuration, projectRoot)
        commandBuilder.setWorkingDirectory(workingDir)
        
        // Add environment variables
        val envOptions = task.configuration.options["env"] as? Map<*, *> ?: emptyMap<String, String>()
        envOptions.forEach { (key, value) ->
            if (key is String && value is String) {
                commandBuilder.addEnvironmentVariables(
                    Command.EnvironmentVariable.newBuilder()
                        .setName(key)
                        .setValue(value)
                        .build()
                )
            }
        }
        
        // Add output paths from target configuration
        task.configuration.outputs.forEach { output ->
            val resolvedOutput = resolvePathPattern(output, task.project, projectRoot)
            commandBuilder.addOutputPaths(resolvedOutput)
        }
        
        return commandBuilder.build()
    }
    
    /**
     * Build input root directory from task inputs
     */
    fun buildInputRoot(task: Task, projectRoot: String): Directory {
        val directoryBuilder = Directory.newBuilder()
        val inputPaths = mutableSetOf<String>()
        
        // Resolve input patterns
        task.configuration.inputs.forEach { input ->
            val resolvedPaths = resolveInputPattern(input, task.project, projectRoot)
            inputPaths.addAll(resolvedPaths)
        }
        
        // Build directory structure
        inputPaths.forEach { inputPath ->
            val fullPath = workspaceRoot.resolve(inputPath)
            if (fullPath.exists()) {
                when {
                    fullPath.isRegularFile() -> {
                        val content = Files.readAllBytes(fullPath)
                        val digest = computeDigest(content)
                        directoryBuilder.addFiles(
                            FileNode.newBuilder()
                                .setName(fullPath.fileName.toString())
                                .setDigest(digest)
                                .setIsExecutable(Files.isExecutable(fullPath))
                                .build()
                        )
                    }
                    fullPath.isDirectory() -> {
                        // For directories, we'd need to recursively build the tree
                        // This is a simplified version
                        directoryBuilder.addDirectories(
                            DirectoryNode.newBuilder()
                                .setName(fullPath.fileName.toString())
                                .setDigest(computeDigest(ByteArray(0))) // Placeholder
                                .build()
                        )
                    }
                }
            }
        }
        
        return directoryBuilder.build()
    }
    
    /**
     * Build ExecuteRequest for a task
     */
    fun buildExecuteRequest(task: Task, projectRoot: String, skipCacheLookup: Boolean = false): ExecuteRequest {
        val action = buildAction(task, projectRoot)
        val actionDigest = computeDigest(action.toByteArray())
        
        return ExecuteRequest.newBuilder()
            .setInstanceName(instanceName)
            .setActionDigest(actionDigest)
            .setSkipCacheLookup(skipCacheLookup)
            .build()
    }
    
    /**
     * Extract commands from target configuration
     */
    private fun extractCommands(target: TargetConfiguration): List<String> {
        val options = target.options
        return when (val commandsValue = options["commands"]) {
            is List<*> -> commandsValue.filterIsInstance<String>()
            is String -> listOf(commandsValue)
            else -> emptyList()
        }
    }
    
    /**
     * Resolve working directory for task
     */
    private fun resolveWorkingDirectory(target: TargetConfiguration, projectRoot: String): String {
        val options = target.options
        val cwd = options["cwd"] as? String
        return if (cwd != null) {
            workspaceRoot.resolve(cwd).toString()
        } else {
            workspaceRoot.resolve(projectRoot).toString()
        }
    }
    
    /**
     * Resolve input pattern to actual file paths
     */
    private fun resolveInputPattern(pattern: String, projectName: String, projectRoot: String): List<String> {
        return when {
            pattern == "default" -> {
                // Default inputs: source files, config files
                listOf(
                    "$projectRoot/src/**/*",
                    "$projectRoot/*.json",
                    "$projectRoot/*.yaml",
                    "$projectRoot/*.yml",
                    "$projectRoot/Dockerfile"
                )
            }
            pattern.startsWith("{projectRoot}") -> {
                listOf(pattern.replace("{projectRoot}", projectRoot))
            }
            pattern.startsWith("^default") -> {
                // Dependencies' default inputs - simplified
                listOf("libs/**/*")
            }
            else -> listOf(pattern)
        }
    }
    
    /**
     * Resolve path pattern with variable substitution
     */
    private fun resolvePathPattern(pattern: String, projectName: String, projectRoot: String): String {
        return pattern
            .replace("{projectRoot}", projectRoot)
            .replace("{projectName}", projectName)
            .replace("{workspaceRoot}", workspaceRoot.toString())
    }
    
    /**
     * Compute SHA-256 digest
     */
    fun computeDigest(data: ByteArray): Digest {
        val hasher = MessageDigest.getInstance("SHA-256")
        val hash = hasher.digest(data)
        
        return Digest.newBuilder()
            .setHash(hash.joinToString("") { "%02x".format(it) }) // Use hex format like BuildBarn expects
            .setSizeBytes(data.size.toLong())
            .build()
    }
}