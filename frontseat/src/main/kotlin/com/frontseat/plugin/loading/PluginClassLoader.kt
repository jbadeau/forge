package com.frontseat.plugin.loading

import com.frontseat.plugin.FrontseatPlugin
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * ClassLoader for loading plugins from JAR files
 */
class PluginClassLoader(
    private val pluginJar: Path,
    parent: ClassLoader = Thread.currentThread().contextClassLoader
) : URLClassLoader(arrayOf(pluginJar.toUri().toURL()), parent), Closeable {
    
    private val logger = LoggerFactory.getLogger(PluginClassLoader::class.java)
    
    /**
     * Load a FrontseatPlugin from this JAR using ServiceLoader
     */
    fun loadPlugin(): FrontseatPlugin {
        logger.debug("Loading plugin from: $pluginJar")
        
        // Read service file directly from this JAR to avoid parent classloader pollution
        val plugins = loadPluginsFromJar()
        
        return when (plugins.size) {
            0 -> throw PluginLoadException("No FrontseatPlugin implementation found in $pluginJar")
            1 -> {
                val plugin = plugins.first()
                logger.info("Loaded plugin: ${plugin.id}@${plugin.version}")
                plugin
            }
            else -> {
                logger.warn("Multiple FrontseatPlugin implementations found in $pluginJar, using first one")
                val plugin = plugins.first()
                logger.info("Loaded plugin: ${plugin.id}@${plugin.version}")
                plugin
            }
        }
    }
    
    private fun loadPluginsFromJar(): List<FrontseatPlugin> {
        val plugins = mutableListOf<FrontseatPlugin>()
        val serviceResource = "META-INF/services/com.frontseat.plugin.FrontseatPlugin"
        
        // Read the service file from this specific JAR
        val serviceUrl = findResource(serviceResource) 
            ?: throw PluginLoadException("No service file found in $pluginJar")
        
        val classNames = serviceUrl.readText().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        
        classNames.forEach { className ->
            try {
                val pluginClass = loadClass(className)
                val plugin = pluginClass.getDeclaredConstructor().newInstance() as FrontseatPlugin
                plugins.add(plugin)
                logger.debug("Loaded plugin class: $className")
            } catch (e: Exception) {
                logger.error("Failed to load plugin class '$className': ${e.message}", e)
            }
        }
        
        return plugins
    }
    
    /**
     * Load all FrontseatPlugins from this JAR
     */
    fun loadAllPlugins(): List<FrontseatPlugin> {
        logger.debug("Loading all plugins from: $pluginJar")
        
        val serviceLoader = ServiceLoader.load(FrontseatPlugin::class.java, this)
        val plugins = serviceLoader.toList()
        
        logger.info("Loaded ${plugins.size} plugin(s) from $pluginJar")
        plugins.forEach { plugin ->
            logger.debug("  - ${plugin.id}@${plugin.version}")
        }
        
        return plugins
    }
    
    override fun toString(): String {
        return "PluginClassLoader(pluginJar=$pluginJar)"
    }
}

/**
 * Exception thrown when plugin loading fails
 */
class PluginLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)