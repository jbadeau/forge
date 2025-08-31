package com.forge.daemon

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.core.WorkspaceConfiguration
import com.forge.discovery.ProjectDiscovery
import com.forge.execution.ExecutorFactory
import com.forge.graph.Task
import com.forge.graph.TaskExecutionPlan
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists

/**
 * JSON-RPC 2.0 message format (LSP-style)
 */
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val method: String,
    val params: Any?
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Any?
)

class DaemonServer {
    private val logger = LoggerFactory.getLogger(DaemonServer::class.java)
    private val mapper = jacksonObjectMapper()
    private val running = AtomicBoolean(false)
    private val requestId = AtomicInteger(0)
    
    suspend fun start() = withContext(Dispatchers.IO) {
        if (running.get()) {
            logger.warn("Daemon server already running")
            return@withContext
        }
        
        running.set(true)
        logger.info("Forge daemon started (LSP-style stdio)")
        
        try {
            // Read from stdin, write to stdout (LSP-style)
            val input = BufferedReader(InputStreamReader(System.`in`))
            val output = BufferedWriter(OutputStreamWriter(System.out))
            
            while (running.get()) {
                try {
                    val line = input.readLine() ?: break
                    if (line.trim().isEmpty()) continue
                    
                    launch {
                        handleMessage(line, output)
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        logger.error("Error reading message", e)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to start daemon server", e)
            throw e
        }
    }
    
    private suspend fun handleMessage(line: String, output: BufferedWriter) = withContext(Dispatchers.IO) {
        try {
            val jsonRpc = mapper.readValue<JsonRpcRequest>(line)
            logger.debug("Received JSON-RPC request: ${jsonRpc.method}")
            
            val response = processJsonRpcRequest(jsonRpc)
            synchronized(output) {
                val responseLine = mapper.writeValueAsString(response)
                output.write(responseLine)
                output.newLine()
                output.flush()
            }
        } catch (e: Exception) {
            logger.error("Error handling message", e)
            val errorResponse = JsonRpcResponse(
                id = null,
                error = JsonRpcError(-32700, "Parse error: ${e.message}")
            )
            synchronized(output) {
                val responseLine = mapper.writeValueAsString(errorResponse)
                output.write(responseLine)
                output.newLine()
                output.flush()
            }
        }
    }
    
    private suspend fun processJsonRpcRequest(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val result = when (request.method) {
                "ping" -> "pong"
                
                "shutdown" -> {
                    stop()
                    "Daemon shutting down"
                }
                
                "show/projects" -> {
                    val params = request.params as? Map<*, *> ?: emptyMap<String, Any>()
                    val workspaceRoot = params["workspaceRoot"] as? String ?: Paths.get("").toAbsolutePath().toString()
                    val format = params["format"] as? String ?: "text"
                    
                    val discovery = createProjectDiscovery(workspaceRoot)
                    val projectGraph = discovery.discoverProjects()
                    val projects = projectGraph.nodes.values
                    if (format == "json") {
                        projects.map { 
                            mapOf(
                                "name" to it.name,
                                "root" to it.data.root,
                                "type" to it.data.projectType,
                                "tags" to it.data.tags
                            )
                        }
                    } else {
                        projects.joinToString("\n") { "${it.name} (${it.data.projectType})" }
                    }
                }
                
                "show/project" -> {
                    val params = request.params as? Map<*, *> ?: emptyMap<String, Any>()
                    val workspaceRoot = params["workspaceRoot"] as? String ?: Paths.get("").toAbsolutePath().toString()
                    val projectName = params["projectName"] as? String ?: throw IllegalArgumentException("projectName required")
                    val format = params["format"] as? String ?: "text"
                    
                    val discovery = createProjectDiscovery(workspaceRoot)
                    val projectGraph = discovery.discoverProjects()
                    val project = projectGraph.nodes[projectName] ?: throw RuntimeException("Project '$projectName' not found")
                    
                    if (format == "json") {
                        mapOf(
                            "name" to project.name,
                            "root" to project.data.root,
                            "type" to project.data.projectType,
                            "tags" to project.data.tags,
                            "targets" to project.data.targets.keys.toList()
                        )
                    } else {
                        buildString {
                            appendLine("Project: ${project.name}")
                            appendLine("Type: ${project.data.projectType}")
                            appendLine("Root: ${project.data.root}")
                            if (project.data.tags.isNotEmpty()) {
                                appendLine("Tags: ${project.data.tags.joinToString(", ")}")
                            }
                            appendLine("Targets: ${project.data.targets.keys.joinToString(", ")}")
                        }
                    }
                }
                
                "run/task" -> {
                    val params = request.params as? Map<*, *> ?: emptyMap<String, Any>()
                    val workspaceRoot = params["workspaceRoot"] as? String ?: Paths.get("").toAbsolutePath().toString()
                    val projectName = params["projectName"] as? String ?: throw IllegalArgumentException("projectName required")
                    val target = params["target"] as? String ?: throw IllegalArgumentException("target required")
                    val verbose = params["verbose"] as? Boolean ?: false
                    val dryRun = params["dryRun"] as? Boolean ?: false
                    
                    if (dryRun) {
                        "DRY RUN: Would execute $projectName:$target"
                    } else {
                        executeTask(workspaceRoot, projectName, target, verbose)
                    }
                }
                
                "run/many" -> {
                    val params = request.params as? Map<*, *> ?: emptyMap<String, Any>()
                    val workspaceRoot = params["workspaceRoot"] as? String ?: Paths.get("").toAbsolutePath().toString()
                    val target = params["target"] as? String ?: throw IllegalArgumentException("target required")
                    val tags = (params["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val all = params["all"] as? Boolean ?: false
                    val dryRun = params["dryRun"] as? Boolean ?: false
                    
                    val discovery = createProjectDiscovery(workspaceRoot)
                    val projectGraph = discovery.discoverProjects()
                    
                    if (dryRun) {
                        val projects = if (all) projectGraph.nodes.values else {
                            projectGraph.nodes.values.filter { project ->
                                tags.isEmpty() || tags.any { tag -> project.data.tags.contains(tag) }
                            }
                        }
                        val filteredProjects = projects.filter { it.data.targets.containsKey(target) }
                        mapOf(
                            "message" to "DRY RUN: Would execute $target on:",
                            "projects" to filteredProjects.map { it.name }
                        )
                    } else {
                        // TODO: Implement task execution with current architecture
                        mapOf("message" to "Run many execution not yet implemented in daemon")
                    }
                }
                
                "project/graph" -> {
                    val params = request.params as? Map<*, *> ?: emptyMap<String, Any>()
                    val workspaceRoot = params["workspaceRoot"] as? String ?: Paths.get("").toAbsolutePath().toString()
                    val format = params["format"] as? String ?: "text"
                    
                    val discovery = createProjectDiscovery(workspaceRoot)
                    val projectGraph = discovery.discoverProjects()
                    
                    if (format == "json") {
                        // Convert dependencies map to a simple format for JSON
                        projectGraph.dependencies.mapValues { it.value.map { dep -> dep.target } }
                    } else {
                        buildString {
                            for ((project, deps) in projectGraph.dependencies) {
                                val targets = deps.map { it.target }
                                if (targets.isNotEmpty()) {
                                    appendLine("$project -> ${targets.joinToString(", ")}")
                                }
                            }
                        }
                    }
                }
                
                else -> throw RuntimeException("Unknown method: ${request.method}")
            }
            
            JsonRpcResponse(id = request.id, result = result)
            
        } catch (e: Exception) {
            logger.error("Error processing JSON-RPC request", e)
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(-32603, "Internal error: ${e.message}")
            )
        }
    }
    
    private fun createProjectDiscovery(workspaceRoot: String): ProjectDiscovery {
        val workspaceRootPath = Paths.get(workspaceRoot)
        return ProjectDiscovery(workspaceRootPath)
    }
    
    private fun loadWorkspaceConfiguration(workspaceRoot: Path): WorkspaceConfiguration {
        val forgeConfigPath = workspaceRoot.resolve("forge.json")
        val nxConfigPath = workspaceRoot.resolve("nx.json")
        
        return when {
            forgeConfigPath.exists() -> {
                logger.debug("Loading forge.json configuration")
                WorkspaceConfiguration.load(forgeConfigPath)
            }
            nxConfigPath.exists() -> {
                logger.debug("Loading nx.json configuration (compatibility mode)")
                WorkspaceConfiguration.load(nxConfigPath)
            }
            else -> {
                logger.debug("No workspace configuration found, using defaults")
                WorkspaceConfiguration()
            }
        }
    }
    
    private suspend fun executeTask(
        workspaceRoot: String,
        projectName: String,
        target: String,
        verbose: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            val path = Paths.get(workspaceRoot)
            val discovery = ProjectDiscovery(path)
            val projectGraph = discovery.discoverProjects()
            
            // Find the project
            val project = projectGraph.nodes[projectName]
                ?: return@withContext "Error: Project '$projectName' not found"
            
            // Check if target exists
            if (!project.data.targets.containsKey(target)) {
                return@withContext "Error: Target '$target' not found in project '$projectName'"
            }
            
            // Create a Task for the requested target
            val task = Task(
                id = "$projectName:$target",
                projectName = projectName,
                targetName = target,
                target = project.data.targets[target]!!
            )
            
            // Create single-layer execution plan
            val executionPlan = TaskExecutionPlan(
                layers = listOf(listOf(task))
            )
            
            // Load workspace configuration to get Remote Execution settings
            val workspaceConfig = loadWorkspaceConfiguration(path)
            
            // Create executor with Remote Execution support
            val executor = ExecutorFactory.createExecutor(
                workspaceRoot = path,
                projectGraph = projectGraph,
                workspaceConfig = workspaceConfig
            )
            
            logger.info("Executing task $projectName:$target")
            
            // Execute the task
            val results = executor.execute(executionPlan, verbose)
            
            // Close executor if it's closeable
            if (executor is AutoCloseable) {
                executor.close()
            }
            
            // Get the task result
            val taskResult = results.results[task.id]
            
            if (taskResult != null) {
                val output = taskResult.output
                if (taskResult.isSuccess) {
                    "Task $projectName:$target completed successfully${if (output.isNotBlank()) "\nOutput:\n$output" else ""}"
                } else {
                    val error = taskResult.error
                    "Task $projectName:$target failed: $error${if (output.isNotBlank()) "\nOutput:\n$output" else ""}"
                }
            } else {
                "Task $projectName:$target: No result available"
            }
        } catch (e: Exception) {
            logger.error("Error executing task $projectName:$target", e)
            "Error executing task: ${e.message}"
        }
    }
    
    fun stop() {
        if (!running.get()) return
        
        running.set(false)
        logger.info("Daemon server stopped")
    }
}