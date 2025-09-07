package com.frontseat.plugin

import com.frontseat.workspace.Workspace

/**
 * Simplified single interface for Frontseat plugins.
 * Plugins register capabilities (natures, executors, generators) during initialization.
 */
interface FrontseatPlugin {
    /**
     * Unique identifier for this plugin
     */
    val id: String
    
    /**
     * Human-readable name for this plugin
     */
    val name: String
    
    /**
     * Version of this plugin
     */
    val version: String
    
    /**
     * Initialize the plugin with workspace context.
     * This is where plugins register their capabilities:
     * - Register natures with NatureRegistry
     * - Register executors with ExecutorRegistry  
     * - Register generators with GeneratorRegistry
     */
    fun initialize(workspace: Workspace)
    
    /**
     * Called when plugin is being shut down
     */
    fun shutdown() {}
}