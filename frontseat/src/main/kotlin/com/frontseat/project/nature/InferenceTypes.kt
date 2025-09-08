package com.frontseat.project.nature

import com.frontseat.task.CommandTask
import java.nio.file.Path

/**
 * Result of project inference process
 */
data class InferenceResult(
    val projects: Map<String, Map<String, CommandTask>>,
    val dependencies: List<RawProjectGraphDependency>,
    val externalNodes: Map<String, Any> = emptyMap()
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
 * Dependency types for inference
 */
enum class DependencyType {
    STATIC,
    IMPLICIT
}