package com.forge.workspace

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Configuration for a Forge workspace
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkspaceConfiguration(
    val version: Int = 1,
    val namedInputs: Map<String, List<String>> = emptyMap(),
    val targetDefaults: Map<String, TargetConfiguration> = emptyMap(),
    val generators: Map<String, Any> = emptyMap(),
    val tasksRunnerOptions: Map<String, Any> = emptyMap(),
    val affected: AffectedConfiguration = AffectedConfiguration(),
    val cli: CliConfiguration = CliConfiguration(),
    val remoteExecution: RemoteExecutionWorkspaceConfig? = null
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()
        
        /**
         * Load workspace configuration from forge.json file
         */
        fun load(configPath: Path): WorkspaceConfiguration {
            val jsonContent = configPath.readText()
            return objectMapper.readValue(jsonContent)
        }
        
    }
    
    /**
     * Check if Remote Execution is enabled at workspace level
     */
    fun isRemoteExecutionEnabled(): Boolean = remoteExecution?.enabled == true
    
}

/**
 * Configuration for affected command
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AffectedConfiguration(
    val defaultBase: String = "main"
)

/**
 * Configuration for CLI behavior
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CliConfiguration(
    val packageManager: String = "npm",
    val defaultCollection: String = "@forge/workspace"
)

/**
 * Workspace-level Remote Execution configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteExecutionWorkspaceConfig(
    val enabled: Boolean = false,
    val defaultEndpoint: String = "localhost:8080",
    val defaultInstanceName: String = "",
    val useTls: Boolean = false,
    val maxConnections: Int = 100,
    val defaultTimeoutSeconds: Long = 300,
    val defaultPlatform: Map<String, String> = emptyMap(),
    val endpoints: Map<String, RemoteExecutionEndpointConfig> = emptyMap()
)

/**
 * Configuration for a specific Remote Execution endpoint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteExecutionEndpointConfig(
    val endpoint: String,
    val instanceName: String = "",
    val useTls: Boolean = false,
    val maxConnections: Int = 100,
    val timeoutSeconds: Long = 300,
    val platform: Map<String, String> = emptyMap()
)