package com.frontseat.workspace

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.frontseat.plugin.PluginSpec
import com.frontseat.plugin.api.TargetConfiguration
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Configuration for a Forge workspace
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkspaceConfiguration(
    val plugins: List<PluginSpec> = getDefaultPlugins(),
    val namedInputs: Map<String, List<String>> = emptyMap(),
    val targetDefaults: Map<String, TargetConfiguration> = emptyMap(),
    val generators: Map<String, Any> = emptyMap(),
    val tasksRunnerOptions: Map<String, Any> = emptyMap(),
    val affected: AffectedConfiguration = AffectedConfiguration(),
    val cli: CliConfiguration = CliConfiguration(),
    val remoteExecution: RemoteExecutionWorkspaceConfig? = null,
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()
        
        /**
         * Load workspace configuration from frontseat.json file
         */
        fun load(configPath: Path): WorkspaceConfiguration {
            val jsonContent = configPath.readText()
            return objectMapper.readValue(jsonContent)
        }
        
        /**
         * No default plugins - plugins should be explicitly configured
         * The core should not know about specific plugins
         */
        private fun getDefaultPlugins(): List<PluginSpec> = emptyList()
    }
    
    /**
     * Check if Remote Execution is enabled at workspace level
     */
    fun isRemoteExecutionEnabled(): Boolean = remoteExecution?.enabled == true
    
    /**
     * Get Remote Execution configuration as a map
     */
    fun getRemoteExecutionConfig(targetConfig: TargetConfiguration? = null): Map<String, Any>? {
        val workspaceConfig = remoteExecution ?: return null
        val targetRemoteConfig = targetConfig?.remoteExecution
        
        // If target explicitly disables remote execution, return null
        if (targetRemoteConfig?.enabled == false) {
            return null
        }
        
        // If workspace remote execution is disabled and target doesn't enable it, return null
        if (!workspaceConfig.enabled && targetRemoteConfig?.enabled != true) {
            return null
        }
        
        // Build configuration from workspace defaults and target overrides
        val endpoint = targetRemoteConfig?.endpoint ?: workspaceConfig.defaultEndpoint
        val instanceName = targetRemoteConfig?.instanceName ?: workspaceConfig.defaultInstanceName
        val timeoutSeconds = targetRemoteConfig?.timeoutSeconds ?: workspaceConfig.defaultTimeoutSeconds
        val platform = targetRemoteConfig?.platform?.let { targetPlatform ->
            workspaceConfig.defaultPlatform + targetPlatform
        } ?: workspaceConfig.defaultPlatform
        
        return mapOf(
            "endpoint" to endpoint,
            "instanceName" to instanceName,
            "useTls" to workspaceConfig.useTls,
            "maxConnections" to workspaceConfig.maxConnections,
            "timeoutSeconds" to timeoutSeconds,
            "platform" to platform
        )
    }
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
    val defaultCollection: String = "@frontseat/workspace"
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