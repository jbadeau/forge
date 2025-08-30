package com.forge.demo

import com.forge.discovery.ProjectDiscovery
import com.forge.execution.TaskGraphBuilder
import com.forge.cli.SimpleShowCommand
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

fun main(args: Array<String>) {
    println("🔧 FORGE - Kotlin Build Tool Demo")
    println("==================================")
    
    // Parse command line arguments
    val target = args.find { it.startsWith("--target=") }?.substringAfter("=") ?: "build"
    val dryRun = args.contains("--dry-run")
    val showProject = args.find { it.startsWith("--show=") }?.substringAfter("=")
    val showJson = args.contains("--json")
    
    try {
        val workspaceRoot = findWorkspaceRoot()
        
        // Handle show project command
        if (showProject != null) {
            println("📍 Workspace: $workspaceRoot")
            println()
            SimpleShowCommand.showProject(showProject, showJson)
            return
        }
        
        println("📍 Workspace: $workspaceRoot")
        println("🎯 Target: $target")
        if (dryRun) println("🔍 Mode: DRY RUN")
        println()

        // Discover projects
        println("📁 Discovering projects...")
        val discovery = ProjectDiscovery(workspaceRoot)
        val projectGraph = discovery.discoverProjects()

        if (projectGraph.nodes.isEmpty()) {
            println("❌ No projects found in workspace")
            return
        }

        println("✅ Discovered ${projectGraph.nodes.size} projects:")
        projectGraph.getAllProjects().sortedBy { it.name }.forEach { project ->
            val tags = if (project.data.tags.isNotEmpty()) " (${project.data.tags.joinToString(", ")})" else ""
            println("  • ${project.name} - ${project.data.projectType}$tags")
        }
        println()

        // Build task graph
        println("⚙️  Building task graph for target '$target'...")
        val taskGraphBuilder = TaskGraphBuilder(projectGraph)
        val projectNames = projectGraph.getAllProjects().map { it.name }
        val taskGraph = taskGraphBuilder.buildTaskGraphForProjects(target, projectNames)

        if (taskGraph.isEmpty()) {
            println("❌ No tasks found for target '$target'")
            return
        }

        val executionPlan = taskGraph.getExecutionPlan()
        
        println("📋 Execution Plan:")
        println("  • Total tasks: ${executionPlan.totalTasks}")
        println("  • Execution layers: ${executionPlan.getLayerCount()}")
        println("  • Max parallelism: ${executionPlan.maxParallelism}")
        println()
        
        executionPlan.layers.forEachIndexed { index, layer ->
            val layerNum = index + 1
            println("  Layer $layerNum: ${layer.joinToString(", ") { it.id }}")
        }
        println()
        
        if (dryRun) {
            println("🔍 Dry run completed - no tasks were executed")
        } else {
            println("▶️  This would execute ${executionPlan.totalTasks} tasks in ${executionPlan.getLayerCount()} parallel layers")
            println("✅ Demo completed successfully!")
        }

    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    }
}

private fun findWorkspaceRoot(): Path {
    var current = Path.of("").absolute()
    while (current.parent != null) {
        if (current.resolve("forge.json").exists() || 
            current.resolve("nx.json").exists() ||
            current.resolve(".git").exists()) {
            return current
        }
        current = current.parent
    }
    return Path.of("").absolute()
}