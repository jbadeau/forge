package com.frontseat.plugin

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.exists

/**
 * Manages plugin loading and lifecycle
 */
class PluginManager(
    private val pluginRepository: PluginRepository = PluginRepository()
) {
    private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    private val pluginClassLoaders = mutableListOf<PluginClassLoader>()
    
    /**
     * Load plugins for a workspace using ServiceLoader for built-in plugins
     */
    fun loadPlugins(workspaceRoot: Path): List<ProjectPlugin> {
        logger.info("Loading plugins for workspace: $workspaceRoot")
        
        val plugins = mutableListOf<ProjectPlugin>()
        
        // Load built-in plugins using ServiceLoader
        try {
            val serviceLoader = ServiceLoader.load(ProjectPlugin::class.java)
            serviceLoader.forEach { plugin ->
                try {
                    plugins.add(plugin)
                    logger.info("Loaded built-in plugin: ${plugin.metadata.name} v${plugin.metadata.version}")
                } catch (e: Exception) {
                    logger.error("Failed to load built-in plugin: ${plugin.metadata.id}", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load built-in plugins via ServiceLoader", e)
        }
        
        logger.info("Loaded ${plugins.size} plugin(s)")
        return plugins
    }
    
    /**
     * Load plugins from specifications
     */
    fun loadPlugins(pluginSpecs: List<PluginSpec>): List<ProjectPlugin> {
        logger.info("Loading ${pluginSpecs.size} specified plugin(s)")
        
        val plugins = mutableListOf<ProjectPlugin>()
        
        pluginSpecs.forEach { spec ->
            try {
                val plugin = loadPlugin(spec)
                plugins.add(plugin)
            } catch (e: Exception) {
                logger.error("Failed to load plugin: ${spec.id}", e)
                // Continue loading other plugins
            }
        }
        
        logger.info("Loaded ${plugins.size} plugin(s)")
        return plugins
    }
    
    /**
     * Load a specific plugin
     */
    fun loadPlugin(spec: PluginSpec): ProjectPlugin {
        // Check if already loaded
        loadedPlugins[spec.id]?.let { loadedPlugin ->
            if (loadedPlugin.version == spec.version) {
                logger.debug("Plugin already loaded: ${spec.id}@${spec.version}")
                return loadedPlugin.plugin
            } else {
                logger.info("Unloading existing plugin ${spec.id}@${loadedPlugin.version} to load version ${spec.version}")
                unloadPlugin(spec.id)
            }
        }
        
        logger.info("Loading plugin: ${spec.toCoordinateString()}")
        
        // Resolve plugin JAR
        val jarPath = pluginRepository.resolve(spec)
        
        // Load plugin
        val classLoader = PluginClassLoader(jarPath)
        val plugin = classLoader.loadPlugin()
        
        // Validate plugin metadata
        if (plugin.metadata.id != spec.id) {
            logger.warn("Plugin ID mismatch: expected ${spec.id}, got ${plugin.metadata.id}")
        }
        
        // Validate plugin options
        val validationResult = plugin.validateOptions(spec.options)
        if (!validationResult.isValid) {
            throw PluginConfigurationException("Invalid plugin options for ${spec.id}: ${validationResult.errors}")
        }
        
        // Initialize plugin
        try {
            plugin.initialize()
        } catch (e: Exception) {
            throw PluginInitializationException("Failed to initialize plugin ${spec.id}", e)
        }
        
        // Store loaded plugin
        val loadedPlugin = LoadedPlugin(
            plugin = plugin,
            spec = spec,
            version = plugin.metadata.version,
            classLoader = classLoader
        )
        loadedPlugins[spec.id] = loadedPlugin
        pluginClassLoaders.add(classLoader)
        
        logger.info("Successfully loaded plugin: ${plugin.metadata.name} v${plugin.metadata.version}")
        return plugin
    }
    
    /**
     * Unload a plugin
     */
    fun unloadPlugin(pluginId: String) {
        val loadedPlugin = loadedPlugins.remove(pluginId)
        if (loadedPlugin != null) {
            logger.info("Unloading plugin: $pluginId")
            
            try {
                loadedPlugin.plugin.cleanup()
            } catch (e: Exception) {
                logger.error("Error during plugin cleanup: $pluginId", e)
            }
            
            try {
                loadedPlugin.classLoader.close()
                pluginClassLoaders.remove(loadedPlugin.classLoader)
            } catch (e: Exception) {
                logger.error("Error closing plugin classloader: $pluginId", e)
            }
        }
    }
    
    /**
     * Get all loaded plugins
     */
    fun getLoadedPlugins(): List<ProjectPlugin> {
        return loadedPlugins.values.map { it.plugin }
    }
    
    /**
     * Get plugin by ID
     */
    fun getPlugin(pluginId: String): ProjectPlugin? {
        return loadedPlugins[pluginId]?.plugin
    }
    
    /**
     * Install a plugin (download and cache)
     */
    fun installPlugin(spec: PluginSpec): Path {
        logger.info("Installing plugin: ${spec.toCoordinateString()}")
        return pluginRepository.resolve(spec)
    }
    
    /**
     * List installed (cached) plugins
     */
    fun listInstalled(): List<CachedPlugin> {
        return pluginRepository.listCached()
    }
    
    
    /**
     * Clean up all loaded plugins
     */
    fun shutdown() {
        logger.info("Shutting down plugin manager")
        
        loadedPlugins.keys.toList().forEach { pluginId ->
            unloadPlugin(pluginId)
        }
        
        pluginClassLoaders.forEach { classLoader ->
            try {
                classLoader.close()
            } catch (e: Exception) {
                logger.error("Error closing classloader during shutdown", e)
            }
        }
        pluginClassLoaders.clear()
    }
    
}

/**
 * Represents a loaded plugin with its metadata
 */
private data class LoadedPlugin(
    val plugin: ProjectPlugin,
    val spec: PluginSpec,
    val version: String,
    val classLoader: PluginClassLoader
)

/**
 * Exception thrown when plugin configuration is invalid
 */
class PluginConfigurationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when plugin initialization fails
 */
class PluginInitializationException(message: String, cause: Throwable? = null) : Exception(message, cause)