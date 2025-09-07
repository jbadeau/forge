package com.frontseat.plugin.sdk

import com.frontseat.actions.ActionId
import com.frontseat.actions.ActionNode
import com.frontseat.actions.ActionType
import com.frontseat.task.Task
import com.frontseat.plugin.api.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Base class for action plugins with common functionality
 */
abstract class BaseActionPlugin : ActionPlugin {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)
    protected var config: Map<String, Any> = emptyMap()
    protected var isInitialized = false
    
    override fun initialize(config: Map<String, Any>) {
        this.config = config
        try {
            onInitialize()
            isInitialized = true
            logger.info("Plugin ${metadata.id} initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize plugin ${metadata.id}", e)
            throw e
        }
    }
    
    override fun healthCheck(): HealthStatus {
        return if (isInitialized) {
            try {
                onHealthCheck()
            } catch (e: Exception) {
                logger.error("Health check failed for plugin ${metadata.id}", e)
                HealthStatus.UNHEALTHY
            }
        } else {
            HealthStatus.UNHEALTHY
        }
    }
    
    override fun shutdown() {
        try {
            onShutdown()
            isInitialized = false
            logger.info("Plugin ${metadata.id} shutdown successfully")
        } catch (e: Exception) {
            logger.error("Error during plugin shutdown: ${metadata.id}", e)
        }
    }
    
    /**
     * Called during plugin initialization - override to provide custom initialization
     */
    protected open fun onInitialize() {}
    
    /**
     * Called during health check - override to provide custom health check logic
     */
    protected open fun onHealthCheck(): HealthStatus = HealthStatus.HEALTHY
    
    /**
     * Called during plugin shutdown - override to provide custom cleanup
     */
    protected open fun onShutdown() {}
    
    /**
     * Helper method to get typed configuration value
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getConfig(key: String): T? = config[key] as? T
    
    /**
     * Helper method to get typed configuration value with default
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getConfig(key: String, default: T): T = config[key] as? T ?: default
    
    /**
     * Helper method to check if plugin should process this task
     */
    protected fun shouldProcessTask(task: Task, context: ExecutionContext): Boolean {
        // Override in subclasses for custom logic
        return true
    }
    
    /**
     * Helper method to wrap a task with before/after actions
     */
    protected fun wrapWithTiming(
        task: Task,
        graph: MutableActionGraph,
        actionType: ActionType = ActionType.RUN_TASK
    ) {
        val startAction = ActionNode(
            id = ActionId.generate(task.project, task.target, actionType, "start"),
            type = actionType,
            project = task.project,
            target = task.target,
            metadata = mapOf(
                "timing" to "start",
                "taskId" to task.id.value
            )
        )
        
        val endAction = ActionNode(
            id = ActionId.generate(task.project, task.target, actionType, "end"),
            type = actionType,
            project = task.project,
            target = task.target,
            metadata = mapOf(
                "timing" to "end",
                "taskId" to task.id.value
            )
        )
        
        graph.wrapTask(task.id.value, startAction, endAction)
    }
    
    /**
     * Helper method to add conditional action
     */
    protected fun addConditionalAction(
        graph: MutableActionGraph,
        condition: String,
        action: ActionNode
    ) {
        val conditionalAction = action.copy(
            metadata = action.metadata + ("condition" to condition)
        )
        graph.addNode(conditionalAction)
    }
    
    /**
     * Helper method to create action with standard metadata
     */
    protected fun createAction(
        task: Task,
        actionType: ActionType,
        suffix: String = "",
        inputs: Map<String, Any> = emptyMap(),
        dependencies: Set<ActionId> = emptySet()
    ): ActionNode {
        return ActionNode(
            id = ActionId.generate(task.project, task.target, actionType, suffix),
            type = actionType,
            project = task.project,
            target = task.target,
            inputs = inputs,
            dependencies = dependencies,
            metadata = mapOf(
                "plugin" to metadata.id,
                "taskId" to task.id.value,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}

/**
 * Base class for action handlers with common functionality
 */
abstract class BaseActionHandler : ActionHandler {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)
    
    override fun validate(action: ActionNode): ValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            onValidate(action, errors)
        } catch (e: Exception) {
            errors.add("Validation error: ${e.message}")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(*errors.toTypedArray())
        }
    }
    
    override fun estimateResources(action: ActionNode): ResourceRequirements? {
        return try {
            onEstimateResources(action)
        } catch (e: Exception) {
            logger.warn("Failed to estimate resources for action ${action.id}", e)
            null
        }
    }
    
    /**
     * Override to provide custom validation logic
     */
    protected open fun onValidate(action: ActionNode, errors: MutableList<String>) {}
    
    /**
     * Override to provide custom resource estimation
     */
    protected open fun onEstimateResources(action: ActionNode): ResourceRequirements? = null
    
    /**
     * Helper method to get typed input value
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getInput(action: ActionNode, key: String): T? = action.inputs[key] as? T
    
    /**
     * Helper method to get typed input value with default
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getInput(action: ActionNode, key: String, default: T): T = 
        action.inputs[key] as? T ?: default
    
    /**
     * Helper method to get metadata value
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getMetadata(action: ActionNode, key: String): T? = action.metadata[key] as? T
}