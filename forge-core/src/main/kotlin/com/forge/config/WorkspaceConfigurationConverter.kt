package com.forge.config

import com.forge.core.WorkspaceConfiguration as CoreWorkspaceConfiguration
import com.forge.core.RemoteExecutionWorkspaceConfig
import com.forge.plugin.PluginSpec
import com.forge.core.TargetConfiguration

/**
 * Converts between old config format (com.forge.config.WorkspaceConfiguration) 
 * and new format (com.forge.core.WorkspaceConfiguration)
 */
object WorkspaceConfigurationConverter {
    
    /**
     * Convert old format to new format, with support for Remote Execution configuration
     */
    fun convert(oldConfig: WorkspaceConfiguration): CoreWorkspaceConfiguration {
        // Convert plugins from old format to new format
        val pluginSpecs = oldConfig.plugins.map { oldPlugin ->
            // Convert @forge/js format to com.forge.js format
            val id = when {
                oldPlugin.plugin.startsWith("@forge/") -> "com.forge.${oldPlugin.plugin.removePrefix("@forge/")}"
                else -> oldPlugin.plugin
            }
            PluginSpec(
                id = id,
                version = "latest",
                options = oldPlugin.options
            )
        }
        
        // Create Remote Execution config if it exists in the old config
        // Since the old config doesn't have remoteExecution, check if it's stored as an extension
        val remoteExecutionConfig = extractRemoteExecutionConfig(oldConfig)
        
        return CoreWorkspaceConfiguration(
            plugins = pluginSpecs,
            targetDefaults = oldConfig.targetDefaults,
            namedInputs = oldConfig.namedInputs,
            generators = oldConfig.generators,
            tasksRunnerOptions = oldConfig.tasksRunnerOptions,
            affected = com.forge.core.AffectedConfiguration(defaultBase = "main"),
            cli = com.forge.core.CliConfiguration(packageManager = "npm", defaultCollection = "@forge/workspace"),
            remoteExecution = remoteExecutionConfig
        )
    }
    
    /**
     * Try to extract Remote Execution config from the old configuration format
     * This looks for custom extensions or falls back to BuildBarn defaults
     */
    private fun extractRemoteExecutionConfig(oldConfig: WorkspaceConfiguration): RemoteExecutionWorkspaceConfig? {
        // Check if there's a remoteExecution field in generators or other extension points
        val remoteExecutionData = oldConfig.generators["remoteExecution"] as? Map<String, Any>
        
        return if (remoteExecutionData != null) {
            RemoteExecutionWorkspaceConfig(
                enabled = remoteExecutionData["enabled"] as? Boolean ?: false,
                defaultEndpoint = remoteExecutionData["defaultEndpoint"] as? String ?: "127.0.0.1:8980",
                defaultInstanceName = remoteExecutionData["defaultInstanceName"] as? String ?: "default",
                useTls = remoteExecutionData["useTls"] as? Boolean ?: false,
                maxConnections = remoteExecutionData["maxConnections"] as? Int ?: 100,
                defaultTimeoutSeconds = (remoteExecutionData["defaultTimeoutSeconds"] as? Number)?.toLong() ?: 300L,
                defaultPlatform = remoteExecutionData["defaultPlatform"] as? Map<String, String> ?: emptyMap()
            )
        } else {
            // Default BuildBarn configuration for testing
            RemoteExecutionWorkspaceConfig(
                enabled = true,
                defaultEndpoint = "127.0.0.1:8980",
                defaultInstanceName = "default",
                useTls = false,
                maxConnections = 100,
                defaultTimeoutSeconds = 300L,
                defaultPlatform = emptyMap()
            )
        }
    }
}