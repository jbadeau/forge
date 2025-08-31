package com.forge.execution

import com.forge.core.ProjectGraph
import com.forge.graph.Task
import com.forge.graph.TaskExecutionPlan
import com.forge.graph.TaskResult
import com.forge.graph.TaskStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * Executes tasks by running shell commands
 */
class TaskExecutor(
    private val workspaceRoot: Path,
    private val projectGraph: ProjectGraph
) {
    private val logger = LoggerFactory.getLogger(TaskExecutor::class.java)
    
    /**
     * Execute a task execution plan
     */
    fun execute(executionPlan: TaskExecutionPlan, verbose: Boolean = false): ExecutionResults {
        val results = mutableMapOf<String, TaskResult>()
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting execution of ${executionPlan.totalTasks} task(s) across ${executionPlan.getLayerCount()} layer(s)")
        
        for ((layerIndex, layer) in executionPlan.layers.withIndex()) {
            logger.info("Executing layer ${layerIndex + 1} with ${layer.size} task(s)")
            
            // Execute tasks in parallel within each layer
            val layerResults = layer.map { task ->
                executeTask(task, verbose)
            }
            
            // Add results to map
            layerResults.forEach { result ->
                results[result.task.id] = result
            }
            
            // Check if any task in this layer failed
            val failedTasks = layerResults.filter { !it.isSuccess }
            if (failedTasks.isNotEmpty()) {
                logger.error("${failedTasks.size} task(s) failed in layer ${layerIndex + 1}")
                failedTasks.forEach { result ->
                    logger.error("Failed task: ${result.task.id} - ${result.error}")
                }
                // Stop execution on failure
                break
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        val successCount = results.values.count { it.isSuccess }
        val failureCount = results.values.count { !it.isSuccess }
        
        logger.info("Execution completed in ${duration}ms - $successCount succeeded, $failureCount failed")
        
        return ExecutionResults(
            results = results,
            totalDuration = duration,
            successCount = successCount,
            failureCount = failureCount
        )
    }
    
    /**
     * Execute a single task
     */
    private fun executeTask(task: Task, verbose: Boolean): TaskResult {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info("Executing task: ${task.id}")
            
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
            
            // All targets must use run-commands executor
            val processResult = when (targetConfig.executor) {
                "nx:run-commands", "@nx/run-commands", "forge:run-commands", null -> {
                    executeRunCommands(targetConfig, task.projectName, projectNode.data.root, verbose)
                }
                else -> {
                    logger.error("Unsupported executor: ${targetConfig.executor}. Only 'forge:run-commands', 'nx:run-commands', and '@nx/run-commands' are supported.")
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
     * Execute run-commands executor (Nx style)
     */
    private fun executeRunCommands(
        targetConfig: com.forge.core.TargetConfiguration,
        projectName: String,
        projectRoot: String,
        verbose: Boolean
    ): ProcessResult {
        val options = targetConfig.options
        val workingDir = resolveWorkingDirectory(options["cwd"] as? String, projectRoot)
        
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
        
        return if (parallel) {
            executeCommandsInParallel(commands, workingDir, envOptions, projectName, verbose)
        } else {
            executeCommandsInSequence(commands, workingDir, envOptions, projectName, verbose)
        }
    }
    
    /**
     * Execute commands in sequence
     */
    private fun executeCommandsInSequence(
        commands: List<String>,
        workingDir: Path,
        envOptions: Map<*, *>,
        projectName: String,
        verbose: Boolean
    ): ProcessResult {
        val allOutput = StringBuilder()
        val allErrors = StringBuilder()
        
        for ((index, command) in commands.withIndex()) {
            val resolvedCommand = resolveCommand(command, projectName, workingDir.toString())
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
     * Execute commands in parallel
     */
    private fun executeCommandsInParallel(
        commands: List<String>,
        workingDir: Path,
        envOptions: Map<*, *>,
        projectName: String,
        verbose: Boolean
    ): ProcessResult {
        logger.debug("Executing ${commands.size} commands in parallel")
        
        // For now, execute sequentially (parallel execution would require coroutines or threads)
        // This is a simplification - real parallel execution would use CompletableFuture or similar
        return executeCommandsInSequence(commands, workingDir, envOptions, projectName, verbose)
    }

    /**
     * Execute a shell command
     */
    private fun executeShellCommand(command: String, workingDir: Path, verbose: Boolean): ProcessResult {
        return executeShellCommand(command, workingDir, verbose, emptyMap<String, String>())
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
    private fun resolveCommand(command: String, projectName: String, projectRoot: String): String {
        return command
            .replace("{projectRoot}", workspaceRoot.resolve(projectRoot).toString())
            .replace("{projectName}", projectName)
            .replace("{workspaceRoot}", workspaceRoot.toString())
    }
    
    /**
     * Resolve working directory
     */
    private fun resolveWorkingDirectory(cwd: String?, projectRoot: String): Path {
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

/**
 * Result of executing a shell process
 */
data class ProcessResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

/**
 * Results of executing multiple tasks
 */
data class ExecutionResults(
    val results: Map<String, TaskResult>,
    val totalDuration: Long,
    val successCount: Int,
    val failureCount: Int
) {
    val success: Boolean get() = failureCount == 0
}