package com.forge.daemon

/**
 * Protocol definitions for daemon communication
 */
data class DaemonRequest(
    val command: String,
    val args: List<String> = emptyList(),
    val options: Map<String, Any> = emptyMap()
)

data class DaemonResponse(
    val success: Boolean,
    val result: Any? = null,
    val error: String? = null
)

/**
 * Daemon commands
 */
object DaemonCommands {
    const val PING = "ping"
    const val SHUTDOWN = "shutdown"
    const val RUN_TASK = "run-task"
    const val SHOW_PROJECTS = "show-projects"
    const val SHOW_PROJECT = "show-project"
    const val RUN_MANY = "run-many"
    const val PROJECT_GRAPH = "project-graph"
}