package com.frontseat.plugin

import com.frontseat.actions.ActionNode
import com.frontseat.actions.ActionType
import com.frontseat.plugin.api.*
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all plugins (project, action and executor)
 */
class PluginRegistry {
    private val logger = LoggerFactory.getLogger(PluginRegistry::class.java)
    
    private val actionPlugins = ConcurrentHashMap<String, ActionPlugin>()
    private val executorPlugins = ConcurrentHashMap<String, Executor>()
    private val actionHandlers = ConcurrentHashMap<ActionType, ActionHandler>()
    private val pluginClassLoaders = mutableListOf<URLClassLoader>()
    
    /**
     * Load plugins from various sources
     */
    fun loadPlugins(config: PluginConfiguration) {
        // Load built-in plugins
        loadBuiltInPlugins()
        
        // Load plugins via ServiceLoader
        loadServiceLoaderPlugins()
        
        // Load plugins from directory
        config.pluginDirectory?.let { dir ->
            loadPluginsFromDirectory(File(dir))
        }
        
        // Load explicitly configured plugins
        config.plugins.forEach { pluginConfig ->
            when {
                pluginConfig.path != null -> loadPluginFromJar(File(pluginConfig.path))
                pluginConfig.className != null -> loadPluginByClassName(pluginConfig.className)
                else -> logger.warn("Invalid plugin configuration: ${pluginConfig.id}")
            }
        }
        
        logger.info("Loaded ${actionPlugins.size} action plugins and ${executorPlugins.size} executor plugins")
    }
    
    /**
     * Load built-in plugins
     */
    private fun loadBuiltInPlugins() {
        // These will be implemented in separate files
        // registerActionPlugin(CacheActionPlugin())
        // registerActionPlugin(TracingActionPlugin())
        // registerExecutor(LocalExecutor())
        // registerExecutor(DockerExecutor())
    }
    
    /**
     * Load plugins using ServiceLoader mechanism
     */
    private fun loadServiceLoaderPlugins() {
        
        // Load action plugins
        ServiceLoader.load(ActionPlugin::class.java).forEach { plugin ->
            try {
                registerActionPlugin(plugin)
            } catch (e: Exception) {
                logger.error("Failed to register action plugin: ${plugin.javaClass.name}", e)
            }
        }
        
        // Load executor plugins
        ServiceLoader.load(Executor::class.java).forEach { plugin ->
            try {
                registerExecutor(plugin)
            } catch (e: Exception) {
                logger.error("Failed to register executor plugin: ${plugin.javaClass.name}", e)
            }
        }
    }
    
    /**
     * Load plugins from a directory
     */
    private fun loadPluginsFromDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) {
            logger.warn("Plugin directory does not exist: $directory")
            return
        }
        
        directory.listFiles { file ->
            file.isFile && file.extension == "jar"
        }?.forEach { jarFile ->
            try {
                loadPluginFromJar(jarFile)
            } catch (e: Exception) {
                logger.error("Failed to load plugin from JAR: $jarFile", e)
            }
        }
    }
    
    /**
     * Load plugin from JAR file
     */
    private fun loadPluginFromJar(jarFile: File) {
        if (!jarFile.exists()) {
            logger.error("Plugin JAR not found: $jarFile")
            return
        }
        
        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            this.javaClass.classLoader
        )
        pluginClassLoaders.add(classLoader)
        
        // Load plugin manifest
        val manifestStream = classLoader.getResourceAsStream("META-INF/frontseat-plugin.json")
        if (manifestStream != null) {
            val manifest = loadManifest(manifestStream)
            
            // Load action plugins
            manifest.actionPlugins?.forEach { className ->
                loadPluginClass(classLoader, className, ActionPlugin::class.java)?.let {
                    registerActionPlugin(it)
                }
            }
            
            // Load executor plugins
            manifest.executorPlugins?.forEach { className ->
                loadPluginClass(classLoader, className, Executor::class.java)?.let {
                    registerExecutor(it)
                }
            }
        } else {
            // Try ServiceLoader with the JAR's classloader
            ServiceLoader.load(ActionPlugin::class.java, classLoader).forEach {
                registerActionPlugin(it)
            }
            ServiceLoader.load(Executor::class.java, classLoader).forEach {
                registerExecutor(it)
            }
        }
    }
    
    /**
     * Load plugin by class name
     */
    private fun loadPluginByClassName(className: String) {
        try {
            val clazz = Class.forName(className)
            when {
                ActionPlugin::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as ActionPlugin
                    registerActionPlugin(plugin)
                }
                Executor::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as Executor
                    registerExecutor(plugin)
                }
                else -> logger.error("Class is not a valid plugin: $className")
            }
        } catch (e: Exception) {
            logger.error("Failed to load plugin class: $className", e)
        }
    }
    
    /**
     * Load a plugin class from a specific classloader
     */
    private fun <T> loadPluginClass(
        classLoader: ClassLoader,
        className: String,
        pluginType: Class<T>
    ): T? {
        return try {
            val clazz = classLoader.loadClass(className)
            if (pluginType.isAssignableFrom(clazz)) {
                @Suppress("UNCHECKED_CAST")
                clazz.getDeclaredConstructor().newInstance() as T
            } else {
                logger.error("Class $className is not a valid ${pluginType.simpleName}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to load plugin class: $className", e)
            null
        }
    }
    
    
    /**
     * Register an action plugin
     */
    fun registerActionPlugin(plugin: ActionPlugin, config: Map<String, Any> = emptyMap()) {
        if (actionPlugins.containsKey(plugin.metadata.id)) {
            logger.warn("Replacing existing action plugin: ${plugin.metadata.id}")
        }
        
        // Initialize plugin with configuration
        plugin.initialize(config)
        
        // Register the plugin
        actionPlugins[plugin.metadata.id] = plugin
        
        // Register its action handlers
        plugin.getActionHandlers().forEach { (type, handler) ->
            if (actionHandlers.containsKey(type)) {
                logger.warn("Replacing existing action handler for type: $type")
            }
            actionHandlers[type] = handler
        }
        
        logger.info("Registered action plugin: ${plugin.metadata.name} v${plugin.metadata.version} (${plugin.metadata.id})")
    }
    
    /**
     * Register an executor plugin
     */
    fun registerExecutor(plugin: Executor, config: Map<String, Any> = emptyMap()) {
        if (executorPlugins.containsKey(plugin.metadata.id)) {
            logger.warn("Replacing existing executor plugin: ${plugin.metadata.id}")
        }
        
        // Initialize plugin with configuration
        plugin.initialize(config)
        
        // Register the plugin
        executorPlugins[plugin.metadata.id] = plugin
        
        logger.info("Registered executor plugin: ${plugin.metadata.name} v${plugin.metadata.version} (${plugin.metadata.id})")
    }
    
    
    /**
     * Get all registered action plugins
     */
    fun getActionPlugins(): Collection<ActionPlugin> = actionPlugins.values
    
    /**
     * Get action plugin by ID
     */
    fun getActionPlugin(id: String): ActionPlugin? = actionPlugins[id]
    
    /**
     * Get all registered executor plugins
     */
    fun getExecutors(): Collection<Executor> = executorPlugins.values
    
    /**
     * Get executor plugin by ID
     */
    fun getExecutor(id: String): Executor? = executorPlugins[id]
    
    /**
     * Get executor that can handle the given action
     */
    fun getExecutorForAction(action: ActionNode): Executor? {
        // First check if action specifies a preferred executor
        val preferredExecutorId = action.metadata["executor"] as? String
        if (preferredExecutorId != null) {
            val executor = executorPlugins[preferredExecutorId]
            if (executor != null && executor.canExecute(action)) {
                return executor
            }
        }
        
        // Find first executor that can handle this action
        return executorPlugins.values.firstOrNull { it.canExecute(action) }
    }
    
    /**
     * Get action handler for a specific action type
     */
    fun getActionHandler(type: ActionType): ActionHandler? = actionHandlers[type]
    
    /**
     * Health check all plugins
     */
    suspend fun healthCheck(): Map<String, HealthStatus> {
        val results = mutableMapOf<String, HealthStatus>()
        
        actionPlugins.forEach { (id, plugin) ->
            results["action:$id"] = try {
                plugin.healthCheck()
            } catch (e: Exception) {
                logger.error("Health check failed for action plugin: $id", e)
                HealthStatus.UNHEALTHY
            }
        }
        
        executorPlugins.forEach { (id, plugin) ->
            results["executor:$id"] = try {
                plugin.healthCheck()
            } catch (e: Exception) {
                logger.error("Health check failed for executor plugin: $id", e)
                HealthStatus.UNHEALTHY
            }
        }
        
        return results
    }
    
    /**
     * Shutdown all plugins
     */
    fun shutdown() {
        logger.info("Shutting down plugin registry")
        
        
        // Shutdown action plugins
        actionPlugins.values.forEach { plugin ->
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down action plugin: ${plugin.metadata.id}", e)
            }
        }
        
        // Shutdown executor plugins
        executorPlugins.values.forEach { plugin ->
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down executor plugin: ${plugin.metadata.id}", e)
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
        actionPlugins.clear()
        executorPlugins.clear()
        actionHandlers.clear()
        pluginClassLoaders.clear()
    }
    
    /**
     * Load plugin manifest from stream
     */
    private fun loadManifest(stream: java.io.InputStream): PluginManifest {
        // In a real implementation, this would parse JSON/YAML
        // For now, returning a simple structure
        return PluginManifest()
    }
}

/**
 * Plugin configuration
 */
data class PluginConfiguration(
    val pluginDirectory: String? = null,
    val plugins: List<PluginConfig> = emptyList()
)

data class PluginConfig(
    val id: String,
    val enabled: Boolean = true,
    val path: String? = null,
    val className: String? = null,
    val config: Map<String, Any> = emptyMap()
)

/**
 * Plugin manifest structure
 */
data class PluginManifest(
    val actionPlugins: List<String>? = null,
    val executorPlugins: List<String>? = null,
    val dependencies: List<String>? = null,
    val version: String? = null
)