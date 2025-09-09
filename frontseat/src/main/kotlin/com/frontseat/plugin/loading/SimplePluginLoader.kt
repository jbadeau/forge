package com.frontseat.plugin.loading

import com.frontseat.project.nature.Nature as NatureInterface  
import com.frontseat.project.nature.NatureInfo
import com.frontseat.plugin.Plugin
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.workspace.Workspace
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.slf4j.LoggerFactory

/**
 * Simple runtime plugin loader that discovers plugins and natures via annotations
 */
class SimplePluginLoader {
    private val logger = LoggerFactory.getLogger(SimplePluginLoader::class.java)
    
    /**
     * Load all plugins and auto-register natures found in the specified packages
     */
    fun loadPlugins(workspace: Workspace, packages: List<String> = listOf("com.frontseat")) {
        logger.info("Scanning for plugins in packages: $packages")
        
        packages.forEach { pkg ->
            scanPackage(pkg, workspace)
        }
    }
    
    private fun scanPackage(packageName: String, workspace: Workspace) {
        try {
            val reflections = Reflections(packageName, Scanners.TypesAnnotated)
            
            // Load plugins
            loadPluginsFromPackage(reflections, workspace)
            
            // Auto-register natures
            autoRegisterNatures(reflections, workspace)
            
        } catch (e: Exception) {
            logger.error("Error scanning package $packageName", e)
        }
    }
    
    private fun loadPluginsFromPackage(reflections: Reflections, workspace: Workspace) {
        val pluginClasses = reflections.getTypesAnnotatedWith(Plugin::class.java)
        
        pluginClasses.forEach { clazz ->
            try {
                if (FrontseatPlugin::class.java.isAssignableFrom(clazz)) {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as FrontseatPlugin
                    val annotation = clazz.getAnnotation(Plugin::class.java)
                    logger.info("Loaded plugin: ${annotation.id} (${clazz.simpleName})")
                    
                    // Let plugin do its own initialization if needed
                    plugin.initialize(workspace)
                }
            } catch (e: Exception) {
                logger.error("Failed to load plugin: ${clazz.name}", e)
            }
        }
    }
    
    private fun autoRegisterNatures(reflections: Reflections, workspace: Workspace) {
        val natureClasses = reflections.getTypesAnnotatedWith(NatureInfo::class.java)
        val registry = workspace.getNatureRegistry()
        
        natureClasses.forEach { clazz ->
            try {
                if (NatureInterface::class.java.isAssignableFrom(clazz)) {
                    val nature = clazz.getDeclaredConstructor().newInstance() as NatureInterface
                    registry.register(nature)
                    logger.info("Auto-registered nature: ${nature.id} (${clazz.simpleName})")
                }
            } catch (e: Exception) {
                logger.error("Failed to register nature: ${clazz.name}", e)
            }
        }
    }
}