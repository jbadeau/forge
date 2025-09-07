package com.frontseat.nature

import org.slf4j.LoggerFactory
import java.util.*

/**
 * Registry for managing available project natures
 */
class NatureRegistry {
    private val logger = LoggerFactory.getLogger(NatureRegistry::class.java)
    private val natures = mutableMapOf<String, ProjectNature>()
    
    init {
        loadNatures()
    }
    
    /**
     * Register a nature
     */
    fun register(nature: ProjectNature) {
        logger.debug("Registering nature: ${nature.id}")
        natures[nature.id] = nature
    }
    
    /**
     * Get a nature by ID
     */
    fun getNature(id: String): ProjectNature? {
        return natures[id]
    }
    
    /**
     * Get all registered natures
     */
    fun getAllNatures(): Collection<ProjectNature> {
        return natures.values
    }
    
    /**
     * Find natures that are applicable to the given project path
     */
    fun findApplicableNatures(projectPath: java.nio.file.Path): Set<ProjectNature> {
        return natures.values.filter { nature ->
            try {
                nature.isApplicable(projectPath)
            } catch (e: Exception) {
                logger.warn("Error checking nature ${nature.id} applicability", e)
                false
            }
        }.toSet()
    }
    
    /**
     * Resolve nature dependencies and conflicts
     */
    fun resolveNatures(candidates: Set<ProjectNature>): Set<ProjectNature> {
        val resolved = mutableSetOf<ProjectNature>()
        val toProcess = candidates.toMutableSet()
        
        // Add dependencies recursively
        while (toProcess.isNotEmpty()) {
            val nature = toProcess.first()
            toProcess.remove(nature)
            
            // Add dependencies
            nature.dependencies.forEach { depId ->
                val depNature = getNature(depId)
                if (depNature != null && !resolved.contains(depNature)) {
                    toProcess.add(depNature)
                } else if (depNature == null) {
                    logger.warn("Missing dependency: ${nature.id} requires $depId")
                }
            }
            
            // Check for conflicts
            val hasConflicts = nature.conflicts.any { conflictId ->
                resolved.any { it.id == conflictId }
            }
            
            if (!hasConflicts) {
                resolved.add(nature)
            } else {
                logger.warn("Nature ${nature.id} conflicts with existing natures")
            }
        }
        
        return resolved
    }
    
    private fun loadNatures() {
        // Use service loader to discover natures
        val serviceLoader = ServiceLoader.load(ProjectNature::class.java)
        serviceLoader.forEach { nature ->
            register(nature)
        }
        
        logger.info("Loaded ${natures.size} natures")
    }
    
    companion object {
        @JvmStatic
        val instance: NatureRegistry by lazy { NatureRegistry() }
    }
}