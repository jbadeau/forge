package com.forge.project

import com.forge.plugin.api.ProjectConfiguration
import com.forge.plugin.PluginManager
import com.forge.plugin.ProjectPlugin
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.InferenceResult
import com.forge.plugin.api.RawProjectGraphDependency
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

/**
 * Discovers projects in the workspace using ForgePlugins
 */
class ProjectDiscoverer(
    private val pluginManager: PluginManager = PluginManager()
) {
    private val logger = LoggerFactory.getLogger(ProjectDiscoverer::class.java)
    
    /**
     * Discover projects across all ForgePlugins for the given workspace
     */
    fun discoverProjects(
        workspaceRoot: Path,
        nxJsonConfiguration: Map<String, Any> = emptyMap()
    ): InferenceResult {
        val context = CreateNodesContext(
            workspaceRoot = workspaceRoot,
            nxJsonConfiguration = nxJsonConfiguration
        )
        
        val allProjects = mutableMapOf<String, ProjectConfiguration>()
        val allExternalNodes = mutableMapOf<String, Any>()
        val allDependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Load ForgePlugins from workspace configuration
        val projectPlugins = try {
            pluginManager.loadPlugins(workspaceRoot)
        } catch (e: Exception) {
            logger.warn("Failed to load ProjectPlugins, using built-in plugins: ${e.message}")
            getBuiltInPlugins()
        }
        
        projectPlugins.forEach { plugin ->
            try {
                logger.debug("Running inference plugin: ${plugin.metadata.id}")
                val matchingFiles = findMatchingFiles(workspaceRoot, plugin.metadata.createNodesPattern)
                
                if (matchingFiles.isNotEmpty()) {
                    logger.debug("Found ${matchingFiles.size} files matching pattern '${plugin.metadata.createNodesPattern}'")
                    
                    // Find plugin configuration options from workspace
                    val options = plugin.defaultOptions
                    
                    val result = plugin.createNodes(
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
                    
                    logger.info("Plugin '${plugin.metadata.id}' inferred ${result.projects.size} projects")
                }
            } catch (e: Exception) {
                logger.error("Error running inference plugin '${plugin.metadata.id}': ${e.message}", e)
            }
        }
        
        // After all projects are inferred, run dependency inference
        val dependenciesContext = CreateDependenciesContext(
            workspaceRoot = workspaceRoot,
            projects = allProjects,
            nxJsonConfiguration = nxJsonConfiguration
        )
        
        projectPlugins.forEach { plugin ->
            try {
                logger.debug("Running dependency inference for plugin: ${plugin.metadata.id}")
                val options = plugin.defaultOptions
                
                val dependencies = plugin.createEdges(options, dependenciesContext)
                
                allDependencies.addAll(dependencies)
                logger.info("Plugin '${plugin.metadata.id}' inferred ${dependencies.size} dependencies")
            } catch (e: Exception) {
                logger.error("Error running dependency inference for plugin '${plugin.metadata.id}': ${e.message}", e)
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
     * Get built-in plugins when external plugins fail to load
     */
    private fun getBuiltInPlugins(): List<ProjectPlugin> {
        // Return built-in implementations of common plugins
        return listOf(
            // We'll add built-in plugins here if needed
            // For now, return empty list and rely on external plugins
        )
    }
}