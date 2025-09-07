package com.frontseat.project

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.frontseat.backstage.BackstageProjectDiscoverer
import com.frontseat.nature.NatureRegistry
import com.frontseat.workspace.WorkspaceConfiguration
import com.frontseat.plugin.api.ProjectConfiguration
import com.frontseat.plugin.api.TargetConfiguration
import com.frontseat.plugin.api.InferenceResult
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class ProjectGraphBuilder(
    private val workspaceRoot: Path,
    private val plugins: List<DiscoveryPlugin> = emptyList(),
    private val enableInference: Boolean = true,
    private val natureRegistry: NatureRegistry
) {
    private val backstageDiscoverer = BackstageProjectDiscoverer(natureRegistry)
    private val logger = LoggerFactory.getLogger(ProjectGraphBuilder::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    // Expose workspace configuration for external access
    var workspaceConfiguration: WorkspaceConfiguration? = null
        private set
    
    fun buildProjectGraph(): ProjectGraph {
        logger.info("Building project graph for workspace: $workspaceRoot")
        
        val workspaceConfig = loadWorkspaceConfiguration()
        this.workspaceConfiguration = workspaceConfig // Store for external access
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        // Discover projects via explicit project.json files
        projects.putAll(discoverExplicitProjects())
        
        // Discover projects via Backstage catalog
        var inferenceResult: InferenceResult? = null
        if (enableInference) {
            inferenceResult = backstageDiscoverer.discoverProjects(workspaceRoot)
            projects.putAll(inferenceResult.projects)
            logger.info("Inferred ${inferenceResult.projects.size} projects via Backstage catalog")
        }
        
        // Discover projects via discovery plugins
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
        val dependencies = buildDependencyGraph(configuredProjects, workspaceConfig, inferenceResult)
        
        // Calculate root projects (no incoming dependencies)
        val allTargets = dependencies.values.flatten().map { it.target }.toSet()
        val roots = nodes.keys.filter { it !in allTargets }
        
        logger.info("Discovered ${nodes.size} projects with ${dependencies.values.sumOf { it.size }} dependencies")
        
        return ProjectGraph(nodes, dependencies, roots)
    }
    
    private fun loadWorkspaceConfiguration(): WorkspaceConfiguration {
        val forgeConfigPath = workspaceRoot / "frontseat.json"
        val nxConfigPath = workspaceRoot / "nx.json"
        
        return when {
            forgeConfigPath.exists() -> {
                logger.debug("Loading frontseat.json configuration")
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
                val defaults = workspaceConfig.targetDefaults[targetName]
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
        target: TargetConfiguration,
        defaults: TargetConfiguration
    ): TargetConfiguration {
        return TargetConfiguration(
            executor = target.executor ?: defaults.executor,
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
        workspaceConfig: WorkspaceConfiguration,
        inferenceResult: InferenceResult?
    ): Map<String, List<ProjectGraphDependency>> {
        val dependencies = mutableMapOf<String, MutableList<ProjectGraphDependency>>()
        
        projects.forEach { (projectName, _) ->
            dependencies[projectName] = mutableListOf()
        }
        
        // Add inferred dependencies from plugin inference
        inferenceResult?.dependencies?.forEach { rawDep ->
            if (projects.containsKey(rawDep.source) && projects.containsKey(rawDep.target)) {
                val dependencyType = rawDep.type
                
                dependencies[rawDep.source]?.add(
                    ProjectGraphDependency(
                        target = rawDep.target,
                        source = rawDep.source,
                        type = dependencyType
                    )
                )
                logger.debug("Added inferred dependency: ${rawDep.source} -> ${rawDep.target} (${rawDep.type})")
            }
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