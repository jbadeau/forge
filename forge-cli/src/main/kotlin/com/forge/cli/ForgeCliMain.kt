package com.forge.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.forge.discovery.ProjectDiscovery
import com.forge.execution.ExecutorFactory
import com.forge.execution.TaskGraphBuilder
import com.forge.inference.InferenceEngine
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
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
        echo("🔧 Running target '$target' for project '$project'")
        if (dryRun) echo("🔍 DRY RUN MODE")
        echo()

        val workspaceRoot = findWorkspaceRoot()
        val (projectGraph, workspaceConfig) = discoverProjectsWithConfig(workspaceRoot)

        val projectNode = projectGraph.nodes[project]
        if (projectNode == null) {
            echo("❌ Project '$project' not found", err = true)
            echo("Available projects:")
            projectGraph.getAllProjects().sortedBy { it.name }.forEach { p ->
                echo("  • ${p.name}")
            }
            throw com.github.ajalt.clikt.core.Abort()
        }

        if (!projectNode.data.targets.containsKey(target)) {
            echo("❌ Target '$target' not found for project '$project'", err = true)
            echo("Available targets:")
            projectNode.data.targets.keys.sorted().forEach { t ->
                echo("  • $t")
            }
            throw com.github.ajalt.clikt.core.Abort()
        }

        // Build task graph for single project
        val taskGraphBuilder = TaskGraphBuilder(projectGraph)
        val taskGraph = taskGraphBuilder.buildTaskGraphForProjects(target, listOf(project))

        if (taskGraph.isEmpty()) {
            echo("❌ No tasks to execute", err = true)
            throw com.github.ajalt.clikt.core.Abort()
        }

        val executionPlan = taskGraph.getExecutionPlan()

        if (verbose) {
            echo("📋 Execution Plan:")
            executionPlan.layers.forEachIndexed { index, layer ->
                echo("  Layer ${index + 1}: ${layer.joinToString(", ") { it.id }}")
            }
            echo()
        }

        if (dryRun) {
            echo("🔍 Would execute ${executionPlan.totalTasks} task(s)")
            executionPlan.layers.forEach { layer ->
                layer.forEach { task ->
                    echo("  • ${task.id}")
                }
            }
        } else {
            echo("▶️  Executing ${executionPlan.totalTasks} task(s)...")
            
            // Execute tasks with unified executor (supports both local and remote execution)
            val executor = ExecutorFactory.createExecutor(workspaceRoot, projectGraph, workspaceConfig)
            val results = try {
                executor.execute(executionPlan, verbose)
            } finally {
                if (executor is AutoCloseable) {
                    executor.close()
                }
            }
            
            if (results.success) {
                echo("✅ Task execution completed successfully!")
                echo("   ${results.successCount} tasks completed in ${results.totalDuration}ms")
            } else {
                echo("❌ Task execution failed!")
                echo("   ${results.successCount} succeeded, ${results.failureCount} failed")
                
                // Show failed tasks
                val failedTasks = results.results.values.filter { !it.isSuccess }
                failedTasks.forEach { result ->
                    echo("   ✗ ${result.task.id}: ${result.error}")
                }
                
                throw com.github.ajalt.clikt.core.Abort()
            }
        }
    }
}

/**
 * Run a target for multiple projects
 */
class RunManyCommand : CliktCommand() {
    override fun help(context: Context): String = "Run a target for multiple projects"
    private val targetName by option("--target", help = "Target to run")
    private val projects by option("--projects", help = "Specific projects to run").split(",")
    private val tags by option("--tags", help = "Projects with these tags").split(",")
    private val all by option("--all", help = "Run for all projects").flag()
    private val parallel by option("--parallel", help = "Max parallel tasks").int().default(3)
    private val dryRun by option("--dry-run", help = "Show what would be executed").flag()
    private val verbose by option("--verbose", help = "Show detailed execution plan").flag()

    override fun run() {
        if (targetName == null) {
            echo("❌ --target is required", err = true)
            throw com.github.ajalt.clikt.core.Abort()
        }

        echo("🔧 Running target '$targetName' for multiple projects")
        if (dryRun) echo("🔍 DRY RUN MODE")
        echo()

        val workspaceRoot = findWorkspaceRoot()
        val projectGraph = discoverProjects(workspaceRoot)

        // Select projects based on criteria
        val selectedProjects = when {
            all -> projectGraph.getAllProjects()
            projects != null -> projectGraph.getAllProjects().filter { it.name in projects!! }
            tags != null -> projectGraph.getAllProjects().filter { project ->
                tags!!.any { tag -> project.data.tags.contains(tag) }
            }
            else -> {
                echo("❌ Must specify --projects, --tags, or --all", err = true)
                throw com.github.ajalt.clikt.core.Abort()
            }
        }

        if (selectedProjects.isEmpty()) {
            echo("❌ No projects selected", err = true)
            throw com.github.ajalt.clikt.core.Abort()
        }

        // Filter projects that have the target
        val projectsWithTarget = selectedProjects.filter { it.data.targets.containsKey(targetName!!) }

        if (projectsWithTarget.isEmpty()) {
            echo("❌ No selected projects have target '$targetName'", err = true)
            echo("Selected projects:")
            selectedProjects.forEach { echo("  • ${it.name}") }
            throw com.github.ajalt.clikt.core.Abort()
        }

        echo("📋 Selected ${projectsWithTarget.size} project(s):")
        projectsWithTarget.forEach { project ->
            val tagsStr = if (project.data.tags.isNotEmpty()) " [${project.data.tags.joinToString(", ")}]" else ""
            echo("  • ${project.name}$tagsStr")
        }
        echo()

        // Build task graph for selected projects
        val taskGraphBuilder = TaskGraphBuilder(projectGraph)
        val projectNames = projectsWithTarget.map { it.name }
        val taskGraph = taskGraphBuilder.buildTaskGraphForProjects(targetName!!, projectNames)

        if (taskGraph.isEmpty()) {
            echo("❌ No tasks to execute", err = true)
            throw com.github.ajalt.clikt.core.Abort()
        }

        val executionPlan = taskGraph.getExecutionPlan()

        echo("📋 Execution Summary:")
        echo("  • Total tasks: ${executionPlan.totalTasks}")
        echo("  • Execution layers: ${executionPlan.getLayerCount()}")
        echo("  • Max parallelism: ${Math.min(parallel, executionPlan.maxParallelism)}")
        echo()

        if (verbose) {
            echo("📋 Detailed Execution Plan:")
            executionPlan.layers.forEachIndexed { index, layer ->
                echo("  Layer ${index + 1}: ${layer.joinToString(", ") { it.id }}")
            }
            echo()
        }

        if (dryRun) {
            echo("🔍 Would execute the following tasks:")
            executionPlan.layers.forEach { layer ->
                layer.forEach { task ->
                    echo("  • ${task.id}")
                }
            }
        } else {
            echo("▶️  Executing ${executionPlan.totalTasks} task(s) across ${executionPlan.getLayerCount()} layer(s)...")
            
            // Execute tasks with unified executor (supports both local and remote execution)
            val executor = ExecutorFactory.createExecutor(workspaceRoot, projectGraph)
            val results = try {
                executor.execute(executionPlan, verbose)
            } finally {
                if (executor is AutoCloseable) {
                    executor.close()
                }
            }
            
            if (results.success) {
                echo("✅ Task execution completed successfully!")
                echo("   ${results.successCount} tasks completed in ${results.totalDuration}ms")
            } else {
                echo("❌ Task execution failed!")
                echo("   ${results.successCount} succeeded, ${results.failureCount} failed")
                
                // Show failed tasks
                val failedTasks = results.results.values.filter { !it.isSuccess }
                failedTasks.forEach { result ->
                    echo("   ✗ ${result.task.id}: ${result.error}")
                }
                
                throw com.github.ajalt.clikt.core.Abort()
            }
        }
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
    private val json by option("--json", help = "Output in JSON format").flag()

    override fun run() {
        val workspaceRoot = findWorkspaceRoot()
        val projectGraph = discoverProjects(workspaceRoot)

        if (json) {
            val projects = projectGraph.getAllProjects().sortedBy { it.name }.map { project ->
                mapOf(
                    "name" to project.name,
                    "type" to project.data.projectType,
                    "root" to project.data.root,
                    "tags" to project.data.tags,
                    "targets" to project.data.targets.keys.toList()
                )
            }
            echo(com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(projects))
        } else {
            echo("📦 Projects in workspace ($workspaceRoot):")
            echo("═".repeat(60))
            echo()

            val projects = projectGraph.getAllProjects().sortedBy { it.name }
            projects.forEach { project ->
                val tagsStr = if (project.data.tags.isNotEmpty()) " [${project.data.tags.joinToString(", ")}]" else ""
                val targetsStr = project.data.targets.keys.sorted().joinToString(", ")
                echo("📦 ${project.name} (${project.data.projectType})$tagsStr")
                echo("   Root: ${project.data.root}")
                if (project.data.targets.isNotEmpty()) {
                    echo("   Targets: $targetsStr")
                }
                echo()
            }

            echo("Total: ${projects.size} project(s)")
        }
    }
}

/**
 * Show specific project details
 */
class ShowProjectCommand : CliktCommand("project") {
    override fun help(context: Context): String = "Show project details"
    private val projectName by argument(help = "Project name")
    private val json by option("--json", help = "Output in JSON format").flag()

    override fun run() {
        val workspaceRoot = findWorkspaceRoot()
        val projectGraph = discoverProjects(workspaceRoot)

        val project = projectGraph.nodes[projectName]
        if (project == null) {
            echo("❌ Project '$projectName' not found", err = true)
            echo("Available projects:")
            projectGraph.getAllProjects().sortedBy { it.name }.forEach { p ->
                echo("  • ${p.name}")
            }
            throw com.github.ajalt.clikt.core.Abort()
        }

        if (json) {
            val projectData = mapOf(
                "name" to project.name,
                "type" to project.data.projectType,
                "root" to project.data.root,
                "sourceRoot" to project.data.sourceRoot,
                "tags" to project.data.tags,
                "targets" to project.data.targets
            )
            echo(com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(projectData))
        } else {
            echo("📦 Project: ${project.name}")
            echo("═".repeat(40))
            echo("Type: ${project.data.projectType}")
            echo("Root: ${project.data.root}")
            if (project.data.sourceRoot != null) {
                echo("Source Root: ${project.data.sourceRoot}")
            }
            if (project.data.tags.isNotEmpty()) {
                echo("Tags: ${project.data.tags.joinToString(", ")}")
            }
            echo()

            if (project.data.targets.isNotEmpty()) {
                echo("🎯 Targets:")
                project.data.targets.forEach { (name, target) ->
                    echo("  • $name")
                    if (target.executor != null) {
                        echo("    Executor: ${target.executor}")
                    }
                    if (target.options.isNotEmpty()) {
                        echo("    Options: ${target.options}")
                    }
                    if (target.dependsOn.isNotEmpty()) {
                        echo("    Depends on: ${target.dependsOn.joinToString(", ")}")
                    }
                }
                echo()
            }

            // Show dependencies
            val dependencies = projectGraph.dependencies[project.name] ?: emptyList()
            if (dependencies.isNotEmpty()) {
                echo("🔗 Dependencies:")
                dependencies.forEach { dep ->
                    echo("  • ${dep.target} (${dep.type.toString().lowercase()})")
                }
            }
        }
    }
}

/**
 * Show project dependency graph
 */
class GraphCommand : CliktCommand() {
    override fun help(context: Context): String = "Show project dependency graph"
    override fun run() {
        echo("🌐 Project dependency graph:")
        echo("═".repeat(40))
        echo()

        val workspaceRoot = findWorkspaceRoot()
        val projectGraph = discoverProjects(workspaceRoot)

        val projects = projectGraph.getAllProjects().sortedBy { it.name }
        projects.forEach { project ->
            val dependencies = projectGraph.dependencies[project.name] ?: emptyList()
            if (dependencies.isNotEmpty()) {
                echo("📦 ${project.name}")
                dependencies.forEach { dep ->
                    echo("  └─ ${dep.target} (${dep.type.toString().lowercase()})")
                }
                echo()
            }
        }

        // Show projects with no dependencies
        val projectsWithoutDeps = projects.filter {
            (projectGraph.dependencies[it.name] ?: emptyList()).isEmpty()
        }

        if (projectsWithoutDeps.isNotEmpty()) {
            echo("📦 Projects with no dependencies:")
            projectsWithoutDeps.forEach { project ->
                echo("  • ${project.name}")
            }
        }
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

private fun discoverProjects(workspaceRoot: Path): com.forge.core.ProjectGraph {
    val inferenceEngine = InferenceEngine()
    val discovery = ProjectDiscovery(workspaceRoot, enableInference = true, inferenceEngine = inferenceEngine)
    return discovery.discoverProjects()
}

private fun discoverProjectsWithConfig(workspaceRoot: Path): Pair<com.forge.core.ProjectGraph, com.forge.core.WorkspaceConfiguration?> {
    val inferenceEngine = InferenceEngine()
    val discovery = ProjectDiscovery(workspaceRoot, enableInference = true, inferenceEngine = inferenceEngine)
    val projectGraph = discovery.discoverProjects()
    
    return projectGraph to discovery.workspaceConfiguration
}

fun main(args: Array<String>) = ForgeCli()
    .subcommands(
        RunCommand(),
        RunManyCommand(),
        ShowCommand().subcommands(
            ShowProjectsCommand(),
            ShowProjectCommand()
        ),
        GraphCommand(),
        PluginCommand()
    )
    .main(args)