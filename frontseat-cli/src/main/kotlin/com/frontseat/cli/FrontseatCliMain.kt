package com.frontseat.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.frontseat.client.DaemonClient
import com.frontseat.client.JsonRpcResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Paths
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Main CLI class for Frontseat build tool
 */
class FrontseatCli : CliktCommand() {
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

/**
 * Generate plugins from OpenAPI specifications
 */
class GenerateCommand : CliktCommand() {
    override fun help(context: Context): String = """
    Generate Forge plugins from OpenAPI specifications.
    
    Example: forge generate --file react-plugin.yaml
    """.trimIndent()
    
    private val file by option("--file", "-f", help = "OpenAPI specification file (.yaml or .json)")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
    private val outputDir by option("--output-dir", "-o", help = "Output directory for generated plugin")
        .default("./generated-plugins")
    private val packageName by option("--package", "-p", help = "Base package name for generated classes")
        .default("com.frontseat.plugin.generated")
    private val pluginName by option("--name", "-n", help = "Plugin name (auto-detected from spec if not provided)")
    
    override fun run() {
        when {
            file == null -> {
                echo("❌ OpenAPI specification file is required. Use --file option.", err = true)
                echo("Example: forge generate --file react-plugin.yaml", err = true)
                exitProcess(1)
            }
            else -> {
                generatePlugin()
            }
        }
    }
    
    private fun generatePlugin() {
        val specFile = file!!
        echo("🔍 Reading OpenAPI specification: ${specFile.absolutePath}")
        
        try {
            // Parse and validate the OpenAPI spec
            val objectMapper = ObjectMapper(YAMLFactory())
            val specContent = Files.readString(specFile.toPath())
            val spec = objectMapper.readTree(specContent)
            
            val actualPluginName = pluginName ?: specFile.nameWithoutExtension.replace("-plugin", "")
            val outputPath = Paths.get(outputDir).resolve("$actualPluginName-plugin")
            
            echo("⚙️  Analyzing plugin specification for '$actualPluginName'...")
            
            // Extract plugin information
            val pluginInfo = spec.get("info")
            val pluginTitle = pluginInfo?.get("title")?.asText() ?: actualPluginName
            val pluginVersion = pluginInfo?.get("version")?.asText() ?: "1.0.0"
            val pluginDescription = pluginInfo?.get("description")?.asText() ?: "Generated from OpenAPI spec"
            
            val forgeExtension = spec.get("x-frontseat-plugin")
            val pluginId = forgeExtension?.get("id")?.asText() ?: "com.frontseat.plugin.${actualPluginName.lowercase()}"
            
            // Analyze paths to determine plugin capabilities
            val paths = spec.get("paths") ?: objectMapper.createObjectNode()
            val executors = mutableListOf<String>()
            val generators = mutableListOf<String>() 
            val initializers = mutableListOf<String>()
            val migrators = mutableListOf<String>()
            
            paths.fieldNames().forEach { path ->
                when {
                    path.startsWith("/executors/") -> {
                        val name = path.removePrefix("/executors/").split("/").first()
                        if (name !in executors) executors.add(name)
                    }
                    path.startsWith("/generators/") -> {
                        val name = path.removePrefix("/generators/").split("/").first()
                        if (name !in generators) generators.add(name)
                    }
                    path.startsWith("/initializers/") -> {
                        val name = path.removePrefix("/initializers/").split("/").first()
                        if (name !in initializers) initializers.add(name)
                    }
                    path.startsWith("/migrators/") -> {
                        val name = path.removePrefix("/migrators/").split("/").first()
                        if (name !in migrators) migrators.add(name)
                    }
                }
            }
            
            echo("📋 Plugin Analysis:")
            echo("   Title: $pluginTitle")
            echo("   ID: $pluginId") 
            echo("   Version: $pluginVersion")
            echo("   Description: $pluginDescription")
            echo("")
            
            val totalComponents = executors.size + generators.size + initializers.size + migrators.size
            if (totalComponents == 0) {
                echo("⚠️  No plugin capabilities found in specification")
                echo("   Expected paths like /executors/*, /generators/*, /initializers/*, /migrators/*")
                return
            }
            
            echo("🔧 Found ${totalComponents} plugin capabilities:")
            if (executors.isNotEmpty()) {
                echo("   📦 ${executors.size} executor(s): ${executors.joinToString(", ")}")
            }
            if (generators.isNotEmpty()) {
                echo("   🎨 ${generators.size} generator(s): ${generators.joinToString(", ")}")
            }
            if (initializers.isNotEmpty()) {
                echo("   🚀 ${initializers.size} initializer(s): ${initializers.joinToString(", ")}")
            }
            if (migrators.isNotEmpty()) {
                echo("   🔄 ${migrators.size} migrator(s): ${migrators.joinToString(", ")}")
            }
            
            echo("")
            echo("📁 Output directory: $outputPath")
            
            // Create output structure (for now just show what would be created)
            echo("🏗️  Plugin structure (to be generated):")
            echo("   ${outputPath}/")
            echo("   ├── pom.xml")
            echo("   ├── README.md")
            echo("   └── src/main/kotlin/${packageName.replace('.', '/')}/")
            echo("       ├── ${actualPluginName.replaceFirstChar { it.uppercaseChar() }}Plugin.kt")
            
            if (executors.isNotEmpty()) {
                echo("       ├── executors/")
                executors.forEach { executor ->
                    echo("       │   └── ${executor.replaceFirstChar { it.uppercaseChar() }}Executor.kt")
                }
            }
            if (generators.isNotEmpty()) {
                echo("       ├── generators/")
                generators.forEach { generator ->
                    echo("       │   └── ${generator.replaceFirstChar { it.uppercaseChar() }}Generator.kt")
                }
            }
            if (initializers.isNotEmpty()) {
                echo("       ├── initializers/")
                initializers.forEach { initializer ->
                    echo("       │   └── ${initializer.replaceFirstChar { it.uppercaseChar() }}Initializer.kt")
                }
            }
            if (migrators.isNotEmpty()) {
                echo("       └── migrators/")
                migrators.forEach { migrator ->
                    echo("           └── ${migrator.replaceFirstChar { it.uppercaseChar() }}Migrator.kt")
                }
            }
            
            echo("")
            echo("✅ OpenAPI specification validated successfully!")
            echo("")
            echo("📝 Next steps to complete plugin generation:")
            echo("1. Provide custom OpenAPI generators for Forge plugin components:")
            echo("   - forge-executor-generator")
            echo("   - forge-generator-generator") 
            echo("   - forge-initializer-generator")
            echo("   - forge-migrator-generator")
            echo("   - frontseat-plugin-generator")
            echo("")
            echo("2. Use OpenAPI Generator CLI with your custom generators:")
            echo("   openapi-generator generate -g frontseat-plugin-generator -i ${specFile.absolutePath} -o $outputPath")
            echo("")
            echo("3. Once generated, build and install:")
            echo("   mvn clean install -f $outputPath/pom.xml")
            echo("   forge plugin install $outputPath/target/$actualPluginName-plugin-*.jar")
            
        } catch (e: Exception) {
            echo("❌ Error parsing OpenAPI specification: ${e.message}", err = true)
            echo("   Make sure the file is a valid OpenAPI 3.x specification", err = true)
            exitProcess(1)
        }
    }
}

fun main(args: Array<String>) = FrontseatCli()
    .subcommands(
        RunCommand(),
        RunManyCommand(),
        ShowCommand().subcommands(
            ShowProjectsCommand(),
            ShowProjectCommand()
        ),
        GraphCommand(),
        GenerateCommand()
    )
    .main(args)