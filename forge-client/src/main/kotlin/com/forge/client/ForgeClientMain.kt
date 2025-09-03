package com.forge.client

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) = ForgeCommand().subcommands(
    ShowCommand(),
    RunCommand(),
    RunManyCommand(),
    GraphCommand(),
    DaemonCommand()
).main(args)

class ForgeCommand : CliktCommand(name = "forge") {
    override fun run() = Unit
}

class ShowCommand : CliktCommand(name = "show") {
    private val what by argument("What to show").choice("projects", "project")
    private val projectName by argument("Project name (for 'project' command)").optional()
    private val format by option("--format", "-f", help = "Output format").choice("text", "json").default("text")
    
    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val (method, params) = when (what) {
            "projects" -> "show/projects" to mapOf(
                "workspaceRoot" to workspaceRoot,
                "format" to format
            )
            "project" -> {
                val name = projectName ?: throw RuntimeException("Project name required for 'show project' command")
                "show/project" to mapOf(
                    "workspaceRoot" to workspaceRoot,
                    "projectName" to name,
                    "format" to format
                )
            }
            else -> throw RuntimeException("Unknown show command: $what")
        }
        
        val response = client.sendRequest(method, params)
        handleJsonRpcResponse(response)
    }
}

class RunCommand : CliktCommand(name = "run") {
    private val projectName by argument("Project name")
    private val target by argument("Target to run")
    private val verbose by option("--verbose", "-v", help = "Verbose output").flag()
    private val dryRun by option("--dry-run", help = "Show what would be executed").flag()
    
    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "projectName" to projectName,
            "target" to target,
            "verbose" to verbose,
            "dryRun" to dryRun
        )
        
        val response = client.sendRequest("run/task", params)
        handleJsonRpcResponse(response)
    }
}

class RunManyCommand : CliktCommand(name = "run-many") {
    private val target by option("--target", "-t", help = "Target to run").required()
    private val tags by option("--tags", help = "Project tags (comma-separated)").split(",").default(emptyList())
    private val all by option("--all", help = "Run on all projects").flag()
    private val verbose by option("--verbose", "-v", help = "Verbose output").flag()
    private val dryRun by option("--dry-run", help = "Show what would be executed").flag()
    
    override fun run() {
        val client = DaemonClient()
        val workspaceRoot = Paths.get("").toAbsolutePath().toString()
        
        val params = mapOf(
            "workspaceRoot" to workspaceRoot,
            "target" to target,
            "tags" to tags.filter { it.isNotBlank() },
            "all" to all,
            "verbose" to verbose,
            "dryRun" to dryRun
        )
        
        val response = client.sendRequest("run/many", params)
        handleJsonRpcResponse(response)
    }
}

class GraphCommand : CliktCommand(name = "graph") {
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

class DaemonCommand : CliktCommand(name = "daemon") {
    private val action by argument("Daemon action").choice("ping", "shutdown")
    
    override fun run() {
        val client = DaemonClient()
        
        val response = client.sendRequest(action)
        handleJsonRpcResponse(response)
    }
}

private fun handleJsonRpcResponse(response: JsonRpcResponse) {
    if (response.error != null) {
        System.err.println("Error: ${response.error.message}")
        if (response.error.data != null) {
            System.err.println("Details: ${response.error.data}")
        }
        exitProcess(1)
    }
    
    if (response.result != null) {
        when (response.result) {
            is String -> println(response.result)
            is Map<*, *> -> {
                val result = response.result.entries.associate { (k, v) -> k.toString() to v }
                if (result.containsKey("message")) {
                    println(result["message"])
                }
                if (result.containsKey("projects")) {
                    val projects = result["projects"] as? List<*>
                    projects?.forEach { println("  - $it") }
                }
                if (result.containsKey("exitCode")) {
                    val exitCode = result["exitCode"] as? Int ?: 0
                    if (exitCode != 0) {
                        exitProcess(exitCode)
                    }
                }
            }
            is List<*> -> {
                // Handle JSON array results (e.g., project lists)
                response.result.forEach { item ->
                    when (item) {
                        is Map<*, *> -> {
                            val project = item.entries.associate { (k, v) -> k.toString() to v }
                            val name = project["name"]
                            val type = project["type"]
                            println("$name ($type)")
                        }
                        else -> println(item)
                    }
                }
            }
            else -> println(response.result)
        }
    }
}