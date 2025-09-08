package com.frontseat.project.nature

import org.slf4j.LoggerFactory

/**
 * Registry for managing available project natures
 */
class NatureRegistry {
    private val logger = LoggerFactory.getLogger(NatureRegistry::class.java)
    private val natures = mutableMapOf<String, Nature>()
    
    
    /**
     * Register a nature
     */
    fun register(nature: Nature) {
        logger.debug("Registering nature: ${nature.id}")
        natures[nature.id] = nature
    }
    
    /**
     * Get a nature by ID
     */
    fun getNature(id: String): Nature? {
        return natures[id]
    }
    
    /**
     * Get all registered natures
     */
    fun getAllNatures(): Collection<Nature> {
        return natures.values
    }
    
    /**
     * Find natures that are applicable to the given project path using layered approach
     */
    fun findApplicableNatures(projectPath: java.nio.file.Path): Set<Nature> {
        val applicableNatures = mutableSetOf<Nature>()
        
        // Group natures by layer
        val naturesGroupedByLayer = natures.values.groupBy { it.layer }.toSortedMap()
        
        // Apply natures layer by layer
        for ((layer, layerNatures) in naturesGroupedByLayer) {
            logger.debug("Processing nature layer $layer with ${layerNatures.size} natures")
            
            // Create context with currently applied natures
            val context = NatureContextImpl(projectPath, applicableNatures.map { it.id }.toSet())
            
            // Check applicability of natures in current layer
            val layerApplicableNatures = layerNatures.filter { nature ->
                try {
                    nature.isApplicable(projectPath, context)
                } catch (e: Exception) {
                    logger.warn("Error checking nature ${nature.id} applicability in layer $layer", e)
                    false
                }
            }
            
            applicableNatures.addAll(layerApplicableNatures)
            logger.debug("Layer $layer added ${layerApplicableNatures.size} applicable natures: ${layerApplicableNatures.map { it.id }}")
        }
        
        return applicableNatures
    }
    
    /**
     * Since natures now handle dependencies and conflicts at runtime in isApplicable(),
     * this method just returns the candidates as-is
     */
    fun resolveNatures(candidates: Set<Nature>): Set<Nature> {
        // Natures are responsible for their own validation in isApplicable()
        return candidates
    }
    
    
}