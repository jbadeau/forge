package com.frontseat.command

import com.frontseat.nature.TargetLifecycle
import java.nio.file.Path

/**
 * Result of command execution
 */
sealed class CommandResult {
    data class Success(val message: String) : CommandResult()
    data class Failure(val error: String, val exitCode: Int = 1) : CommandResult()
    data class Skipped(val reason: String) : CommandResult()
}

/**
 * Base interface for executable command tasks (NX executor-like)
 */
interface CommandTask {
    val id: String
    val description: String?
    val lifecycle: TargetLifecycle
    val cacheable: Boolean
    val workingDirectory: Path
    val environment: Map<String, String>
    val timeout: Long?
    val commands: List<String>
    val parallel: Boolean
    val args: List<String>
    val forwardAllArgs: Boolean
    val readyWhen: String?
    val color: Boolean
    val envFile: Path?
    
    // NX executor-like properties
    val inputs: List<String>
    val outputs: List<String>
    val options: Map<String, Any>
    
    /**
     * Execute this command task
     */
    suspend fun execute(): CommandResult
    
    /**
     * Get all commands to execute (combines commands and args)
     */
    fun getAllCommands(): List<String> {
        return if (forwardAllArgs && args.isNotEmpty()) {
            commands.map { cmd ->
                "$cmd ${args.joinToString(" ")}"
            }
        } else {
            commands
        }
    }
}

/**
 * Concrete implementation of command task following NX run-commands pattern
 */
data class SimpleCommandTask(
    override val id: String,
    override val description: String? = null,
    override val lifecycle: TargetLifecycle,
    override val cacheable: Boolean = true,
    override val workingDirectory: Path,
    override val environment: Map<String, String> = emptyMap(),
    override val timeout: Long? = null,
    override val commands: List<String> = emptyList(),
    override val parallel: Boolean = true,
    override val args: List<String> = emptyList(),
    override val forwardAllArgs: Boolean = true,
    override val readyWhen: String? = null,
    override val color: Boolean = true,
    override val envFile: Path? = null,
    override val inputs: List<String> = emptyList(),
    override val outputs: List<String> = emptyList(),
    override val options: Map<String, Any> = emptyMap()
) : CommandTask {
    
    init {
        require(commands.isNotEmpty()) { "At least one command must be specified" }
    }
    
    override suspend fun execute(): CommandResult {
        // TODO: Implement actual command execution
        return CommandResult.Success("Commands executed successfully: ${getAllCommands()}")
    }
}

/**
 * Builder for creating command tasks
 */
class CommandTaskBuilder(private val id: String, private val lifecycle: TargetLifecycle) {
    private var description: String? = null
    private var cacheable: Boolean = true
    private var workingDirectory: Path = Path.of(".")
    private var environment: Map<String, String> = emptyMap()
    private var timeout: Long? = null
    private var commands: MutableList<String> = mutableListOf()
    private var parallel: Boolean = true
    private var args: List<String> = emptyList()
    private var forwardAllArgs: Boolean = true
    private var readyWhen: String? = null
    private var color: Boolean = true
    private var envFile: Path? = null
    var inputs: List<String> = emptyList()
    var outputs: List<String> = emptyList()
    var options: Map<String, Any> = emptyMap()
    
    fun description(description: String) = apply { this.description = description }
    fun cacheable(cacheable: Boolean) = apply { this.cacheable = cacheable }
    fun workingDirectory(workingDirectory: Path) = apply { this.workingDirectory = workingDirectory }
    fun environment(environment: Map<String, String>) = apply { this.environment = environment }
    fun timeout(timeout: Long) = apply { this.timeout = timeout }
    fun commands(vararg commands: String) = apply { this.commands.addAll(commands) }
    fun commands(commands: List<String>) = apply { this.commands.addAll(commands) }
    fun command(command: String) = apply { this.commands.add(command) }
    fun parallel(parallel: Boolean) = apply { this.parallel = parallel }
    fun args(vararg args: String) = apply { this.args = args.toList() }
    fun args(args: List<String>) = apply { this.args = args }
    fun forwardAllArgs(forwardAllArgs: Boolean) = apply { this.forwardAllArgs = forwardAllArgs }
    fun readyWhen(readyWhen: String) = apply { this.readyWhen = readyWhen }
    fun color(color: Boolean) = apply { this.color = color }
    fun envFile(envFile: Path) = apply { this.envFile = envFile }
    
    fun build(): CommandTask {
        return SimpleCommandTask(
            id = id,
            description = description,
            lifecycle = lifecycle,
            cacheable = cacheable,
            workingDirectory = workingDirectory,
            environment = environment,
            timeout = timeout,
            commands = commands.toList(),
            parallel = parallel,
            args = args,
            forwardAllArgs = forwardAllArgs,
            readyWhen = readyWhen,
            color = color,
            envFile = envFile,
            inputs = inputs,
            outputs = outputs,
            options = options
        )
    }
}

/**
 * Convenience function to create a command task
 */
fun commandTask(
    id: String,
    lifecycle: TargetLifecycle,
    configure: CommandTaskBuilder.() -> Unit = {}
): CommandTask {
    return CommandTaskBuilder(id, lifecycle)
        .apply(configure)
        .build()
}