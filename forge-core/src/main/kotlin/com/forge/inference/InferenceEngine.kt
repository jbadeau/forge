package com.forge.inference

import com.forge.config.PluginConfiguration
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
        nxJsonConfiguration: Map<String, Any> = emptyMap(),
        pluginConfigurations: List<PluginConfiguration> = emptyList()
    ): InferenceResult {
        val context = CreateNodesContext(
            workspaceRoot = workspaceRoot,
            nxJsonConfiguration = nxJsonConfiguration
        )
        
        val allProjects = mutableMapOf<String, com.forge.core.ProjectConfiguration>()
        val allExternalNodes = mutableMapOf<String, Any>()
        val allDependencies = mutableListOf<RawProjectGraphDependency>()
        
        pluginRegistry.getPlugins().forEach { plugin ->
            try {
                logger.debug("Running inference plugin: ${plugin.name}")
                val matchingFiles = findMatchingFiles(workspaceRoot, plugin.createNodesPattern)
                
                if (matchingFiles.isNotEmpty()) {
                    logger.debug("Found ${matchingFiles.size} files matching pattern '${plugin.createNodesPattern}'")
                    
                    // Find plugin configuration options
                    val pluginConfig = pluginConfigurations.find { it.plugin == plugin.name }
                    val options = if (pluginConfig != null) {
                        mergePluginOptions(plugin, pluginConfig.options)
                    } else {
                        plugin.defaultOptions
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    val result = (plugin as InferencePlugin<Any>).createNodes(
                        matchingFiles,
                        options,
                        context
                    )
                    
                    // Merge results - properly merge projects with same names
                    result.projects.forEach { (projectName, projectConfig) ->
                        if (allProjects.containsKey(projectName)) {
                            // Merge with existing project
                            val existing = allProjects[projectName]!!
                            val mergedTargets = existing.targets + projectConfig.targets
                            val mergedTags = (existing.tags + projectConfig.tags).distinct()
                            allProjects[projectName] = existing.copy(
                                targets = mergedTargets,
                                tags = mergedTags
                            )
                            logger.debug("Merged plugin results for project '$projectName'")
                        } else {
                            allProjects[projectName] = projectConfig
                        }
                    }
                    allExternalNodes.putAll(result.externalNodes)
                    
                    logger.info("Plugin '${plugin.name}' inferred ${result.projects.size} projects")
                }
            } catch (e: Exception) {
                logger.error("Error running inference plugin '${plugin.name}': ${e.message}", e)
            }
        }
        
        // After all projects are inferred, run dependency inference
        val dependenciesContext = CreateDependenciesContext(
            workspaceRoot = workspaceRoot,
            projects = allProjects,
            nxJsonConfiguration = nxJsonConfiguration
        )
        
        pluginRegistry.getPlugins().forEach { plugin ->
            if (plugin.createDependencies != null) {
                try {
                    logger.debug("Running dependency inference for plugin: ${plugin.name}")
                    val pluginConfig = pluginConfigurations.find { it.plugin == plugin.name }
                    val options = if (pluginConfig != null) {
                        mergePluginOptions(plugin, pluginConfig.options)
                    } else {
                        plugin.defaultOptions
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    val dependencies = (plugin as InferencePlugin<Any>).createDependencies!!(
                        options,
                        dependenciesContext
                    )
                    
                    allDependencies.addAll(dependencies)
                    logger.info("Plugin '${plugin.name}' inferred ${dependencies.size} dependencies")
                } catch (e: Exception) {
                    logger.error("Error running dependency inference for plugin '${plugin.name}': ${e.message}", e)
                }
            }
        }
        
        return InferenceResult(
            projects = allProjects,
            dependencies = allDependencies,
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
    
    /**
     * Merge plugin configuration options with default options
     * Currently returns plugin's default options to avoid ClassCastException
     */
    private fun mergePluginOptions(plugin: InferencePlugin<*>, configOptions: Map<String, Any>): Any? {
        // For now, always use plugin defaults to avoid ClassCastException
        // TODO: Implement proper option merging that converts Map to typed plugin options
        // when we have proper deserialization logic for each plugin's option type
        return plugin.defaultOptions
    }
}