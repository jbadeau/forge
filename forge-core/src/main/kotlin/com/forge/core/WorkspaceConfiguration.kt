package com.forge.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.plugin.PluginSpec
import com.forge.plugin.PluginSource
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
    val cli: CliConfiguration = CliConfiguration()
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()
        
        /**
         * Load workspace configuration from forge.json file
         */
        fun load(configPath: Path): WorkspaceConfiguration {
            val jsonContent = configPath.readText()
            val jsonNode = objectMapper.readTree(jsonContent)
            
            // Handle legacy format
            if (jsonNode.has("plugins") && jsonNode["plugins"].isArray) {
                val pluginsArray = jsonNode["plugins"]
                val plugins = mutableListOf<PluginSpec>()
                
                pluginsArray.forEach { pluginNode ->
                    try {
                        if (pluginNode.has("plugin")) {
                            // Legacy format: { "plugin": "@forge/js", "options": {...} }
                            val pluginId = pluginNode["plugin"].asText()
                            val options = if (pluginNode.has("options")) {
                                objectMapper.convertValue(pluginNode["options"], Map::class.java) as Map<String, Any>
                            } else {
                                emptyMap()
                            }
                            
                            // Convert @forge/js to com.forge.js
                            val normalizedId = if (pluginId.startsWith("@forge/")) {
                                "com.forge." + pluginId.removePrefix("@forge/")
                            } else {
                                pluginId
                            }
                            
                            plugins.add(PluginSpec(
                                id = normalizedId,
                                version = "latest",
                                source = PluginSource.MAVEN,
                                options = options
                            ))
                        } else if (pluginNode.has("id")) {
                            // New format: { "id": "com.forge.js", "version": "1.0.0", ... }
                            plugins.add(objectMapper.convertValue(pluginNode, PluginSpec::class.java))
                        }
                    } catch (e: Exception) {
                        // Skip invalid plugin configurations
                        println("Warning: Skipping invalid plugin configuration: ${pluginNode}")
                    }
                }
                
                // Parse other fields
                val targetDefaults = if (jsonNode.has("targetDefaults")) {
                    objectMapper.convertValue(jsonNode["targetDefaults"], Map::class.java) as Map<String, TargetConfiguration>
                } else {
                    emptyMap()
                }
                
                return WorkspaceConfiguration(
                    plugins = plugins,
                    targetDefaults = targetDefaults
                )
            } else {
                // Standard format
                return objectMapper.readValue(jsonContent)
            }
        }
        
        /**
         * Default plugins that are always available
         */
        private fun getDefaultPlugins(): List<PluginSpec> = listOf(
            PluginSpec(
                id = "com.forge.js",
                version = "latest",
                options = mapOf(
                    "buildTargetName" to "build",
                    "testTargetName" to "test",
                    "lintTargetName" to "lint"
                )
            ),
            PluginSpec(
                id = "com.forge.maven", 
                version = "latest",
                options = mapOf(
                    "buildTargetName" to "compile",
                    "testTargetName" to "test",
                    "packageTargetName" to "package"
                )
            ),
            PluginSpec(
                id = "com.forge.go",
                version = "latest",
                options = mapOf(
                    "buildTargetName" to "build",
                    "testTargetName" to "test"
                )
            ),
            PluginSpec(
                id = "com.forge.docker",
                version = "latest",
                options = mapOf(
                    "buildTargetName" to "docker-build",
                    "runTargetName" to "docker-run",
                    "pushTargetName" to "docker-push"
                )
            )
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
    val defaultCollection: String = "@forge/workspace"
)