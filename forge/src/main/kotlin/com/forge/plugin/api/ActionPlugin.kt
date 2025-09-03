package com.forge.plugin.api

import com.forge.actions.ActionNode
import com.forge.actions.ActionResult
import com.forge.actions.ActionType
import com.forge.task.Task

/**
 * Metadata describing an Action plugin
 */
data class ActionPluginMetadata(
    val id: String,                         // "com.forge.cache"
    val name: String,                       // "Cache Plugin"
    val version: String,                    // "1.2.0"
    val description: String,                // "Plugin for caching task results"
    val supportedActionTypes: Set<ActionType> = emptySet(),
    val author: String = "",                // "Forge Team"
    val homepage: String = "",              // "https://github.com/forge/plugin-cache"
    val tags: List<String> = emptyList()    // ["cache", "performance"]
)

/**
 * Plugin that can inject custom actions into the action graph
 */
interface ActionPlugin {
    /**
     * Plugin metadata
     */
    val metadata: ActionPluginMetadata
    
    /**
     * Called during action graph construction to inject custom actions
     */
    fun inject(
        task: Task,
        graph: MutableActionGraph,
        context: ExecutionContext
    )
    
    /**
     * Register custom action handlers for this plugin's action types
     */
    fun getActionHandlers(): Map<ActionType, ActionHandler> = emptyMap()
    
    /**
     * Plugin configuration schema for validation
     */
    fun getConfigurationSchema(): Schema? = null
    
    /**
     * Initialize plugin with configuration
     */
    fun initialize(config: Map<String, Any>) {}
    
    /**
     * Called when plugin is being shut down
     */
    fun shutdown() {}
    
    /**
     * Health check for the plugin
     */
    fun healthCheck(): HealthStatus = HealthStatus.HEALTHY
}

/**
 * Handler for executing custom actions
 */
interface ActionHandler {
    /**
     * Execute the action
     */
    suspend fun execute(
        action: ActionNode,
        context: ExecutionContext
    ): ActionResult
    
    /**
     * Validate action configuration before execution
     */
    fun validate(action: ActionNode): ValidationResult = ValidationResult.valid()
    
    /**
     * Estimate resource requirements for this action
     */
    fun estimateResources(action: ActionNode): ResourceRequirements? = null
    
    /**
     * Whether this action can be cached
     */
    fun isCacheable(): Boolean = false
    
    /**
     * Whether this action can be executed remotely
     */
    fun isRemotable(): Boolean = false
}

/**
 * Mutable interface for action graph manipulation by plugins
 */
interface MutableActionGraph {
    /**
     * Add a new action node
     */
    fun addNode(node: ActionNode)
    
    /**
     * Add dependency between actions
     */
    fun addDependency(from: String, to: String)
    
    /**
     * Insert action before another action
     */
    fun insertBefore(targetId: String, node: ActionNode)
    
    /**
     * Insert action after another action
     */
    fun insertAfter(targetId: String, node: ActionNode)
    
    /**
     * Wrap a task with before/after actions
     */
    fun wrapTask(taskId: String, before: ActionNode, after: ActionNode)
    
    /**
     * Replace an existing action
     */
    fun replaceAction(actionId: String, newNode: ActionNode)
    
    /**
     * Remove an action
     */
    fun removeAction(actionId: String)
    
    /**
     * Get all actions for a task
     */
    fun getTaskActions(taskId: String): List<ActionNode>
}

/**
 * Execution context passed to plugins
 */
data class ExecutionContext(
    val workspaceRoot: String,
    val configuration: Map<String, Any>,
    val environment: Map<String, String>,
    val cacheDir: String?,
    val isCI: Boolean,
    val isDryRun: Boolean,
    val parallelism: Int,
    val profile: String?,
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Store data for cross-action communication
     */
    fun store(key: String, value: Any) {
        metadata[key] = value
    }
    
    /**
     * Retrieve stored data
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> retrieve(key: String): T? = metadata[key] as? T
}

/**
 * Schema for configuration validation
 */
data class Schema(
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList()
)

data class PropertySchema(
    val type: String,
    val description: String?,
    val default: Any?,
    val enum: List<Any>? = null
)

/**
 * Validation result
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun valid() = ValidationResult(true)
        fun invalid(vararg errors: String) = ValidationResult(false, errors.toList())
    }
}

/**
 * Resource requirements for an action
 */
data class ResourceRequirements(
    val cpu: Double? = null,
    val memory: Long? = null,
    val disk: Long? = null,
    val network: Boolean = false,
    val gpu: Int? = null,
    val customResources: Map<String, Any> = emptyMap()
)

/**
 * Health status for plugins
 */
enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}