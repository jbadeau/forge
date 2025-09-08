package com.frontseat.project.nature

import com.frontseat.task.CommandTask
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Engine for inferring project configuration based on natures
 */
class NatureInferenceEngine(private val natureRegistry: NatureRegistry) {
    private val logger = LoggerFactory.getLogger(NatureInferenceEngine::class.java)
    
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
        val allTasks = mutableMapOf<String, CommandTask>()
        resolvedNatures.forEach { nature ->
            try {
                val natureTasks = nature.createTasks(projectPath, context)
                allTasks.putAll(natureTasks)
                logger.debug("Nature ${nature.id} contributed ${natureTasks.size} tasks")
            } catch (e: Exception) {
                logger.error("Error creating tasks for nature ${nature.id}", e)
            }
        }
        
        // Tasks are now self-contained with their lifecycle dependencies
        
        // Infer project name from path
        val projectName = projectPath.fileName?.toString() ?: "unknown"
        
        return InferredProject(
            name = projectName,
            root = projectPath.toString(),
            natures = resolvedNatures.map { it.id }.toSet(),
            tasks = allTasks,
            tags = inferTags(resolvedNatures)
        )
    }
    
    
    /**
     * Infer project tags from applied natures
     */
    private fun inferTags(natures: Set<Nature>): List<String> {
        val tags = mutableSetOf<String>()
        natures.forEach { nature ->
            // Add nature ID as tag
            tags.add(nature.id)
            
            // Plugin-specific tag inference should be handled by the plugins themselves
            // through nature metadata or custom tag providers
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
    val tasks: Map<String, CommandTask>,
    val tags: List<String>
)

