package com.forge.nature

import com.forge.plugin.api.ProjectConfiguration
import com.forge.plugin.api.TargetConfiguration
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
        
        // Collect targets from all natures
        val allTargets = mutableMapOf<String, NatureTargetDefinition>()
        resolvedNatures.forEach { nature ->
            try {
                val natureTargets = nature.createTargets(projectPath, context)
                allTargets.putAll(natureTargets)
                logger.debug("Nature ${nature.id} contributed ${natureTargets.size} targets")
            } catch (e: Exception) {
                logger.error("Error creating targets for nature ${nature.id}", e)
            }
        }
        
        // Resolve target dependencies based on lifecycle phases
        val resolvedTargets = resolveTargetDependencies(allTargets)
        
        // Infer project name from path
        val projectName = projectPath.fileName?.toString() ?: "unknown"
        
        return InferredProject(
            name = projectName,
            root = projectPath.toString(),
            natures = resolvedNatures.map { it.id }.toSet(),
            targets = resolvedTargets,
            tags = inferTags(resolvedNatures)
        )
    }
    
    /**
     * Resolve target dependencies based on lifecycle phase ordering
     */
    private fun resolveTargetDependencies(targets: Map<String, NatureTargetDefinition>): Map<String, TargetConfiguration> {
        return targets.mapValues { (targetName, targetDef) ->
            val dependencies = when (val lifecycle = targetDef.lifecycle) {
                is TargetLifecycle.Build -> {
                    // Find all targets in earlier build phases
                    targets.filter { (otherName, otherTarget) ->
                        otherName != targetName &&
                        otherTarget.lifecycle is TargetLifecycle.Build &&
                        otherTarget.lifecycle.phase.order < lifecycle.phase.order
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
                            targets.filter { (_, otherTarget) ->
                                otherTarget.lifecycle is TargetLifecycle.Build &&
                                otherTarget.lifecycle.phase == BuildLifecyclePhase.BUNDLE
                            }.keys.toList()
                        }
                        DevelopmentLifecyclePhase.WATCH,
                        DevelopmentLifecyclePhase.RELOAD -> {
                            // These need compilation
                            targets.filter { (_, otherTarget) ->
                                otherTarget.lifecycle is TargetLifecycle.Build &&
                                otherTarget.lifecycle.phase == BuildLifecyclePhase.COMPILE
                            }.keys.toList()
                        }
                    }
                    
                    // Plus any earlier development phases
                    val devDependencies = targets.filter { (otherName, otherTarget) ->
                        otherName != targetName &&
                        otherTarget.lifecycle is TargetLifecycle.Development &&
                        otherTarget.lifecycle.phase.order < lifecycle.phase.order
                    }.keys.toList()
                    
                    buildDependencies + devDependencies
                }
                is TargetLifecycle.Release -> {
                    // Find all targets in earlier release phases
                    targets.filter { (otherName, otherTarget) ->
                        otherName != targetName &&
                        otherTarget.lifecycle is TargetLifecycle.Release &&
                        otherTarget.lifecycle.phase.order < lifecycle.phase.order
                    }.keys.toList()
                }
            }
            
            // Add dependencies to target configuration
            targetDef.configuration.copy(dependsOn = dependencies)
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
    val targets: Map<String, TargetConfiguration>,
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
            targets = targets,
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
    private val availableTargets: MutableSet<String> = mutableSetOf()
) : NatureContext {
    
    override fun getAppliedNatures(): Set<String> = appliedNatures
    
    override fun hasNature(natureId: String): Boolean = appliedNatures.contains(natureId)
    
    override fun hasTarget(targetName: String): Boolean = availableTargets.contains(targetName)
    
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