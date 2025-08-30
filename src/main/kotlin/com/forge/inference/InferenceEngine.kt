package com.forge.inference

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

/**
 * Engine for running inference plugins to discover project configurations
 */
class InferenceEngine(
    private val pluginRegistry: InferencePluginRegistry = InferencePluginRegistry()
) {
    private val logger = LoggerFactory.getLogger(InferenceEngine::class.java)
    
    /**
     * Run inference across all registered plugins for the given workspace
     */
    fun runInference(
        workspaceRoot: Path,
        nxJsonConfiguration: Map<String, Any> = emptyMap()
    ): CreateNodesResult {
        val context = CreateNodesContext(
            workspaceRoot = workspaceRoot,
            nxJsonConfiguration = nxJsonConfiguration
        )
        
        val allProjects = mutableMapOf<String, com.forge.core.ProjectConfiguration>()
        val allExternalNodes = mutableMapOf<String, Any>()
        
        pluginRegistry.getPlugins().forEach { plugin ->
            try {
                logger.debug("Running inference plugin: ${plugin.name}")
                val matchingFiles = findMatchingFiles(workspaceRoot, plugin.createNodesPattern)
                
                if (matchingFiles.isNotEmpty()) {
                    logger.debug("Found ${matchingFiles.size} files matching pattern '${plugin.createNodesPattern}'")
                    
                    @Suppress("UNCHECKED_CAST")
                    val result = (plugin as InferencePlugin<Any>).createNodes(
                        matchingFiles,
                        plugin.defaultOptions,
                        context
                    )
                    
                    // Merge results
                    allProjects.putAll(result.projects)
                    allExternalNodes.putAll(result.externalNodes)
                    
                    logger.info("Plugin '${plugin.name}' inferred ${result.projects.size} projects")
                }
            } catch (e: Exception) {
                logger.error("Error running inference plugin '${plugin.name}': ${e.message}", e)
            }
        }
        
        return CreateNodesResult(
            projects = allProjects,
            externalNodes = allExternalNodes
        )
    }
    
    /**
     * Find files matching a glob pattern within the workspace
     */
    private fun findMatchingFiles(workspaceRoot: Path, pattern: String): List<String> {
        return try {
            val matcher = workspaceRoot.fileSystem.getPathMatcher("glob:$pattern")
            
            Files.walk(workspaceRoot)
                .filter { it.isRegularFile() }
                .filter { path ->
                    // Convert to relative path for matching
                    val relativePath = workspaceRoot.relativize(path)
                    matcher.matches(relativePath)
                }
                .map { it.pathString }
                .collect(Collectors.toList())
        } catch (e: Exception) {
            logger.warn("Error finding files for pattern '$pattern': ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Register a plugin with the inference engine
     */
    fun <T> registerPlugin(plugin: InferencePlugin<T>) {
        pluginRegistry.register(plugin)
        logger.info("Registered inference plugin: ${plugin.name}")
    }
    
    /**
     * Get all registered plugins
     */
    fun getPlugins(): List<InferencePlugin<*>> = pluginRegistry.getPlugins()
}