package com.forge.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.forge.client.DaemonClient
import com.forge.client.JsonRpcResponse
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Main CLI class for Forge build tool
 */
class ForgeCli : CliktCommand() {
    override fun help(context: Context): String = """
    Forge is a command line tool that showcases Kotlin-based build system.

    This tool is inspired by Nx and provides project and task graph functionality
    for managing complex monorepo workspaces.
    """.trimIndent()

    override fun run() = Unit
}

/**
 * Run a target for a specific project
 */
class RunCommand : CliktCommand() {
    override fun help(context: Context): String = "Run a target for a specific project"
    private val project by argument(help = "Project name")
    private val target by argument(help = "Target name")
    private val dryRun by option("--dry-run", help = "Show what would be executed").flag()
    private val verbose by option("--verbose", help = "Show detailed execution plan").flag()

    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "projectName" to project,
            "target" to target,
            "verbose" to verbose,
            "dryRun" to dryRun
        )
        
        val response = client.sendRequest("run/task", params)
        handleJsonRpcResponse(response)
    }
}

/**
 * Run a target for multiple projects
 */
class RunManyCommand : CliktCommand() {
    override fun help(context: Context): String = "Run a target for multiple projects"
    private val target by option("--target", "-t", help = "Target to run").required()
    private val projects by option("--projects", help = "Specific projects to run").split(",")
    private val tags by option("--tags", help = "Projects with these tags").split(",")
    private val all by option("--all", help = "Run for all projects").flag()
    private val dryRun by option("--dry-run", help = "Show what would be executed").flag()
    private val verbose by option("--verbose", help = "Show detailed execution plan").flag()

    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "target" to target,
            "projects" to (projects?.filter { it.isNotBlank() } ?: emptyList()),
            "tags" to (tags?.filter { it.isNotBlank() } ?: emptyList()),
            "all" to all,
            "verbose" to verbose,
            "dryRun" to dryRun
        )
        
        val response = client.sendRequest("run/many", params)
        handleJsonRpcResponse(response)
    }
}

/**
 * Show commands (projects, project details, etc.)
 */
class ShowCommand : CliktCommand() {
    override fun help(context: Context): String = "Show workspace information"
    override fun run() = Unit
}

/**
 * Show all projects
 */
class ShowProjectsCommand : CliktCommand("projects") {
    override fun help(context: Context): String = "List all projects"
    private val format by option("--format", "-f", help = "Output format").choice("text", "json").default("text")

    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "format" to format
        )
        
        val response = client.sendRequest("show/projects", params)
        handleJsonRpcResponse(response)
    }
}

/**
 * Show specific project details
 */
class ShowProjectCommand : CliktCommand("project") {
    override fun help(context: Context): String = "Show project details"
    private val projectName by argument(help = "Project name")
    private val format by option("--format", "-f", help = "Output format").choice("text", "json").default("text")

    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "projectName" to projectName,
            "format" to format
        )
        
        val response = client.sendRequest("show/project", params)
        handleJsonRpcResponse(response)
    }
}

/**
 * Show project dependency graph
 */
class GraphCommand : CliktCommand() {
    override fun help(context: Context): String = "Show project dependency graph"
    private val format by option("--format", "-f", help = "Output format").choice("text", "json").default("text")
    
    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "format" to format
        )
        
        val response = client.sendRequest("project/graph", params)
        handleJsonRpcResponse(response)
    }
}

private fun handleJsonRpcResponse(response: JsonRpcResponse) {
    val error = response.error
    if (error != null) {
        System.err.println("Error: ${error.message}")
        if (error.data != null) {
            System.err.println("Details: ${error.data}")
        }
        exitProcess(1)
    }
    
    if (response.result != null) {
        when (response.result) {
            is String -> println(response.result)
            is Map<*, *> -> {
                val result = (response.result as Map<*, *>).entries.associate { (k, v) -> k.toString() to v }
                if (result.containsKey("message")) {
                    println(result["message"])
                }
                if (result.containsKey("exitCode")) {
                    val exitCode = result["exitCode"] as? Int ?: 0
                    if (exitCode != 0) {
                        exitProcess(exitCode)
                    }
                }
            }
            else -> println(response.result)
        }
    }
}

fun main(args: Array<String>) = ForgeCli()
    .subcommands(
        RunCommand(),
        RunManyCommand(),
        ShowCommand().subcommands(
            ShowProjectsCommand(),
            ShowProjectCommand()
        ),
        GraphCommand()
    )
    .main(args)