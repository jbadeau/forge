package com.frontseat.nature

import com.frontseat.plugin.api.ProjectConfiguration
import com.frontseat.plugin.api.TargetConfiguration
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Engine for inferring project configuration based on natures
 */
class ProjectInferenceEngine(private val natureRegistry: NatureRegistry = NatureRegistry.instance) {
    private val logger = LoggerFactory.getLogger(ProjectInferenceEngine::class.java)
    
    /**
     * Infer project configuration from the given path
     */
    fun inferProject(projectPath: Path): InferredProject? {
        logger.debug("Inferring project at: $projectPath")
        
        // Find applicable natures
        val applicableNatures = natureRegistry.findApplicableNatures(projectPath)
        if (applicableNatures.isEmpty()) {
            logger.debug("No applicable natures found for: $projectPath")
            return null
        }
        
        // Resolve dependencies and conflicts
        val resolvedNatures = natureRegistry.resolveNatures(applicableNatures)
        logger.debug("Resolved natures: ${resolvedNatures.map { it.id }}")
        
        // Create nature context
        val context = NatureContextImpl(projectPath, resolvedNatures.map { it.id }.toSet())
        
        // Collect tasks from all natures
        val allTasks = mutableMapOf<String, NatureTargetDefinition>()
        resolvedNatures.forEach { nature ->
            try {
                val natureTasks = nature.createTasks(projectPath, context)
                allTasks.putAll(natureTasks)
                logger.debug("Nature ${nature.id} contributed ${natureTasks.size} tasks")
            } catch (e: Exception) {
                logger.error("Error creating tasks for nature ${nature.id}", e)
            }
        }
        
        // Resolve task dependencies based on lifecycle phases
        val resolvedTasks = resolveTaskDependencies(allTasks)
        
        // Infer project name from path
        val projectName = projectPath.fileName?.toString() ?: "unknown"
        
        return InferredProject(
            name = projectName,
            root = projectPath.toString(),
            natures = resolvedNatures.map { it.id }.toSet(),
            tasks = resolvedTasks,
            tags = inferTags(resolvedNatures)
        )
    }
    
    /**
     * Resolve task dependencies based on lifecycle phase ordering
     */
    private fun resolveTaskDependencies(tasks: Map<String, NatureTargetDefinition>): Map<String, TargetConfiguration> {
        return tasks.mapValues { (taskName, taskDef) ->
            val dependencies = when (val lifecycle = taskDef.lifecycle) {
                is TargetLifecycle.Build -> {
                    // Find all tasks in earlier build phases
                    tasks.filter { (otherName, otherTask) ->
                        otherName != taskName &&
                        otherTask.lifecycle is TargetLifecycle.Build &&
                        otherTask.lifecycle.phase.order < lifecycle.phase.order
                    }.keys.toList()
                }
                is TargetLifecycle.Development -> {
                    // Different development phases have different dependencies
                    val buildDependencies = when (lifecycle.phase) {
                        DevelopmentLifecyclePhase.FORMAT,
                        DevelopmentLifecyclePhase.LINT,
                        DevelopmentLifecyclePhase.NUKE -> {
                            // These don't depend on build
                            emptyList()
                        }
                        DevelopmentLifecyclePhase.SERVE,
                        DevelopmentLifecyclePhase.DEBUG,
                        DevelopmentLifecyclePhase.PROFILE,
                        DevelopmentLifecyclePhase.MONITOR -> {
                            // These need the artifacts to be bundled
                            tasks.filter { (_, otherTask) ->
                                otherTask.lifecycle is TargetLifecycle.Build &&
                                otherTask.lifecycle.phase == BuildLifecyclePhase.PACKAGE
                            }.keys.toList()
                        }
                        DevelopmentLifecyclePhase.WATCH,
                        DevelopmentLifecyclePhase.RELOAD -> {
                            // These need compilation
                            tasks.filter { (_, otherTask) ->
                                otherTask.lifecycle is TargetLifecycle.Build &&
                                otherTask.lifecycle.phase == BuildLifecyclePhase.COMPILE
                            }.keys.toList()
                        }
                    }
                    
                    // Plus any earlier development phases
                    val devDependencies = tasks.filter { (otherName, otherTask) ->
                        otherName != taskName &&
                        otherTask.lifecycle is TargetLifecycle.Development &&
                        otherTask.lifecycle.phase.order < lifecycle.phase.order
                    }.keys.toList()
                    
                    buildDependencies + devDependencies
                }
                is TargetLifecycle.Release -> {
                    // Find all tasks in earlier release phases
                    tasks.filter { (otherName, otherTask) ->
                        otherName != taskName &&
                        otherTask.lifecycle is TargetLifecycle.Release &&
                        otherTask.lifecycle.phase.order < lifecycle.phase.order
                    }.keys.toList()
                }
            }
            
            // Add dependencies to task configuration
            taskDef.configuration.copy(dependsOn = dependencies)
        }
    }
    
    /**
     * Infer project tags from applied natures
     */
    private fun inferTags(natures: Set<ProjectNature>): List<String> {
        val tags = mutableSetOf<String>()
        natures.forEach { nature ->
            // Add nature ID as tag
            tags.add(nature.id)
            
            // Add common technology tags
            when (nature.id) {
                "maven" -> tags.addAll(listOf("java", "build-tool"))
                "gradle" -> tags.addAll(listOf("java", "build-tool"))
                "spring-boot" -> tags.addAll(listOf("java", "framework", "web"))
                "docker" -> tags.addAll(listOf("containerization"))
                "react" -> tags.addAll(listOf("javascript", "frontend", "web"))
            }
        }
        return tags.toList()
    }
}

/**
 * Result of project inference
 */
data class InferredProject(
    val name: String,
    val root: String,
    val natures: Set<String>,
    val tasks: Map<String, TargetConfiguration>,
    val tags: List<String>
) {
    /**
     * Convert to ProjectConfiguration
     */
    fun toProjectConfiguration(): ProjectConfiguration {
        return ProjectConfiguration(
            name = name,
            root = root,
            projectType = "inferred",
            targets = tasks, // Keep targets in API for now
            tags = tags
        )
    }
}

/**
 * Implementation of NatureContext
 */
private class NatureContextImpl(
    private val projectPath: Path,
    private val appliedNatures: Set<String>,
    private val availableTasks: MutableSet<String> = mutableSetOf()
) : NatureContext {
    
    override fun getAppliedNatures(): Set<String> = appliedNatures
    
    override fun hasNature(natureId: String): Boolean = appliedNatures.contains(natureId)
    
    override fun hasTask(taskName: String): Boolean = availableTasks.contains(taskName)
    
    override fun getProjectPath(): Path = projectPath
    
    override fun getAllProjects(): List<ProjectInfo> {
        // TODO: Implement workspace-aware project discovery
        return emptyList()
    }
    
    override fun findProjects(predicate: (ProjectInfo) -> Boolean): List<ProjectInfo> {
        // TODO: Implement workspace-aware project search
        return emptyList()
    }
}