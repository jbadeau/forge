package com.frontseat.daemon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.frontseat.project.ProjectGraphBuilder
import com.frontseat.protocol.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modern JSON-RPC 2.0 server with LSP-style protocol.
 * Provides foundation for action graph execution and multi-client support.
 */
class JsonRpcServer {
    private val logger = LoggerFactory.getLogger(JsonRpcServer::class.java)
    private val mapper = jacksonObjectMapper()
    private val running = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    
    // Server state
    private var serverCapabilities: ServerCapabilities? = null
    private val activeRuns = ConcurrentHashMap<String, RunContext>()
    
    data class RunContext(
        val runId: String,
        val workspaceId: String,
        val startTime: Instant,
        val job: Job?
    )
    
    suspend fun start() = withContext(Dispatchers.IO) {
        if (running.get()) {
            logger.warn("JSON-RPC server already running")
            return@withContext
        }
        
        
        running.set(true)
        logger.info("Forge JSON-RPC daemon started")
        
        try {
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
            logger.error("Failed to start JSON-RPC server", e)
            throw e
        }
    }
    
    private suspend fun handleMessage(line: String, output: BufferedWriter) = withContext(Dispatchers.IO) {
        try {
            val request = parseRequest(line)
            val method = when (request) {
                is JsonRpcRequest -> request.method
                is JsonRpcNotification -> request.method
                else -> "unknown"
            }
            
            logger.debug("Processing JSON-RPC request: $method")
            
            val response = when (request) {
                is JsonRpcRequest -> processRequest(request)
                is JsonRpcNotification -> {
                    processNotification(request)
                    null
                }
                else -> null
            }
            
            response?.let { 
                sendResponse(it, output) 
            }
            
        } catch (e: Exception) {
            logger.error("Error handling JSON-RPC message", e)
            
            val errorResponse = JsonRpcResponse(
                id = JsonRpcId.NullId,
                error = JsonRpcError(
                    code = ForgeErrorCodes.PARSE_ERROR,
                    message = "Parse error: ${e.message}"
                )
            )
            sendResponse(errorResponse, output)
        }
    }
    
    private fun parseRequest(line: String): Any {
        val json = mapper.readTree(line)
        
        return if (json.has("id")) {
            JsonRpcRequest(
                id = parseJsonRpcId(json.get("id")),
                method = json.get("method").textValue(),
                params = if (json.has("params")) json.get("params") else null
            )
        } else {
            JsonRpcNotification(
                method = json.get("method").textValue(),
                params = if (json.has("params")) json.get("params") else null
            )
        }
    }
    
    private fun parseJsonRpcId(idNode: JsonNode): JsonRpcId {
        return when {
            idNode.isNull -> JsonRpcId.NullId
            idNode.isTextual -> JsonRpcId.StringId(idNode.textValue())
            idNode.isNumber -> JsonRpcId.IntId(idNode.intValue())
            else -> JsonRpcId.NullId
        }
    }
    
    private suspend fun processRequest(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val result = when (request.method) {
                ForgeProtocol.INITIALIZE -> handleInitialize(request.params)
                ForgeProtocol.SHUTDOWN -> handleShutdown()
                ForgeProtocol.WORKSPACE_SCAN -> handleWorkspaceScan(request.params)
                ForgeProtocol.RUN_START -> handleRunStart(request.params)
                ForgeProtocol.RUN_STATUS -> handleRunStatus(request.params)
                
                else -> throw RuntimeException("Method not found: ${request.method}")
            }
            
            JsonRpcResponse(id = request.id, result = result)
            
        } catch (e: Exception) {
            logger.error("Error processing request ${request.method}", e)
            
            val errorCode = when (e) {
                is IllegalArgumentException -> ForgeErrorCodes.INVALID_PARAMS
                is RuntimeException -> if (e.message?.contains("not found") == true) {
                    ForgeErrorCodes.METHOD_NOT_FOUND
                } else {
                    ForgeErrorCodes.INTERNAL_ERROR
                }
                else -> ForgeErrorCodes.INTERNAL_ERROR
            }
            
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(errorCode, e.message ?: "Internal error")
            )
        }
    }
    
    private suspend fun processNotification(notification: JsonRpcNotification) {
        try {
            when (notification.method) {
                ForgeProtocol.INITIALIZED -> logger.info("Client initialized")
                ForgeProtocol.WORKSPACE_DID_CHANGE -> logger.debug("Workspace changed")
                else -> logger.warn("Unknown notification: ${notification.method}")
            }
        } catch (e: Exception) {
            logger.error("Error processing notification ${notification.method}", e)
        }
    }
    
    // Protocol method handlers
    private suspend fun handleInitialize(params: Any?): InitializeResult {
        if (initialized.get()) {
            throw RuntimeException("Server already initialized")
        }
        
        serverCapabilities = ServerCapabilities(
            features = ServerFeatures(
                tools = ToolFeatures(nix = false, system = true),
                cache = CacheFeatures(local = true),
                graph = GraphFeatures(fingerprints = true, advice = false),
                artifacts = ArtifactFeatures(fingerprinting = true, provenance = false)
            )
        )
        
        initialized.set(true)
        logger.info("JSON-RPC server initialized")
        
        return InitializeResult(
            serverInfo = ServerInfo(version = "1.0.0"),
            capabilities = serverCapabilities!!
        )
    }
    
    private fun handleShutdown(): String {
        logger.info("Shutdown requested")
        stop()
        return "Server shutting down"
    }
    
    private suspend fun handleWorkspaceScan(params: Any?): Map<String, Any> {
        val workspaceRoot = Paths.get("").toAbsolutePath()
        val graphBuilder = ProjectGraphBuilder(workspaceRoot)
        val projectGraph = graphBuilder.buildProjectGraph()
        
        return mapOf(
            "projects" to projectGraph.nodes.size,
            "scannedAt" to Instant.now().toString()
        )
    }
    
    private suspend fun handleRunStart(params: Any?): RunStartResult {
        val runId = "run-${System.currentTimeMillis()}"
        
        // For now, create a simple run context without actual execution
        activeRuns[runId] = RunContext(runId, "workspace-1", Instant.now(), null)
        
        logger.info("Run queued: runId=$runId")
        
        return RunStartResult(
            runId = runId,
            graphId = "graph-${System.currentTimeMillis()}",
                totalActions = 1 // Placeholder
            )
        }
    
    private fun handleRunStatus(params: Any?): Map<String, Any> {
        return mapOf(
            "activeRuns" to activeRuns.keys.toList(),
            "totalRuns" to activeRuns.size,
            "serverVersion" to "1.0.0"
        )
    }
    
    
    private suspend fun sendResponse(response: JsonRpcResponse, output: BufferedWriter) = withContext(Dispatchers.IO) {
        synchronized(output) {
            val responseLine = mapper.writeValueAsString(response)
            output.write(responseLine)
            output.newLine()
            output.flush()
        }
    }
    
    fun stop() {
        if (!running.get()) return
        
        activeRuns.clear()
        running.set(false)
        logger.info("JSON-RPC server stopped")
    }
}