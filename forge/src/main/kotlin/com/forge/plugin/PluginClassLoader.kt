package com.forge.plugin

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.URL
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
     * Load a ProjectPlugin from this JAR using ServiceLoader
     */
    fun loadPlugin(): ProjectPlugin {
        logger.debug("Loading plugin from: $pluginJar")
        
        // Read service file directly from this JAR to avoid parent classloader pollution
        val plugins = loadPluginsFromJar()
        
        return when (plugins.size) {
            0 -> throw PluginLoadException("No ProjectPlugin implementation found in $pluginJar")
            1 -> {
                val plugin = plugins.first()
                logger.info("Loaded plugin: ${plugin.metadata.id}@${plugin.metadata.version}")
                plugin
            }
            else -> {
                logger.warn("Multiple ProjectPlugin implementations found in $pluginJar, using first one")
                val plugin = plugins.first()
                logger.info("Loaded plugin: ${plugin.metadata.id}@${plugin.metadata.version}")
                plugin
            }
        }
    }
    
    private fun loadPluginsFromJar(): List<ProjectPlugin> {
        val plugins = mutableListOf<ProjectPlugin>()
        val serviceResource = "META-INF/services/com.forge.plugin.ProjectPlugin"
        
        // Read the service file from this specific JAR
        val serviceUrl = findResource(serviceResource) 
            ?: throw PluginLoadException("No service file found in $pluginJar")
        
        val classNames = serviceUrl.readText().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        
        classNames.forEach { className ->
            try {
                val pluginClass = loadClass(className)
                val plugin = pluginClass.getDeclaredConstructor().newInstance() as ProjectPlugin
                plugins.add(plugin)
                logger.debug("Loaded plugin class: $className")
            } catch (e: Exception) {
                logger.error("Failed to load plugin class '$className': ${e.message}", e)
            }
        }
        
        return plugins
    }
    
    /**
     * Load all ProjectPlugins from this JAR
     */
    fun loadAllPlugins(): List<ProjectPlugin> {
        logger.debug("Loading all plugins from: $pluginJar")
        
        val serviceLoader = ServiceLoader.load(ProjectPlugin::class.java, this)
        val plugins = serviceLoader.toList()
        
        logger.info("Loaded ${plugins.size} plugin(s) from $pluginJar")
        plugins.forEach { plugin ->
            logger.debug("  - ${plugin.metadata.id}@${plugin.metadata.version}")
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