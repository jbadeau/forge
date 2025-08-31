package com.forge.daemon

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Protocol definitions for communication between CLI and daemon
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DaemonRequest.ShowProjects::class, name = "show_projects"),
    JsonSubTypes.Type(value = DaemonRequest.ShowProject::class, name = "show_project"),
    JsonSubTypes.Type(value = DaemonRequest.RunTask::class, name = "run_task"),
    JsonSubTypes.Type(value = DaemonRequest.RunMany::class, name = "run_many"),
    JsonSubTypes.Type(value = DaemonRequest.Graph::class, name = "graph"),
    JsonSubTypes.Type(value = DaemonRequest.Ping::class, name = "ping"),
    JsonSubTypes.Type(value = DaemonRequest.Shutdown::class, name = "shutdown")
)
sealed class DaemonRequest {
    data class ShowProjects(
        val workspaceRoot: String,
        val format: String = "text"
    ) : DaemonRequest()
    
    data class ShowProject(
        val workspaceRoot: String,
        val projectName: String,
        val format: String = "text"
    ) : DaemonRequest()
    
    data class RunTask(
        val workspaceRoot: String,
        val projectName: String,
        val target: String,
        val verbose: Boolean = false,
        val dryRun: Boolean = false
    ) : DaemonRequest()
    
    data class RunMany(
        val workspaceRoot: String,
        val target: String,
        val tags: List<String> = emptyList(),
        val all: Boolean = false,
        val verbose: Boolean = false,
        val dryRun: Boolean = false
    ) : DaemonRequest()
    
    data class Graph(
        val workspaceRoot: String,
        val format: String = "text"
    ) : DaemonRequest()
    
    data object Ping : DaemonRequest()
    data object Shutdown : DaemonRequest()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DaemonResponse.Success::class, name = "success"),
    JsonSubTypes.Type(value = DaemonResponse.Error::class, name = "error"),
    JsonSubTypes.Type(value = DaemonResponse.Progress::class, name = "progress"),
    JsonSubTypes.Type(value = DaemonResponse.Log::class, name = "log")
)
sealed class DaemonResponse {
    data class Success(
        val result: String? = null,
        val data: Map<String, Any>? = null
    ) : DaemonResponse()
    
    data class Error(
        val message: String,
        val cause: String? = null,
        val exitCode: Int = 1
    ) : DaemonResponse()
    
    data class Progress(
        val message: String,
        val current: Int = 0,
        val total: Int = 0
    ) : DaemonResponse()
    
    data class Log(
        val level: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : DaemonResponse()
}