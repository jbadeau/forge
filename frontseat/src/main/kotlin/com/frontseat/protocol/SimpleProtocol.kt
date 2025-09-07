package com.frontseat.protocol

import com.fasterxml.jackson.databind.JsonNode

/**
 * Simple JSON-RPC 2.0 protocol definitions for enhanced daemon
 */

// Core JSON-RPC 2.0 Types
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonRpcId,
    val method: String,
    val params: Any?
)

data class JsonRpcNotification(
    val jsonrpc: String = "2.0", 
    val method: String,
    val params: Any?
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonRpcId,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

sealed interface JsonRpcId {
    data class StringId(val value: String) : JsonRpcId
    data class IntId(val value: Int) : JsonRpcId
    object NullId : JsonRpcId
}

// Protocol Methods
object ForgeProtocol {
    // Lifecycle
    const val INITIALIZE = "initialize"
    const val INITIALIZED = "initialized"
    const val SHUTDOWN = "shutdown"
    
    // Workspace
    const val WORKSPACE_SCAN = "workspace/scan"
    const val WORKSPACE_DID_CHANGE = "workspace/didChange"
    
    // Execution
    const val RUN_START = "run/start"
    const val RUN_STATUS = "run/status"
    const val RUN_STOP = "run/stop"
}

// Error Codes
object ForgeErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

// Basic Protocol Types
data class InitializeResult(
    val serverInfo: ServerInfo,
    val capabilities: ServerCapabilities
)

data class ServerInfo(
    val name: String = "frontseat",
    val version: String
)

data class ServerCapabilities(
    val protocolVersion: String = "frontseat/1.0",
    val features: ServerFeatures
)

data class ServerFeatures(
    val tools: ToolFeatures,
    val cache: CacheFeatures,
    val graph: GraphFeatures,
    val artifacts: ArtifactFeatures
)

data class ToolFeatures(
    val nix: Boolean = false,
    val system: Boolean = true
)

data class CacheFeatures(
    val local: Boolean = true
)

data class GraphFeatures(
    val fingerprints: Boolean = false,
    val advice: Boolean = false
)

data class ArtifactFeatures(
    val fingerprinting: Boolean = false,
    val provenance: Boolean = false
)

data class RunStartResult(
    val runId: String,
    val graphId: String,
    val totalActions: Int
)