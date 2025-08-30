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

data class CreateDependenciesContext(
    val workspaceRoot: Path,
    val projects: Map<String, ProjectConfiguration> = emptyMap(),
    val nxJsonConfiguration: Map<String, Any> = emptyMap(),
    val filesToProcess: Map<String, String> = emptyMap()
)

data class RawProjectGraphDependency(
    val source: String,
    val target: String,
    val type: String = "static",
    val sourceFile: String? = null
)

data class InferenceResult(
    val projects: Map<String, ProjectConfiguration> = emptyMap(),
    val dependencies: List<RawProjectGraphDependency> = emptyList(),
    val externalNodes: Map<String, Any> = emptyMap()
)

typealias CreateNodesFunction<T> = (
    projectConfigurationFiles: List<String>,
    options: T?,
    context: CreateNodesContext
) -> CreateNodesResult

typealias CreateDependenciesFunction<T> = (
    options: T?,
    context: CreateDependenciesContext
) -> List<RawProjectGraphDependency>

interface InferencePlugin<T> {
    val name: String
    val createNodesPattern: String
    val createNodes: CreateNodesFunction<T>
    val defaultOptions: T?
        get() = null
    val createDependencies: CreateDependenciesFunction<T>?
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