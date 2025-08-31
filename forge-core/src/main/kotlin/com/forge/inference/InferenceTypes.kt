package com.forge.inference

import com.forge.core.DependencyType
import java.nio.file.Path

/**
 * Context provided to plugins during node creation
 */
data class CreateNodesContext(
    val workspaceRoot: Path,
    val nxJsonConfiguration: Map<String, Any> = emptyMap()
)

/**
 * Result of node creation by plugins
 */
data class CreateNodesResult(
    val projects: Map<String, com.forge.core.ProjectConfiguration> = emptyMap(),
    val externalNodes: Map<String, Any> = emptyMap()
)

/**
 * Context provided to plugins during dependency creation
 */
data class CreateDependenciesContext(
    val workspaceRoot: Path,
    val projects: Map<String, com.forge.core.ProjectConfiguration>,
    val nxJsonConfiguration: Map<String, Any> = emptyMap()
)

/**
 * Represents a raw dependency between projects in the workspace
 */
data class RawProjectGraphDependency(
    val source: String,
    val target: String,
    val type: DependencyType,
    val sourceFile: String? = null
)


/**
 * Result of inference process
 */
data class InferenceResult(
    val projects: Map<String, com.forge.core.ProjectConfiguration>,
    val dependencies: List<RawProjectGraphDependency>,
    val externalNodes: Map<String, Any> = emptyMap()
)