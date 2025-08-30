package com.forge.discovery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.config.WorkspaceConfiguration
import com.forge.core.ProjectConfiguration
import com.forge.core.ProjectGraph
import com.forge.core.ProjectGraphDependency
import com.forge.core.ProjectGraphNode
import com.forge.core.DependencyType
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class ProjectDiscovery(
    private val workspaceRoot: Path,
    private val plugins: List<DiscoveryPlugin> = emptyList()
) {
    private val logger = LoggerFactory.getLogger(ProjectDiscovery::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    fun discoverProjects(): ProjectGraph {
        logger.info("Starting project discovery in workspace: $workspaceRoot")
        
        val workspaceConfig = loadWorkspaceConfiguration()
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        // Discover projects via explicit project.json files
        projects.putAll(discoverExplicitProjects())
        
        // Discover projects via plugins (package.json, etc.)
        plugins.forEach { plugin ->
            projects.putAll(plugin.discoverProjects(workspaceRoot))
        }
        
        // Apply workspace defaults
        val configuredProjects = applyWorkspaceDefaults(projects, workspaceConfig)
        
        // Build project graph nodes
        val nodes = configuredProjects.mapValues { (name, config) ->
            ProjectGraphNode(name, config.projectType, config)
        }
        
        // Build dependency graph
        val dependencies = buildDependencyGraph(configuredProjects, workspaceConfig)
        
        logger.info("Discovered ${nodes.size} projects with ${dependencies.values.sumOf { it.size }} dependencies")
        
        return ProjectGraph(nodes, dependencies)
    }
    
    private fun loadWorkspaceConfiguration(): WorkspaceConfiguration {
        val forgeConfigPath = workspaceRoot / "forge.json"
        val nxConfigPath = workspaceRoot / "nx.json"
        
        return when {
            forgeConfigPath.exists() -> {
                logger.debug("Loading forge.json configuration")
                objectMapper.readValue(forgeConfigPath.toFile())
            }
            nxConfigPath.exists() -> {
                logger.debug("Loading nx.json configuration (compatibility mode)")
                objectMapper.readValue(nxConfigPath.toFile())
            }
            else -> {
                logger.debug("No workspace configuration found, using defaults")
                WorkspaceConfiguration()
            }
        }
    }
    
    private fun discoverExplicitProjects(): Map<String, ProjectConfiguration> {
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        Files.walk(workspaceRoot)
            .filter { it.fileName.toString() == "project.json" }
            .forEach { projectFile ->
                try {
                    val configFromFile: ProjectConfiguration = objectMapper.readValue(projectFile.toFile())
                    
                    // Infer root from file location (Nx-style)
                    val projectRoot = workspaceRoot.relativize(projectFile.parent).toString()
                    val config = configFromFile.copy(root = projectRoot)
                    
                    projects[config.name] = config
                    logger.debug("Discovered explicit project: ${config.name} at ${projectFile.parent}")
                } catch (e: Exception) {
                    logger.warn("Failed to parse project.json at $projectFile: ${e.message}")
                }
            }
        
        return projects
    }
    
    private fun applyWorkspaceDefaults(
        projects: Map<String, ProjectConfiguration>,
        workspaceConfig: WorkspaceConfiguration
    ): Map<String, ProjectConfiguration> {
        return projects.mapValues { (_, config) ->
            val updatedTargets = config.targets.mapValues { (targetName, targetConfig) ->
                val defaults = workspaceConfig.getTargetDefaults(targetName)
                if (defaults != null) {
                    mergeTargetConfigurations(targetConfig, defaults)
                } else {
                    targetConfig
                }
            }
            
            config.copy(targets = updatedTargets)
        }
    }
    
    private fun mergeTargetConfigurations(
        target: com.forge.core.TargetConfiguration,
        defaults: com.forge.core.TargetConfiguration
    ): com.forge.core.TargetConfiguration {
        return com.forge.core.TargetConfiguration(
            executor = target.executor ?: defaults.executor,
            command = target.command ?: defaults.command,
            options = defaults.options + target.options,
            configurations = defaults.configurations + target.configurations,
            dependsOn = (defaults.dependsOn + target.dependsOn).distinct(),
            inputs = if (target.inputs.isEmpty()) defaults.inputs else target.inputs,
            outputs = if (target.outputs.isEmpty()) defaults.outputs else target.outputs,
            cache = if (target.cache != defaults.cache) target.cache else defaults.cache,
            parallelism = if (target.parallelism != defaults.parallelism) target.parallelism else defaults.parallelism
        )
    }
    
    private fun buildDependencyGraph(
        projects: Map<String, ProjectConfiguration>,
        workspaceConfig: WorkspaceConfiguration
    ): Map<String, List<ProjectGraphDependency>> {
        val dependencies = mutableMapOf<String, MutableList<ProjectGraphDependency>>()
        
        projects.forEach { (projectName, _) ->
            dependencies[projectName] = mutableListOf()
        }
        
        // Let plugins add their own dependencies (e.g., from package.json imports)
        plugins.forEach { plugin ->
            plugin.buildDependencies(projects, dependencies)
        }
        
        return dependencies.mapValues { it.value.toList() }
    }
}

interface DiscoveryPlugin {
    fun discoverProjects(workspaceRoot: Path): Map<String, ProjectConfiguration>
    
    fun buildDependencies(
        projects: Map<String, ProjectConfiguration>,
        dependencies: MutableMap<String, MutableList<ProjectGraphDependency>>
    )
}