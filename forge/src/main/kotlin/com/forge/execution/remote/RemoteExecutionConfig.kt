package com.forge.execution.remote

/**
 * Configuration for Remote Execution endpoint
 */
data class RemoteExecutionConfig(
    val endpoint: String = "localhost:8080",
    val instanceName: String = "",
    val useTls: Boolean = false,
    val maxConnections: Int = 100,
    val timeoutSeconds: Long = 60,
    val platform: Map<String, String> = emptyMap()
)