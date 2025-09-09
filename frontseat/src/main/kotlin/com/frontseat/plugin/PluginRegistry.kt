package com.frontseat.plugin

import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for Frontseat plugins
 */
class PluginRegistry {
    private val logger = LoggerFactory.getLogger(PluginRegistry::class.java)
    
    private val plugins = ConcurrentHashMap<String, FrontseatPlugin>()
    private val pluginClassLoaders = mutableListOf<URLClassLoader>()
    
    /**
     * Register a plugin
     */
    fun registerPlugin(plugin: FrontseatPlugin) {
        if (plugins.containsKey(plugin.id)) {
            logger.warn("Replacing existing plugin: ${plugin.id}")
        }
        
        plugins[plugin.id] = plugin
        logger.info("Registered plugin: ${plugin.name} v${plugin.version} (${plugin.id})")
    }
    
    /**
     * Get all registered plugins
     */
    fun getPlugins(): Collection<FrontseatPlugin> = plugins.values
    
    /**
     * Get plugin by ID
     */
    fun getPlugin(id: String): FrontseatPlugin? = plugins[id]
    
    /**
     * Shutdown all plugins
     */
    fun shutdown() {
        logger.info("Shutting down plugin registry")
        
        plugins.values.forEach { plugin ->
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down plugin: ${plugin.id}", e)
            }
        }
        
        // Close classloaders
        pluginClassLoaders.forEach { classLoader ->
            try {
                classLoader.close()
            } catch (e: Exception) {
                logger.error("Error closing plugin classloader", e)
            }
        }
        
        // Clear registries
        plugins.clear()
        pluginClassLoaders.clear()
    }
}