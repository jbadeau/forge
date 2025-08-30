package com.forge.inference

import com.forge.core.ProjectConfiguration
import java.nio.file.Path

data class CreateNodesContext(
    val workspaceRoot: Path,
    val nxJsonConfiguration: Map<String, Any> = emptyMap(),
    val configFiles: Map<String, Any> = emptyMap()
)

data class CreateNodesResult(
    val projects: Map<String, ProjectConfiguration> = emptyMap(),
    val externalNodes: Map<String, Any> = emptyMap()
)

typealias CreateNodesFunction<T> = (
    projectConfigurationFiles: List<String>,
    options: T?,
    context: CreateNodesContext
) -> CreateNodesResult

interface InferencePlugin<T> {
    val name: String
    val createNodesPattern: String
    val createNodes: CreateNodesFunction<T>
    val defaultOptions: T?
        get() = null
}

class InferencePluginRegistry {
    private val plugins = mutableListOf<InferencePlugin<*>>()
    
    fun <T> register(plugin: InferencePlugin<T>) {
        plugins.add(plugin)
    }
    
    fun getPlugins(): List<InferencePlugin<*>> = plugins.toList()
    
    fun getPluginByName(name: String): InferencePlugin<*>? =
        plugins.find { it.name == name }
}