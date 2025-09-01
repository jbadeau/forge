package com.forge.plugin.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Workspace configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkspaceConfiguration(
    val version: Int = 1,
    val targetDefaults: Map<String, TargetDefault> = emptyMap(),
    val namedInputs: Map<String, List<String>> = emptyMap(),
    val plugins: List<PluginConfiguration> = emptyList()
)

/**
 * Plugin configuration in workspace
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginConfiguration(
    val plugin: String,
    val options: Map<String, Any> = emptyMap()
)

/**
 * Target default configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TargetDefault(
    val dependsOn: List<String> = emptyList(),
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    val cache: Boolean = true
)