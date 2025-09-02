package com.forge.execution.local

import com.forge.project.ProjectGraph
import com.forge.plugin.api.TargetConfiguration
import com.forge.execution.ProcessResult
import com.forge.execution.TaskExecutorPlugin
import com.forge.project.Task
import com.forge.project.TaskResult
import com.forge.project.TaskStatus
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * Local task executor plugin that executes tasks by running shell commands locally
 */
class LocalTaskExecutorPlugin : TaskExecutorPlugin {
    
    private val logger = LoggerFactory.getLogger(LocalTaskExecutorPlugin::class.java)
    
    override fun getExecutorId(): String = "com.forge.executor.LocalTaskExecutor"
    
    override fun executeTask(
        task: Task,
        projectGraph: ProjectGraph,
        workspaceRoot: Path,
        verbose: Boolean
    ): TaskResult {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info("Executing task locally: ${task.id}")
            
            val projectNode = projectGraph.nodes[task.projectName]
            if (projectNode == null) {
                logger.error("Project not found: ${task.projectName}")
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = Instant.ofEpochMilli(startTime),
                    endTime = Instant.now(),
                    error = "Project not found: ${task.projectName}"
                )
            }
            
            val targetConfig = projectNode.data.targets[task.targetName]
            if (targetConfig == null) {
                logger.error("Target not found: ${task.targetName} in project ${task.projectName}")
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = Instant.ofEpochMilli(startTime),
                    endTime = Instant.now(),
                    error = "Target not found: ${task.targetName}"
                )
            }
            
            // Execute based on executor type
            val processResult = when (targetConfig.executor) {
                "nx:run-commands", "@nx/run-commands", "forge:run-commands", null -> {
                    executeRunCommands(targetConfig, task.projectName, projectNode.data.root, workspaceRoot, verbose)
                }
                else -> {
                    logger.error("Unsupported executor: ${targetConfig.executor}")
                    ProcessResult(
                        exitCode = 1,
                        output = "",
                        error = "Unsupported executor: ${targetConfig.executor}"
                    )
                }
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val endInstant = Instant.now()
            
            if (processResult.exitCode == 0) {
                logger.info("Task ${task.id} completed successfully in ${duration}ms")
                return TaskResult(
                    task = task,
                    status = TaskStatus.COMPLETED,
                    startTime = Instant.ofEpochMilli(startTime),
                    endTime = endInstant,
                    output = processResult.output,
                    exitCode = processResult.exitCode
                )
            } else {
                logger.error("Task ${task.id} failed with exit code ${processResult.exitCode}")
                if (verbose) {
                    logger.error("Command output: ${processResult.output}")
                    logger.error("Command error: ${processResult.error}")
                }
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = Instant.ofEpochMilli(startTime),
                    endTime = endInstant,
                    output = processResult.output,
                    error = "Command failed with exit code ${processResult.exitCode}",
                    exitCode = processResult.exitCode
                )
            }
            
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.error("Task ${task.id} failed with exception: ${e.message}", e)
            return TaskResult(
                task = task,
                status = TaskStatus.FAILED,
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.now(),
                error = "Exception: ${e.message}"
            )
        }
    }
    
    /**
     * Execute run-commands executor
     */
    private fun executeRunCommands(
        targetConfig: TargetConfiguration,
        projectName: String,
        projectRoot: String,
        workspaceRoot: Path,
        verbose: Boolean
    ): ProcessResult {
        val options = targetConfig.options
        val workingDir = resolveWorkingDirectory(options["cwd"] as? String, projectRoot, workspaceRoot)
        
        // Get commands array
        val commands = when (val commandsValue = options["commands"]) {
            is List<*> -> commandsValue.filterIsInstance<String>()
            is String -> listOf(commandsValue)
            null -> emptyList()
            else -> emptyList()
        }
        
        if (commands.isEmpty()) {
            return ProcessResult(
                exitCode = 1,
                output = "",
                error = "No commands specified in 'commands' option for run-commands executor"
            )
        }
        
        // Check if commands should run in parallel
        val parallel = options["parallel"] as? Boolean ?: false
        val envOptions = options["env"] as? Map<*, *> ?: emptyMap<String, String>()
        
        logger.debug("Executing ${commands.size} command(s) in ${if (parallel) "parallel" else "sequence"} in $workingDir")
        
        return executeCommandsInSequence(commands, workingDir, envOptions, projectName, workspaceRoot, verbose)
    }
    
    /**
     * Execute commands in sequence
     */
    private fun executeCommandsInSequence(
        commands: List<String>,
        workingDir: Path,
        envOptions: Map<*, *>,
        projectName: String,
        workspaceRoot: Path,
        verbose: Boolean
    ): ProcessResult {
        val allOutput = StringBuilder()
        val allErrors = StringBuilder()
        
        for ((index, command) in commands.withIndex()) {
            val resolvedCommand = resolveCommand(command, projectName, workingDir.toString(), workspaceRoot)
            logger.debug("Executing command ${index + 1}/${commands.size}: $resolvedCommand")
            
            if (verbose) {
                println("  [${index + 1}/${commands.size}] $resolvedCommand")
            }
            
            val result = executeShellCommand(resolvedCommand, workingDir, verbose, envOptions)
            
            allOutput.append(result.output).append("\n")
            allErrors.append(result.error).append("\n")
            
            if (result.exitCode != 0) {
                logger.error("Command ${index + 1} failed with exit code ${result.exitCode}: $resolvedCommand")
                return ProcessResult(
                    exitCode = result.exitCode,
                    output = allOutput.toString().trim(),
                    error = allErrors.toString().trim()
                )
            }
        }
        
        return ProcessResult(
            exitCode = 0,
            output = allOutput.toString().trim(),
            error = allErrors.toString().trim()
        )
    }
    
    /**
     * Execute a shell command with environment variables
     */
    private fun executeShellCommand(command: String, workingDir: Path, verbose: Boolean, envOptions: Map<*, *>): ProcessResult {
        val processBuilder = ProcessBuilder()
        
        // Use shell to execute the command
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        if (isWindows) {
            processBuilder.command("cmd", "/c", command)
        } else {
            processBuilder.command("sh", "-c", command)
        }
        
        processBuilder.directory(workingDir.toFile())
        processBuilder.redirectErrorStream(true)
        
        // Add environment variables
        val environment = processBuilder.environment()
        envOptions.forEach { (key, value) ->
            if (key is String && value is String) {
                environment[key] = value
            }
        }
        
        val process = processBuilder.start()
        
        // Read output
        val output = StringBuilder()
        val errorOutput = StringBuilder()
        
        // Read stdout
        process.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                output.appendLine(line)
                if (verbose) {
                    println("  $line")
                }
            }
        }
        
        // Read stderr
        process.errorStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                errorOutput.appendLine(line)
                if (verbose) {
                    System.err.println("  $line")
                }
            }
        }
        
        // Wait for process to complete with timeout
        val finished = process.waitFor(10, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out after 10 minutes")
        }
        
        return ProcessResult(
            exitCode = process.exitValue(),
            output = output.toString().trim(),
            error = errorOutput.toString().trim()
        )
    }
    
    /**
     * Resolve command with variable substitution
     */
    private fun resolveCommand(command: String, projectName: String, projectRoot: String, workspaceRoot: Path): String {
        return command
            .replace("{projectRoot}", workspaceRoot.resolve(projectRoot).toString())
            .replace("{projectName}", projectName)
            .replace("{workspaceRoot}", workspaceRoot.toString())
    }
    
    /**
     * Resolve working directory
     */
    private fun resolveWorkingDirectory(cwd: String?, projectRoot: String, workspaceRoot: Path): Path {
        return if (cwd != null) {
            val resolvedCwd = workspaceRoot.resolve(cwd)
            if (resolvedCwd.exists()) {
                resolvedCwd
            } else {
                workspaceRoot.resolve(projectRoot)
            }
        } else {
            workspaceRoot.resolve(projectRoot)
        }
    }
}