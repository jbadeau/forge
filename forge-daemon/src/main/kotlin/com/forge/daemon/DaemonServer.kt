package com.forge.daemon

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.config.WorkspaceConfiguration
import com.forge.discovery.ProjectDiscovery
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
                    val dryRun = params["dryRun"] as? Boolean ?: false
                    
                    if (dryRun) {
                        "DRY RUN: Would execute $projectName:$target"
                    } else {
                        // TODO: Implement task execution with current architecture
                        "Task execution not yet implemented in daemon"
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
    
    fun stop() {
        if (!running.get()) return
        
        running.set(false)
        logger.info("Daemon server stopped")
    }
}