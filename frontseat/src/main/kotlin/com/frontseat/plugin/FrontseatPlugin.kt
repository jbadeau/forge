package com.frontseat.plugin

import com.frontseat.annotation.Plugin
import com.frontseat.workspace.Workspace
import kotlin.reflect.full.findAnnotation

/**
 * Simplified single interface for Frontseat plugins.
 * Plugins register capabilities (natures, tasks, generators) during initialization.
 */
interface FrontseatPlugin {
    /**
     * Unique identifier for this plugin
     * Gets value from @Plugin annotation if present, otherwise must override
     */
    val id: String
        get() = this::class.findAnnotation<Plugin>()?.id 
            ?: error("Plugin ${this::class.simpleName} must have @Plugin annotation or override id")
    
    /**
     * Human-readable name for this plugin
     * Gets value from @Plugin annotation if present, otherwise uses id
     */
    val name: String
        get() = this::class.findAnnotation<Plugin>()?.name?.takeIf { it.isNotEmpty() } 
            ?: id
    
    /**
     * Version of this plugin
     */
    val version: String
        get() = "1.0.0"
    
    /**
     * Initialize the plugin with workspace context.
     * This is where plugins register their capabilities:
     * - Register natures with NatureRegistry
     * - Register tasks (tasks are functions with @Task annotation)
     * - Register generators with GeneratorRegistry
     * 
     * Default implementation does nothing - override if manual registration needed
     */
    fun initialize(workspace: Workspace) {
        // Default empty - plugins can override if needed
    }
    
    /**
     * Called when plugin is being shut down
     */
    fun shutdown() {
        // Default empty
    }
}